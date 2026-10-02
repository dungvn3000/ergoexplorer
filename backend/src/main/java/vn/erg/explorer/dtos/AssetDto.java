package vn.erg.explorer.dtos;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

/** A token amount inside a box or a balance; the raw amount is a decimal string (see TokenDto). */
@Data
public class AssetDto {
    private String tokenId;
    private String name;
    private Integer decimals;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private long amount;
}
