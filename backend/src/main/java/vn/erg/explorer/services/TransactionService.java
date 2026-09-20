package vn.erg.explorer.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import vn.erg.explorer.db.ChainRepository;
import vn.erg.explorer.dtos.BlockDto;
import vn.erg.explorer.dtos.BoxDto;
import vn.erg.explorer.dtos.TxDto;
import vn.erg.explorer.node.NodeClient;
import vn.erg.explorer.utils.ErgoConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import vn.erg.explorer.utils.Parallel;

@Singleton
public class TransactionService {

    /** Confirmed transactions are immutable; confirmations are recomputed on every read. */
    private final Cache<String, TxDto> confirmed = Caffeine.newBuilder().maximumSize(20_000).build();

    private final NodeClient node;
    private final ChainState chain;
    private final BoxMapper boxes;
    private final ChainRepository repo;

    @Inject
    public TransactionService(NodeClient node, ChainState chain, BoxMapper boxes, ChainRepository repo) {
        this.node = node;
        this.chain = chain;
        this.boxes = boxes;
        this.repo = repo;
    }

    /** Confirmed (indexer) first, then the mempool. */
    public Optional<TxDto> get(String id) {
        // DB first, then the node's indexer, then the mempool
        Optional<TxDto> stored = repo.tx(id);
        if (stored.isPresent()) {
            TxDto t = stored.get();
            t.setBlockId(repo.blockAt(t.getHeight()).map(BlockDto::getId).orElse(null));
            t.setConfirmations(chain.confirmations(t.getHeight()));
            return stored;
        }
        TxDto tx = confirmed.getIfPresent(id);
        if (tx == null) {
            Optional<JsonNode> n = node.get("/blockchain/transaction/byId/" + id);
            if (n.isPresent()) {
                tx = fromIndexed(n.get());
                confirmed.put(id, tx);
            }
        }
        if (tx != null) {
            tx.setConfirmations(chain.confirmations(tx.getHeight()));
            return Optional.of(tx);
        }
        return node.get("/transactions/unconfirmed/byTransactionId/" + id).map(this::fromMempool);
    }

    /** Indexed transaction (inputs and outputs carry address, value and spent status). */
    public TxDto fromIndexed(JsonNode n) {
        TxDto tx = new TxDto();
        tx.setId(n.path("id").asText());
        tx.setBlockId(n.path("blockId").asText(null));
        tx.setHeight(n.path("inclusionHeight").asLong());
        tx.setTimestamp(n.path("timestamp").asLong());
        tx.setIndex(n.path("index").asInt());
        tx.setSize(n.path("size").asLong());
        fill(tx, n);
        tx.setConfirmations(chain.confirmations(tx.getHeight()));
        return tx;
    }

    /** Mempool transaction: inputs are box ids only and are resolved through the indexer. */
    public TxDto fromMempool(JsonNode n) {
        TxDto tx = new TxDto();
        tx.setId(n.path("id").asText());
        tx.setPending(true);
        tx.setTimestamp(System.currentTimeMillis());
        tx.setSize(n.path("size").asLong());
        // Mempool inputs carry only box ids: resolve them in parallel through the indexer
        List<JsonNode> inputs = new ArrayList<>();
        n.path("inputs").forEach(inputs::add);
        List<JsonNode> resolved = Parallel.map(inputs, in -> {
            String boxId = in.path("boxId").asText();
            // Confirmed boxes come from the indexer; a box created by another pending tx only exists in the UTXO set + pool
            return node.get("/blockchain/box/byId/" + boxId)
                    .or(() -> node.get("/utxo/withPool/byId/" + boxId))
                    .orElse(in);
        });
        boxes.prefetchTokens(JsonNodeFactory.instance.arrayNode().addAll(resolved), n.path("outputs"));
        int i = 0;
        for (JsonNode in : resolved) {
            BoxDto box = boxes.map(in, i++);
            box.setSpent(null);
            box.setSpentBy(null);
            tx.getInputs().add(box);
        }
        i = 0;
        for (JsonNode out : n.path("outputs")) {
            BoxDto box = boxes.map(out, i++);
            box.setSpent(false);
            tx.getOutputs().add(box);
        }
        tx.setFee(feeOf(tx));
        tx.setKind(kindOf(tx));
        return tx;
    }

    private void fill(TxDto tx, JsonNode n) {
        boxes.prefetchTokens(n.path("inputs"), n.path("dataInputs"), n.path("outputs"));
        int i = 0;
        for (JsonNode in : n.path("inputs")) {
            BoxDto box = boxes.map(in, i++);
            box.setSpent(null);
            box.setSpentBy(null);
            tx.getInputs().add(box);
        }
        i = 0;
        for (JsonNode in : n.path("dataInputs")) {
            BoxDto box = boxes.map(in, i++);
            box.setSpent(null);
            box.setSpentBy(null);
            tx.getDataInputs().add(box);
        }
        i = 0;
        for (JsonNode out : n.path("outputs")) {
            tx.getOutputs().add(boxes.map(out, i++));
        }
        tx.setCoinbase(isCoinbase(tx));
        tx.setFee(feeOf(tx));
        tx.setKind(kindOf(tx));
    }

    /** The emission transaction: spends the emission box back to itself plus the miner reward (see BlockWriter.coinbaseIndex). */
    static boolean isCoinbase(TxDto tx) {
        return tx.getOutputs().size() >= 2
                && (ErgoConstants.EMISSION_TREE.equals(tx.getOutputs().get(0).getErgoTree())
                    || ErgoConstants.EMISSION_ADDRESS.equals(tx.getOutputs().get(0).getAddress()));
    }

    static long feeOf(TxDto tx) {
        long fee = 0;
        for (BoxDto o : tx.getOutputs()) {
            if (ErgoConstants.FEE_TREE.equals(o.getErgoTree()) || ErgoConstants.FEE_ADDRESS.equals(o.getAddress())) {
                fee += o.getValue();
            }
        }
        return fee;
    }

    static String kindOf(TxDto tx) {
        if (tx.isCoinbase()) {
            return "Block reward";
        }
        boolean tokens = tx.getOutputs().stream().anyMatch(o -> !o.getAssets().isEmpty());
        return tokens ? "Token transfer" : "Transfer";
    }

}
