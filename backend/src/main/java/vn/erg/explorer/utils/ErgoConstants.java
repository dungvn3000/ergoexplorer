package vn.erg.explorer.utils;

public final class ErgoConstants {

    private ErgoConstants() {
    }

    public static final long NANO = 1_000_000_000L;

    /** Total ERG that will ever exist. */
    public static final long MAX_SUPPLY = 97_739_924L;

    /** Difficulty is re-targeted every epoch of this many blocks. */
    public static final int EPOCH_LENGTH = 1024;

    /** Target block interval. */
    public static final int BLOCK_TIME_SEC = 120;

    /** Blocks per day at the target interval. */
    public static final int BLOCKS_PER_DAY = 720;

    /**
     * EIP-27: the miner's reward box carries this many "Reemission Token" units as the amount (in nanoERG)
     * the miner must forward to the re-emission contract, so the box value minus the tokens is the real reward.
     */
    public static final String REEMISSION_TOKEN = "d9a2cc8a09abfaed87afacfbb7daee79a6b26f10c6613fc13d3f3953e5521d1a";

    /** EIP-27 pay-to-reemission box: miners send the re-emitted part of each reward here; anyone may merge these into the re-emission box. */
    public static final String PAY_TO_REEMISSION_ADDRESS = "6KxusedL87PBibr1t1f4ggzAyTAmWEPqSpqXbkdoybNwHVw5Nb7cUESBmQw5XK8TyvbQiueyqkR9XMNaUgpWx3jT54p";

    /** EIP-27 re-emission box (holds the re-emission NFT d3feeffa…); pays 3 ERG per block once emission ends. */
    public static final String REEMISSION_ADDRESS = "22WkKcVUvboYCZJe1urbmvBL3j67LKb5KEAvFhJXqA6ubYvHpSCvbvwvEY3xzUr7QvxpEtqjzMAPMsVdZh1VGWmZphvKoJdVzL1ayhsMftTtEFoA3YYdq3zKeeYXavVrrPUmK3fRXJ2HWEbZexewtBWcgAnHBw5tKvYFy9dEUi645gE2fYMUvVBtbvMExE9mjZ2W9goWkqu1VtThAsMZWZWjHxDjX116HpeQKu9b9neEUBj4kE5sX8QXaV6ZeReXxYHFJFg2rmaTknSPMxHXA8NpQKgzryBwLssp5EJ1QTqn5R6xuvGgFCEUZicCEo8qk8UNbE7e2d4WqW5qzpQPzJkKoPa5UtJEPYDWNhaCKmCpzdSc77";

    /** Script of the emission box (unchanged since genesis): output 0 of every emission tx carries it. */
    public static final String EMISSION_TREE = "101004020e36100204a00b08cd0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798ea02d192a39a8cc7a7017300730110010204020404040004c0fd4f05808c82f5f6030580b8c9e5ae040580f882ad16040204c0944004c0f407040004000580f882ad16d19683030191a38cc7a7019683020193c2b2a57300007473017302830108cdeeac93a38cc7b2a573030001978302019683040193b1a5730493c2a7c2b2a573050093958fa3730673079973089c73097e9a730a9d99a3730b730c0599c1a7c1b2a5730d00938cc7b2a5730e0001a390c1a7730f";

    public static final String EMISSION_ADDRESS = ErgoAddress.fromErgoTree(EMISSION_TREE);

    /** Standard miner-fee proposition: every fee box in a transaction is guarded by this tree. */
    public static final String FEE_TREE =
            "1005040004000e36100204a00b08cd0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798"
            + "ea02d192a39a8cc7a701730073011001020402d19683030193a38cc7b2a57300000193c2b2a57301007473027303830108cdeeac93b1a57304";

    public static final String FEE_ADDRESS = ErgoAddress.fromErgoTree(FEE_TREE);

}
