package vn.erg.explorer.models;

import lombok.Getter;
import lombok.Setter;

/** Reference from a transaction to a box it spends (or reads, for data inputs); key (tx_gix, data_input, idx). */
@Getter
@Setter
public class TxInput {

    private long txGix;

    /** false = spent input, true = read-only data input ({@code data_input} 0 / 1). */
    private boolean dataInput;

    private int idx;

    private long boxGix;

}
