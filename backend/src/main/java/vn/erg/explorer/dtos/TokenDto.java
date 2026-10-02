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
        /** Biggest sender (null for a mint); fromMore = other senders. */
        private String from;
        private int fromMore;
        /** Biggest receiver; toMore = other receivers. */
        private String to;
        private int toMore;
        /** Net amount the receivers gained (the whole output for a self transfer). */
        private long amount;
        /** Issuing transaction: no input carried the token. */
        private boolean mint;
        /** Raw amount destroyed: inputs carried more than the outputs re-created (a burn with no receiver has to = null). */
        private long burned;
        /** What the transaction did to this token: Token issue / Token burn / Re-emission / Token transfer. */
        private String kind;
    }
}
