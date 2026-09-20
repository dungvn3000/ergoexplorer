-- Chain index. One row per block / transaction / box of the main chain; reorged blocks are deleted.
--
-- Storage design:
--  * Hashes are BINARY(32): a CHAR(64) utf8mb4 key reserves 256 bytes per index entry, BINARY(32) uses 32.
--  * Blocks, transactions and boxes get sequential keys in chain order (height, tx.gix, box.gix), so
--    inserts append to the clustered indexes; hashes are narrow UNIQUE keys; cross references are 8 bytes.
--  * ErgoTree and address are 1:1 and repeat massively (contracts), so they live once in `script` and
--    every box points at it. Balances, holders and active-address rollups are keyed by script id too.
--  * Spends are rows in the narrow box_spent table; box rows are never updated.
--
-- This is the complete current schema (baseline). A database created by the earlier incremental
-- migrations is identical; see README "Chain index" for how existing deployments adopt this baseline.

CREATE TABLE script (
  id        BIGINT        NOT NULL,
  tree_hash BINARY(32)    NOT NULL,                     -- SHA-256 of the ErgoTree bytes: dedupe key
  addr_hash BINARY(32)    NOT NULL,                     -- SHA-256 of the address string: lookup by address
  ergo_tree MEDIUMTEXT    CHARACTER SET ascii NOT NULL, -- hex
  address   VARCHAR(4096) CHARACTER SET ascii NOT NULL, -- base58; P2S addresses of contracts can be very long
  p2pk      TINYINT       NOT NULL,                     -- 1 = pay-to-public-key, 0 = script (P2S / P2SH)
  PRIMARY KEY (id),
  UNIQUE KEY uk_script_tree (tree_hash),
  UNIQUE KEY uk_script_addr (addr_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE block (
  height            BIGINT      NOT NULL,
  id                BINARY(32)  NOT NULL,
  parent_id         BINARY(32)  NOT NULL,
  timestamp         BIGINT      NOT NULL,
  tx_count          INT         NOT NULL,
  size              INT         NOT NULL,
  miner_pk          BINARY(33)  NOT NULL,   -- powSolutions.pk (compressed EC point)
  miner_script_id   BIGINT      NOT NULL,   -- script of the miner's P2PK address
  difficulty        BIGINT      NOT NULL,
  n_bits            BIGINT      NOT NULL,
  version           TINYINT     NOT NULL,
  votes             BINARY(3)   NOT NULL,
  emission          BIGINT      NOT NULL,   -- value of the miner reward box (nanoERG)
  reemitted         BIGINT      NOT NULL,   -- part of it owed to the re-emission contract (EIP-27); reward = emission - reemitted
  fees              BIGINT      NOT NULL,
  state_root        BINARY(33)  NOT NULL,
  transactions_root BINARY(32)  NOT NULL,
  ad_proofs_root    BINARY(32)  NOT NULL,
  extension_hash    BINARY(32)  NOT NULL,
  pow_w             BINARY(33)  NOT NULL,
  pow_n             BINARY(8)   NOT NULL,
  pow_d             VARCHAR(80) CHARACTER SET ascii NOT NULL,   -- big integer in Autolykos v1 blocks, "0" in v2
  PRIMARY KEY (height),
  UNIQUE KEY uk_block_id (id),
  KEY idx_block_miner (miner_script_id, height),
  KEY idx_block_timestamp (timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tx (
  gix          BIGINT     NOT NULL,          -- global index in chain order
  id           BINARY(32) NOT NULL,
  block_height BIGINT     NOT NULL,
  idx          INT        NOT NULL,          -- position in the block
  coinbase     TINYINT    NOT NULL DEFAULT 0,-- the emission tx (output 0 re-creates the emission box); not always idx 0
  timestamp    BIGINT     NOT NULL,
  size         INT        NOT NULL,
  fee          BIGINT     NOT NULL,          -- sum of the outputs guarded by the miner-fee tree
  PRIMARY KEY (gix),
  UNIQUE KEY uk_tx_id (id),
  KEY idx_tx_block (block_height, idx)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Outputs. gix increases in chain order, so it also orders boxes by age. Genesis boxes: block_height 0.
CREATE TABLE box (
  gix             BIGINT     NOT NULL,
  id              BINARY(32) NOT NULL,
  tx_gix          BIGINT     NOT NULL,
  idx             INT        NOT NULL,       -- output index in its transaction
  block_height    BIGINT     NOT NULL,
  value           BIGINT     NOT NULL,
  script_id       BIGINT     NOT NULL,       -- address + ErgoTree
  creation_height BIGINT     NOT NULL,
  registers       TEXT       CHARACTER SET ascii NULL,  -- raw JSON {"R4":"0e..","R5":".."}, decoded on read
  PRIMARY KEY (gix),
  UNIQUE KEY uk_box_id (id),
  KEY idx_box_tx (tx_gix, idx),
  KEY idx_box_script (script_id, gix),
  KEY idx_box_height (block_height)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- One row per spent box; a box without a row here is unspent.
CREATE TABLE box_spent (
  box_gix BIGINT NOT NULL,
  tx_gix  BIGINT NOT NULL,
  height  BIGINT NOT NULL,
  PRIMARY KEY (box_gix),
  KEY idx_spent_height (height)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE box_asset (
  box_gix  BIGINT     NOT NULL,
  idx      INT        NOT NULL,
  token_id BINARY(32) NOT NULL,
  amount   BIGINT     NOT NULL,
  PRIMARY KEY (box_gix, idx),
  KEY idx_asset_token (token_id, box_gix)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- inputs and data inputs of a transaction (box rows live in `box`)
CREATE TABLE tx_input (
  tx_gix     BIGINT  NOT NULL,
  data_input TINYINT NOT NULL,               -- 0 = spent input, 1 = read-only data input
  idx        INT     NOT NULL,
  box_gix    BIGINT  NOT NULL,
  PRIMARY KEY (tx_gix, data_input, idx)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- EIP-4 tokens: minted when an output carries a token id equal to the tx's first input box id
CREATE TABLE token (
  id              BINARY(32)   NOT NULL,
  box_gix         BIGINT       NOT NULL,     -- issuing box
  tx_gix          BIGINT       NOT NULL,
  block_height    BIGINT       NOT NULL,
  name            VARCHAR(512) NOT NULL,     -- R4 of the issuing box
  description     TEXT         NOT NULL,     -- R5
  decimals        INT          NOT NULL,     -- R6
  emission_amount BIGINT       NOT NULL,
  PRIMARY KEY (id),
  KEY idx_token_height (block_height),
  KEY idx_token_name (name(64))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE indexer_state (
  id       TINYINT    NOT NULL,
  height   BIGINT     NOT NULL,              -- last fully indexed height
  block_id BINARY(32) NOT NULL,
  updated  BIGINT     NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ── Aggregates maintained by the indexer (rich lists, instant balances, address history) ─────

-- One row per address ever seen; rows are kept at zero balance because they carry the tx counters.
CREATE TABLE address_balance (
  script_id    BIGINT NOT NULL,
  nano_erg     BIGINT NOT NULL,              -- sum of unspent boxes
  box_count    INT    NOT NULL,              -- number of unspent boxes
  tx_count     BIGINT NOT NULL DEFAULT 0,    -- distinct transactions touching the address
  first_tx_gix BIGINT NOT NULL DEFAULT 0,    -- oldest / newest of them (0 = none)
  last_tx_gix  BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (script_id),
  KEY idx_balance_rich (nano_erg)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Address history without scanning every box of the address: one (script, tx) row per transaction
-- touching the address. Busy contracts (mining fee, exchanges) have millions of boxes.
CREATE TABLE address_tx (
  script_id BIGINT NOT NULL,
  tx_gix    BIGINT NOT NULL,
  PRIMARY KEY (script_id, tx_gix)            -- newest first = reverse range scan
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE token_holder (
  token_id  BINARY(32) NOT NULL,
  script_id BIGINT     NOT NULL,
  amount    BIGINT     NOT NULL,             -- raw amount held in unspent boxes
  PRIMARY KEY (token_id, script_id),
  KEY idx_holder_rich (token_id, amount)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ── Daily rollups for charts (day = UTC days since epoch) ─────────────────────────────────

CREATE TABLE daily_stats (
  day              INT           NOT NULL,
  blocks           INT           NOT NULL DEFAULT 0,
  txs              INT           NOT NULL DEFAULT 0,
  fees             BIGINT        NOT NULL DEFAULT 0,   -- nanoERG
  size             BIGINT        NOT NULL DEFAULT 0,   -- bytes of all blocks
  difficulty_sum   DECIMAL(30,0) NOT NULL DEFAULT 0,   -- avg difficulty = difficulty_sum / blocks; exceeds BIGINT since 2022
  emission         BIGINT        NOT NULL DEFAULT 0,   -- nanoERG released to miners' reward boxes
  reemitted        BIGINT        NOT NULL DEFAULT 0,
  boxes_created    INT           NOT NULL DEFAULT 0,
  boxes_spent      INT           NOT NULL DEFAULT 0,
  token_transfers  INT           NOT NULL DEFAULT 0,   -- box_asset rows created
  tokens_minted    INT           NOT NULL DEFAULT 0,
  funded_addresses INT           NULL,                 -- snapshot at the end of the day (scripts with ERG > 0)
  utxo_boxes       BIGINT        NULL,                 -- snapshot at the end of the day (unspent boxes)
  PRIMARY KEY (day)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Scripts (addresses) that received or spent a box on a day (active addresses = COUNT(*) per day)
CREATE TABLE daily_address (
  day       INT    NOT NULL,
  script_id BIGINT NOT NULL,
  PRIMARY KEY (day, script_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Mempool snapshots taken by the backend from the node (not derivable from the chain)
CREATE TABLE mempool_sample (
  ts       BIGINT NOT NULL,                     -- epoch millis
  tx_count INT    NOT NULL,
  bytes    BIGINT NOT NULL,
  fees     BIGINT NOT NULL,                     -- nanoERG waiting
  PRIMARY KEY (ts)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
