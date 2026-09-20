package vn.erg.explorer.models;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * An output box. Keyed by {@code gix}, a global index the indexer assigns in chain order (so
 * inserts are sequential and gix orders boxes by age). Address and ErgoTree live in {@link Script};
 * whether the box is spent lives in {@link BoxSpent}.
 */
@Getter
@Setter
@Entity
@Table(name = "box")
public class Box {

    @Id
    private long gix;

    /** Box hash (32 bytes). */
    private byte[] id;

    /** Transaction that created the box (0 = genesis). */
    private long txGix;

    /** Output index in that transaction. */
    private int idx;

    private long blockHeight;

    /** nanoERG */
    private long value;

    private long scriptId;

    private long creationHeight;

    /** Raw registers as the node serializes them: {@code {"R4":"0e..","R5":".."}}; decoded on read. */
    private String registers;

}
