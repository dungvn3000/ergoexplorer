package vn.erg.explorer.models;

import lombok.Getter;
import lombok.Setter;

/** A confirmed transaction, keyed by its global index in chain order. Boxes: {@link Box} (outputs), {@link TxInput} (inputs). */
@Getter
@Setter
public class Tx {

    private long gix;

    private byte[] id;

    private long blockHeight;

    /** Position in the block. */
    private int idx;

    /** The emission transaction: spends the emission box and pays the miner reward. */
    private boolean coinbase;

    private long timestamp;

    private int size;

    /** Sum of the outputs guarded by the miner-fee tree (nanoERG). */
    private long fee;

}
