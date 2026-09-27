-- The block size repair keeps its progress in its own row. It commits every second or so while the
-- indexer's batch (or rollback) transaction is open; both updating the single indexer_state row made the
-- indexer's UPDATE fail under MariaDB's snapshot isolation ("Record has changed since last read").
CREATE TABLE size_repair_state (
  id     TINYINT NOT NULL,
  height BIGINT  NOT NULL,   -- blocks up to this height have had their size checked
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO size_repair_state (id, height) SELECT 1, size_repaired FROM indexer_state WHERE id = 1;
ALTER TABLE indexer_state DROP COLUMN size_repaired;
