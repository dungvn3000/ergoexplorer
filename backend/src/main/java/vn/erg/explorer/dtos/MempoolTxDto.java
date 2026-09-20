package vn.erg.explorer.dtos;

import lombok.Data;

import java.util.List;

@Data
public class MempoolTxDto {
    private List<TxDto> items;
    private int total;
    private long size;
    private long fees;
}
