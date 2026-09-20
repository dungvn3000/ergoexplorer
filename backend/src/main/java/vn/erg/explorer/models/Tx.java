package vn.erg.explorer.models;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A confirmed transaction, keyed by its global index in chain order. Boxes: {@link Box} (outputs), {@link TxInput} (inputs). */
@Getter
@Setter
@Entity
@Table(name = "tx")
public class Tx {

    @Id
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
