package vn.erg.explorer.models;

import lombok.Getter;
import lombok.Setter;

/** Marks a box as spent: by which transaction and in which block. Absent row = unspent. */
@Getter
@Setter
public class BoxSpent {

    private long boxGix;

    private long txGix;

    private long height;

}
