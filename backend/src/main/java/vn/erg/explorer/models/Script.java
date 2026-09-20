package vn.erg.explorer.models;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** An ErgoTree and its address (1:1), stored once and referenced by every box guarded by it. */
@Getter
@Setter
@Entity
@Table(name = "script")
public class Script {

    @Id
    private long id;

    /** SHA-256 of the ErgoTree bytes (dedupe key). */
    private byte[] treeHash;

    /** SHA-256 of the address string (lookup by address). */
    private byte[] addrHash;

    private String ergoTree;

    private String address;

    private boolean p2pk;

}
