package vn.erg.explorer.utils;

import vn.erg.explorer.dtos.AssetDto;
import vn.erg.explorer.dtos.BoxDto;
import vn.erg.explorer.dtos.TxDto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Label of a transaction: Block reward / Token issue / Token burn / Re-emission / Token transfer / Transfer. */
public final class TxKind {

    private TxKind() {
    }

    public static String of(TxDto tx) {
        if (tx.isCoinbase()) {
            return "Block reward";
        }
        // a new token takes the id of the transaction's first input box (EIP-4)
        Set<String> inputIds = tx.getInputs().stream().map(BoxDto::getBoxId).collect(Collectors.toSet());
        boolean issue = tx.getOutputs().stream().flatMap(o -> o.getAssets().stream())
                .map(AssetDto::getTokenId).anyMatch(inputIds::contains);
        if (issue) {
            return "Token issue";
        }
        // burnt = some token's inputs carry more than its outputs (the rest simply is not re-created)
        Map<String, Long> spent = totals(tx.getInputs());
        Map<String, Long> created = totals(tx.getOutputs());
        Set<String> burnt = spent.entrySet().stream().filter(e -> e.getValue() > created.getOrDefault(e.getKey(), 0L))
                .map(Map.Entry::getKey).collect(Collectors.toSet());
        if (burnt.stream().anyMatch(id -> !ErgoConstants.REEMISSION_TOKEN.equals(id))) {
            return "Token burn";
        }
        // EIP-27: spending a mining reward must burn its Reemission Tokens and pay as much ERG to re-emission
        if (!burnt.isEmpty()) {
            return "Re-emission";
        }
        return created.isEmpty() ? "Transfer" : "Token transfer";
    }

    private static Map<String, Long> totals(List<BoxDto> boxes) {
        Map<String, Long> m = new HashMap<>();
        for (BoxDto b : boxes) {
            for (AssetDto a : b.getAssets()) {
                m.merge(a.getTokenId(), a.getAmount(), Long::sum);
            }
        }
        return m;
    }
}
