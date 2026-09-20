package vn.erg.explorer.models;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Aggregate maintained by the indexer: amount of a token held in unspent boxes per script (address). */
@Getter
@Setter
@Entity
@Table(name = "token_holder")
public class TokenHolder {

    @EmbeddedId
    private TokenHolderId id;

    /** Raw amount (apply the token's decimals to display). */
    private long amount;

}
