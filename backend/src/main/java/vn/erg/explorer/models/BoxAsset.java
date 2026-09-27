package vn.erg.explorer.models;

import lombok.Getter;
import lombok.Setter;

/** A token amount carried by a box; key (box_gix, idx). */
@Getter
@Setter
public class BoxAsset {

    private long boxGix;

    /** Position of the asset in the box. */
    private int idx;

    private byte[] tokenId;

    /** Raw amount (apply {@link Token#getDecimals()} to display). */
    private long amount;

}
