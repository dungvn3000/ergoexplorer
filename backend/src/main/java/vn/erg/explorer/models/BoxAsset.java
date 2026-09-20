package vn.erg.explorer.models;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A token amount carried by a box. */
@Getter
@Setter
@Entity
@Table(name = "box_asset")
public class BoxAsset {

    @EmbeddedId
    private BoxAssetId id;

    private byte[] tokenId;

    /** Raw amount (apply {@link Token#getDecimals()} to display). */
    private long amount;

}
