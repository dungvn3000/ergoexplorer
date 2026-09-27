-- Address history without scanning every box of the address: one (script, tx) row per transaction
-- touching the address, plus per-address counters. Busy contracts (mining fee, exchanges) have
-- millions of boxes, so the old UNION over box / box_spent took seconds per page view.
CREATE TABLE address_tx (
  script_id BIGINT NOT NULL,
  tx_gix    BIGINT NOT NULL,
  PRIMARY KEY (script_id, tx_gix)            -- newest first = reverse range scan
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- address_balance rows are no longer pruned at zero balance: they carry the tx counters.
ALTER TABLE address_balance
  ADD COLUMN tx_count     BIGINT NOT NULL DEFAULT 0,  -- distinct transactions touching the address
  ADD COLUMN first_tx_gix BIGINT NOT NULL DEFAULT 0,  -- oldest / newest of them (0 = none)
  ADD COLUMN last_tx_gix  BIGINT NOT NULL DEFAULT 0;

-- One-off backfill of an existing index (minutes on a full mainnet index, done inside Flyway on start).
-- Sorted so the inserts append to the primary key; genesis boxes (tx_gix 0) are not transactions.
INSERT IGNORE INTO address_tx (script_id, tx_gix)
  SELECT DISTINCT script_id, tx_gix FROM box WHERE tx_gix > 0 ORDER BY script_id, tx_gix;
INSERT IGNORE INTO address_tx (script_id, tx_gix)
  SELECT DISTINCT b.script_id, s.tx_gix FROM box_spent s JOIN box b ON b.gix = s.box_gix ORDER BY b.script_id, s.tx_gix;
INSERT INTO address_balance (script_id, nano_erg, box_count, tx_count, first_tx_gix, last_tx_gix)
  SELECT script_id, 0, 0, COUNT(*), MIN(tx_gix), MAX(tx_gix) FROM address_tx GROUP BY script_id
  ON DUPLICATE KEY UPDATE tx_count = VALUES(tx_count), first_tx_gix = VALUES(first_tx_gix), last_tx_gix = VALUES(last_tx_gix);
