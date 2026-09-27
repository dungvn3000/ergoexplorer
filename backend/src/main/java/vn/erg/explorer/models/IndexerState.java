package vn.erg.explorer.models;

import lombok.Getter;
import lombok.Setter;

/** Single row (id = 1): the last height fully written by the chain indexer. */
@Getter
@Setter
public class IndexerState {

    public static final int SINGLETON_ID = 1;

    private int id;

    private long height;

    private byte[] blockId;

    /** Epoch millis of the last update. */
    private long updated;

}
