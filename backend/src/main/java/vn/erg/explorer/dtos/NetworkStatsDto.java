package vn.erg.explorer.dtos;

import lombok.Data;

import java.util.List;

@Data
public class NetworkStatsDto {
    private long height;
    private long tipTimestamp;
    private long epoch;
    private long blocksToNextEpoch;
    /** TH/s */
    private double hashrate;
    private double hashrateChange7d;
    private long difficulty;
    private double avgBlockTimeSec;
    /** Whole ERG, issued minus what the re-emission contract holds (EIP-27). */
    private long circulating;
    /** Whole ERG released from the emission box so far (the node's totalCoinsIssued). */
    private long issued;
    /** Whole ERG earmarked for re-emission by EIP-27 so far (issued, not circulating). */
    private long reemissionLocked;
    private long maxSupply;
    private int mempoolCount;
    private long mempoolBytes;
    private int peers;
    private String nodeVersion;
    private String nodeName;
    private List<Point> hashrate30d;
    private List<PoolShare> poolShare24h;

    @Data
    public static class Point {
        private long day;
        private double value;
    }

    @Data
    public static class PoolShare {
        private String name;
        private String address;
        private int blocks;
    }
}
