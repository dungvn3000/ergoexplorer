package vn.erg.explorer.services;

import com.fasterxml.jackson.databind.JsonNode;
import vn.erg.explorer.utils.Memo;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import vn.erg.explorer.dtos.MempoolTxDto;
import vn.erg.explorer.dtos.TxDto;
import vn.erg.explorer.node.NodeClient;
import vn.erg.explorer.utils.Parallel;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class MempoolService {

    private static final int LIMIT = 200;

    private final Memo<MempoolTxDto> cache;

    @Inject
    public MempoolService(NodeClient node, TransactionService transactions) {
        this.cache = new Memo<>(Duration.ofSeconds(5), () -> {
                    List<JsonNode> raw = new ArrayList<>();
                    node.get("/transactions/unconfirmed?limit=" + LIMIT + "&offset=0").ifPresent(list -> list.forEach(raw::add));
                    List<TxDto> items = Parallel.map(raw, transactions::fromMempool);
                    MempoolTxDto m = new MempoolTxDto();
                    m.setItems(items);
                    m.setTotal(items.size());
                    m.setSize(items.stream().mapToLong(TxDto::getSize).sum());
                    m.setFees(items.stream().mapToLong(TxDto::getFee).sum());
                    return m;
                });
    }

    public MempoolTxDto list() {
        return cache.get();
    }

}
