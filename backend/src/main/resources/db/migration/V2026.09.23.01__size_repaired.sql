-- Progress of the block size repair (index/BlockSizeRepair): every block at or below this height has had
-- its size checked against the full size (header + transactions + extension + AD proofs). The node drops
-- the AD proofs of blocks older than its adProofsSuffixLength (~115k blocks), and the size it reports for
-- such a block lacks them.
ALTER TABLE indexer_state ADD COLUMN size_repaired BIGINT NOT NULL DEFAULT 0;
