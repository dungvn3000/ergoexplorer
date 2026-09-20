package vn.erg.explorer.models;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Embeddable
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class TxInputId {

    private long txGix;

    /** 0 = spent input, 1 = read-only data input. */
    private int dataInput;

    private int idx;

}
