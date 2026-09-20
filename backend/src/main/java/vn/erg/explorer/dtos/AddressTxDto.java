package vn.erg.explorer.dtos;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** One row of an address' transaction history, seen from that address. */
@Data
public class AddressTxDto {
    private String id;
    private long height;
    private long timestamp;
    /** in / out / self */
    private String dir;
    /** Net nanoERG change for the address (negative when sent). */
    private long amount;
    private List<AssetDto> tokens = new ArrayList<>();
    private long fee;
}
