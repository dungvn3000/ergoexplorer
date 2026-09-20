package vn.erg.explorer.dtos;

import lombok.Data;

/** A token amount inside a box or a balance. */
@Data
public class AssetDto {
    private String tokenId;
    private String name;
    private Integer decimals;
    private long amount;
}
