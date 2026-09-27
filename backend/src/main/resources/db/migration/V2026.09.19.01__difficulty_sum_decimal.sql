-- Daily difficulty sum overflows BIGINT: after the ETH merge (Sep 2022) Ergo difficulty reached
-- ~4e16 per block, so 720 blocks/day exceed 9.2e18. DECIMAL(30,0) keeps the exact sum.
ALTER TABLE daily_stats MODIFY difficulty_sum DECIMAL(30,0) NOT NULL DEFAULT 0;
