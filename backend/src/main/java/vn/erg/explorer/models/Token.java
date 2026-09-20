package vn.erg.explorer.models;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** EIP-4 token: minted by the transaction whose first input box id equals the token id. */
@Getter
@Setter
@Entity
@Table(name = "token")
public class Token {

    @Id
    private byte[] id;

    /** Issuing box (the first box carrying the token). */
    private long boxGix;

    private long txGix;

    private long blockHeight;

    /** From R4 of the issuing box. */
    private String name;

    /** From R5. */
    private String description;

    /** From R6. */
    private int decimals;

    private long emissionAmount;

}
