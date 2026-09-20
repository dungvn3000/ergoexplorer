package vn.erg.explorer.services;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import vn.erg.explorer.dtos.SearchResultDto;
import vn.erg.explorer.node.NodeClient;
import vn.erg.explorer.utils.ErgoAddress;
import vn.erg.explorer.utils.Hex;

import java.util.Optional;

/** Resolves what a search string is: height, block id, transaction id, box id, token id or address. */
@Singleton
public class SearchService {

    private final NodeClient node;
    private final TransactionService transactions;
    private final TokenService tokens;
    private final AddressService addresses;

    @Inject
    public SearchService(NodeClient node, TransactionService transactions, TokenService tokens, AddressService addresses) {
        this.node = node;
        this.transactions = transactions;
        this.tokens = tokens;
        this.addresses = addresses;
    }

    public Optional<SearchResultDto> resolve(String q) {
        String s = q == null ? "" : q.trim();
        if (s.isEmpty()) {
            return Optional.empty();
        }
        if (s.matches("\\d+")) {
            return Optional.of(new SearchResultDto("block", s));
        }
        if (Hex.isHex64(s)) {
            String id = s.toLowerCase();
            if (node.get("/blocks/" + id + "/header").isPresent()) {
                return Optional.of(new SearchResultDto("block", id));
            }
            if (transactions.get(id).isPresent()) {
                return Optional.of(new SearchResultDto("transaction", id));
            }
            if (tokens.meta(id).isPresent()) {
                return Optional.of(new SearchResultDto("token", id));
            }
            if (node.get("/blockchain/box/byId/" + id).isPresent()) {
                return Optional.of(new SearchResultDto("box", id));
            }
            return Optional.empty();
        }
        if (ErgoAddress.looksLikeAddress(s) && addresses.isValid(s)) {
            return Optional.of(new SearchResultDto("address", s));
        }
        return Optional.empty();
    }

}
