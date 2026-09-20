package vn.erg.explorer.dtos;

import lombok.Data;

import java.util.List;

@Data
public class TokenDto {
    private String id;
    private String name;
    private int decimals;
    private long supply;
    private String desc;
    private String issueBox;
    private String issueTx;
    private Long issueHeight;
    /** Detail only. Holder count is approximate (capped scan of unspent boxes). */
    private Integer holderCount;
    private List<HolderDto> holders;
    private List<TransferDto> transfers;

    @Data
    public static class HolderDto {
        private Integer rank;
        private String address;
        private long amount;
        /** ERG rich list only: unspent boxes at the address. */
        private Integer boxCount;
    }

    @Data
    public static class TransferDto {
        private String id;
        private long height;
        private long timestamp;
        private String to;
        private long amount;
    }
}
