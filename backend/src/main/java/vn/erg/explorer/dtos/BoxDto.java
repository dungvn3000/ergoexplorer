package vn.erg.explorer.dtos;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BoxDto {
    private String boxId;
    private String address;
    private long value;
    private List<AssetDto> assets = new ArrayList<>();
    private long creationHeight;
    private String ergoTree;
    private List<RegisterDto> registers = new ArrayList<>();
    private int index;
    private String transactionId;
    /** Output boxes only: spent, and by which transaction. */
    private Boolean spent;
    private String spentBy;
}
