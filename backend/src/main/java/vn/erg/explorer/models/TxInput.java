package vn.erg.explorer.models;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Reference from a transaction to a box it spends (or reads, for data inputs). */
@Getter
@Setter
@Entity
@Table(name = "tx_input")
public class TxInput {

    @EmbeddedId
    private TxInputId id;

    private long boxGix;

    public boolean isDataInput() {
        return id != null && id.getDataInput() == 1;
    }

}
