package vn.erg.explorer.services;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import vn.erg.explorer.dtos.BoxDto;
import vn.erg.explorer.utils.ErgoAddress;
import vn.erg.explorer.utils.Registers;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * JSON box (from a full block, the indexer or the mempool) -> {@link BoxDto}. Full-block and
 * mempool boxes carry only the ErgoTree; the address is derived locally.
 */
@Singleton
public class BoxMapper {

    private final TokenService tokens;

    @Inject
    public BoxMapper(TokenService tokens) {
        this.tokens = tokens;
    }

    /** Warms the token cache for every asset in the given box lists (inputs, outputs...) before mapping them. */
    public void prefetchTokens(JsonNode... boxLists) {
        List<String> ids = new ArrayList<>();
        for (JsonNode list : boxLists) {
            for (JsonNode box : list) {
                for (JsonNode a : box.path("assets")) {
                    ids.add(a.path("tokenId").asText());
                }
            }
        }
        tokens.prefetch(ids);
    }

    public BoxDto map(JsonNode n, int index) {
        BoxDto b = new BoxDto();
        b.setBoxId(n.path("boxId").asText());
        b.setValue(n.path("value").asLong());
        b.setErgoTree(n.path("ergoTree").asText(null));
        b.setCreationHeight(n.path("creationHeight").asLong());
        b.setTransactionId(n.path("transactionId").asText(null));
        b.setIndex(n.hasNonNull("index") ? n.path("index").asInt() : index);
        String address = n.path("address").asText(null);
        if (address == null && b.getErgoTree() != null) {
            address = ErgoAddress.fromErgoTree(b.getErgoTree());
        }
        b.setAddress(address);
        for (JsonNode a : n.path("assets")) {
            b.getAssets().add(tokens.asset(a.path("tokenId").asText(), a.path("amount").asLong()));
        }
        JsonNode regs = n.path("additionalRegisters");
        for (Iterator<Map.Entry<String, JsonNode>> it = regs.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> e = it.next();
            b.getRegisters().add(Registers.decode(e.getKey(), e.getValue().asText()));
        }
        if (n.has("spentTransactionId")) {
            String spentBy = n.path("spentTransactionId").asText(null);
            b.setSpent(spentBy != null);
            b.setSpentBy(spentBy);
        }
        return b;
    }

}
