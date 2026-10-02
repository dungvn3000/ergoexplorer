package vn.erg.explorer.dtos;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.List;

/**
 * Raw token amounts (supply, holdings, transfers) are sent as decimal strings: they go up to 2^63, and JavaScript
 * numbers lose digits past 2^53, so a 1e18 supply would be shown rounded.
 */
@Data
public class TokenDto {
    private String id;
    private String name;
    private int decimals;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private long supply;
    private String desc;
    private String issueBox;
    private String issueTx;
    private Long issueHeight;
    /** Detail only. Holder count is approximate (capped scan of unspent boxes). */
    private Integer holderCount;
    private List<TokenHolderDto> holders;
    private List<TransferDto> transfers;

    /** An address of the ERG rich list: amount in nanoERG. */
    @Data
    public static class HolderDto {
        private Integer rank;
        private String address;
        private long amount;
        /** Unspent boxes at the address. */
        private Integer boxCount;
    }

    /** A holder of a token; the ERG rich list uses {@link HolderDto} (nanoERG fit a JavaScript number closely enough). */
    @Data
    public static class TokenHolderDto {
        private Integer rank;
        private String address;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private long amount;
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
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private long amount;
        /** Issuing transaction: no input carried the token. */
        private boolean mint;
        /** Raw amount destroyed: inputs carried more than the outputs re-created (a burn with no receiver has to = null). */
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private long burned;
        /** What the transaction did to this token: Token issue / Token burn / Re-emission / Token transfer. */
        private String kind;
    }
}
