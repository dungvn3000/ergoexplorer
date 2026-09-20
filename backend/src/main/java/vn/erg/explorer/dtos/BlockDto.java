package vn.erg.explorer.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class BlockDto {
    private long height;
    private String id;
    private String parentId;
    private long timestamp;
    private int txCount;
    private long size;
    /**
     * Pool / miner label when known, otherwise null.
     */
    private String miner;
    private String minerAddress;
    private long difficulty;
    private String nBits;
    /**
     * What the miner keeps (nanoERG).
     */
    private long reward;
    /**
     * Total emitted by this block = reward + reemitted.
     */
    private long emission;
    /**
     * Part of the emission the miner must forward to the re-emission contract (EIP-27).
     */
    private long reemitted;
    private long fees;
    private String votes;
    private int version;
    private String stateRoot;
    private String txRoot;
    private String adRoot;
    private String extHash;
    private PowDto pow;
    private long confirmations;
    /**
     * Detail only.
     */
    private List<TxDto> transactions;

    /**
     * Lombok names the accessor {@code getNBits()}, which Jackson would expose as {@code nbits} next to the
     * field's {@code nBits}: the explicit name here keeps a single property.
     */
    @JsonProperty("nBits")
    public String getNBits() {
        return nBits;
    }
}
