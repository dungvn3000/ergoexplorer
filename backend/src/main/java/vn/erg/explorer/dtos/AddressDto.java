package vn.erg.explorer.dtos;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AddressDto {
    private String address;
    private String label;
    private boolean contract;
    private long balance;
    private long unconfirmed;
    private List<AssetDto> tokens = new ArrayList<>();
    private long txCount;
    /** Unspent boxes at the address (from the index only). */
    private Integer boxes;
    private List<AddressTxDto> txs = new ArrayList<>();
    private String ergoTree;
    private Long firstSeen;
    private Long lastSeen;
}
