package vn.erg.explorer.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** How ERG is spread over funded addresses: one bucket per balance range, richest range first. Amounts in nanoERG. */
@Data
@AllArgsConstructor
public class BalanceDistributionDto {
    private List<BucketDto> buckets;
    /** funded addresses (balance > 0) */
    private long addresses;
    /** ERG held by them, in nanoERG */
    private long nanoErg;

    @Data
    @AllArgsConstructor
    public static class BucketDto {
        /** e.g. "100k – 1M" (ERG) */
        private String label;
        /** inclusive lower bound, nanoERG */
        private long minNanoErg;
        /** exclusive upper bound, nanoERG; null for the richest bucket */
        private Long maxNanoErg;
        private long addresses;
        private long nanoErg;
    }
}
