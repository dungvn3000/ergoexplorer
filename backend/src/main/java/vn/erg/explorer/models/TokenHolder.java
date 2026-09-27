package vn.erg.explorer.models;

import lombok.Getter;
import lombok.Setter;

/** Aggregate maintained by the indexer: amount of a token held in unspent boxes per script (address); key (token_id, script_id). */
@Getter
@Setter
public class TokenHolder {

    private byte[] tokenId;

    private long scriptId;

    /** Raw amount (apply the token's decimals to display). */
    private long amount;

}
