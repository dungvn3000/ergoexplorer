package vn.erg.explorer.models;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** One main-chain block (header + what the body adds). Reorged blocks are deleted, not flagged. */
@Getter
@Setter
@Entity
@Table(name = "block")
public class Block {

    @Id
    private long height;

    /** Header id (32 bytes). */
    private byte[] id;

    private byte[] parentId;

    private long timestamp;

    private int txCount;

    private int size;

    /** Miner public key from {@code powSolutions.pk}; {@link #minerScriptId} is its P2PK script. */
    private byte[] minerPk;

    private long minerScriptId;

    private long difficulty;

    private long nBits;

    private int version;

    /** Three signed parameter votes. */
    private byte[] votes;

    /** Value of the miner reward box (nanoERG). */
    private long emission;

    /** Part of the emission owed to the re-emission contract (EIP-27). Reward = emission − reemitted. */
    private long reemitted;

    private long fees;

    private byte[] stateRoot;

    private byte[] transactionsRoot;

    private byte[] adProofsRoot;

    private byte[] extensionHash;

    private byte[] powW;

    private byte[] powN;

    /** Big integer in Autolykos v1 blocks, "0" in v2. */
    private String powD;

    public long getReward() {
        return emission - reemitted;
    }

}
