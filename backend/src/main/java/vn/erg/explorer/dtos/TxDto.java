package vn.erg.explorer.dtos;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TxDto {
    private String id;
    private String blockId;
    private Long height;
    private long timestamp;
    private Integer index;
    private long size;
    private long fee;
    /** Block reward / Transfer / Token transfer */
    private String kind;
    private boolean coinbase;
    private boolean pending;
    private long confirmations;
    private List<BoxDto> inputs = new ArrayList<>();
    private List<BoxDto> dataInputs = new ArrayList<>();
    private List<BoxDto> outputs = new ArrayList<>();
}
