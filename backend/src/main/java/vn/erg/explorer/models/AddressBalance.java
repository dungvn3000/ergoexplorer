package vn.erg.explorer.models;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Aggregate maintained by the indexer: ERG held in unspent boxes per script (address) and its transaction counters. */
@Getter
@Setter
@Entity
@Table(name = "address_balance")
public class AddressBalance {

    @Id
    private long scriptId;

    private long nanoErg;

    private int boxCount;

    /** Distinct transactions touching the address (rows of {@code address_tx}). */
    private long txCount;

    /** Oldest / newest of those transactions (0 = none). */
    private long firstTxGix;

    private long lastTxGix;

}
