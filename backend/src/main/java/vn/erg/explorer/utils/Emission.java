package vn.erg.explorer.utils;

import static vn.erg.explorer.utils.ErgoConstants.NANO;

/**
 * Ergo emission schedule (verified against the node's /emission/at at every boundary):
 * 75 ERG per block for the first 525,600 blocks, then 3 ERG less every 64,800 blocks down to 3 ERG.
 * EIP-27 (from block 777,217) earmarks part of every block for re-emission: 12 ERG while the
 * emission is at least 15, otherwise everything above the 3 ERG miner reward. That part is issued
 * but not circulating (it sits in pay-to-reemission boxes, the re-emission box, or unspent reward
 * boxes), which is how the official explorer computes the circulating supply.
 */
public final class Emission {

    public static final int FIXED_RATE_PERIOD = 525_600;
    public static final int EPOCH_LENGTH = 64_800;
    public static final long EIP27_ACTIVATION = 777_217;

    private Emission() {
    }

    /** Whole ERG emitted by the block at {@code height}. */
    public static long emissionAt(long height) {
        if (height <= FIXED_RATE_PERIOD) {
            return 75;
        }
        return Math.max(3, 75 - 3 * ((height - FIXED_RATE_PERIOD) / EPOCH_LENGTH + 1));
    }

    /** Whole ERG of that block's emission earmarked for re-emission (EIP-27). */
    public static long reemittedAt(long height) {
        if (height < EIP27_ACTIVATION) {
            return 0;
        }
        long emission = emissionAt(height);
        return emission >= 15 ? 12 : emission - 3;
    }

    /** Whole ERG earmarked for re-emission by all blocks up to {@code height} inclusive. */
    public static long totalReemitted(long height) {
        long total = 0;
        for (long h = EIP27_ACTIVATION; h <= height; h++) {
            total += reemittedAt(h);
        }
        return total;
    }

    /** nanoERG helper for callers working in raw units. */
    public static long totalReemittedNano(long height) {
        return totalReemitted(height) * NANO;
    }

}
