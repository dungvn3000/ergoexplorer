-- Unspent boxes per address. The unspent page used to walk every box of the address backwards and probe
-- box_spent for each: a busy contract with 700k spent boxes and 18 unspent ones scanned all 700k (4-10 s).
-- One row per unspent box, keyed by (script_id, gix): an address's unspent boxes newest first is a reverse
-- range scan of exactly one page. Kept by the indexer (insert on create, delete on spend, undone on rollback).
CREATE TABLE box_unspent (
  script_id BIGINT NOT NULL,
  gix       BIGINT NOT NULL,
  PRIMARY KEY (script_id, gix)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- One-off backfill of an existing index (the UTXO set, a few million rows; minutes inside Flyway on start).
INSERT INTO box_unspent (script_id, gix)
  SELECT b.script_id, b.gix FROM box b
  WHERE NOT EXISTS (SELECT 1 FROM box_spent s WHERE s.box_gix = b.gix)
  ORDER BY b.script_id, b.gix;
