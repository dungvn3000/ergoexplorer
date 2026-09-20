package vn.erg.explorer.dtos;

import lombok.Data;

@Data
public class PowDto {
    private String pk;
    private String w;
    private String n;
    /** Big integer in Autolykos v1 blocks, 0 in v2 — kept as text. */
    private String d;
}
