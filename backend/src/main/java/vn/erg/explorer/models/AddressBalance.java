package vn.erg.explorer.models;

import lombok.Getter;
import lombok.Setter;

/** Aggregate maintained by the indexer: ERG held in unspent boxes per script (address) and its transaction counters. */
@Getter
@Setter
public class AddressBalance {

    private long scriptId;

    private long nanoErg;

    private int boxCount;

    /** Distinct transactions touching the address (rows of {@code address_tx}). */
    private long txCount;

    /** Oldest / newest of those transactions (0 = none). */
    private long firstTxGix;

    private long lastTxGix;

}
