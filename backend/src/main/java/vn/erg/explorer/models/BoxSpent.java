package vn.erg.explorer.models;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Marks a box as spent: by which transaction and in which block. Absent row = unspent. */
@Getter
@Setter
@Entity
@Table(name = "box_spent")
public class BoxSpent {

    @Id
    private long boxGix;

    private long txGix;

    private long height;

}
