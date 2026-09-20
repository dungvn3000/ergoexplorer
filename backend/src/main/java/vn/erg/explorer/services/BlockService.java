package vn.erg.explorer.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import vn.erg.explorer.db.ChainRepository;
import vn.erg.explorer.db.IndexStatus;
import vn.erg.explorer.dtos.BlockDto;
import vn.erg.explorer.dtos.PageDto;
import vn.erg.explorer.dtos.PowDto;
import vn.erg.explorer.dtos.TxDto;
import vn.erg.explorer.node.NodeClient;
import vn.erg.explorer.utils.ErgoAddress;
import vn.erg.explorer.utils.ErgoConstants;
import vn.erg.explorer.utils.Hex;
import vn.erg.explorer.utils.Parallel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Singleton
public class BlockService {

    /**
     * What the block body adds to the header; immutable per block id.
     */
    private record Body(int txCount, long size, long emission, long reemitted, long fees, String minerTree, List<String> txIds) {
    }

    private final Cache<String, Body> bodies = Caffeine.newBuilder().maximumSize(10_000).build();

    private final NodeClient node;
    private final ChainState chain;
    private final HeaderService headers;
    private final TransactionService transactions;
    private final LabelService labels;
    private final ChainRepository repo;
    private final IndexStatus index;

    @Inject
    public BlockService(NodeClient node, ChainState chain, HeaderService headers, TransactionService transactions, LabelService labels,
                        ChainRepository repo, IndexStatus index) {
        this.node = node;
        this.chain = chain;
        this.headers = headers;
        this.transactions = transactions;
        this.labels = labels;
        this.repo = repo;
        this.index = index;
    }

    /** Fills what the DB does not store: label and confirmations. */
    private BlockDto finish(BlockDto b) {
        b.setMiner(labels.label(b.getMinerAddress()));
        b.setConfirmations(chain.confirmations(b.getHeight()));
        return b;
    }

    /**
     * Newest first.
     */
    public PageDto<BlockDto> list(int page, int rowsPerPage) {
        long tip = chain.height();
        long to = tip - (long) (page - 1) * rowsPerPage;
        long from = Math.max(1, to - rowsPerPage + 1);
        List<BlockDto> items = new ArrayList<>();
        if (to >= 1 && index.has(to)) {
            items = repo.blocks(from, to).stream().map(this::finish).toList();
        } else if (to >= 1) {
            List<JsonNode> range = new ArrayList<>(headers.range(from, to));
            Collections.reverse(range);
            items = Parallel.map(range, this::summary);
        }
        return new PageDto<>(items, tip);
    }

    public List<BlockDto> latest(int limit) {
        return list(1, limit).getItems();
    }

    /** By height or by id: the DB when indexed, else the node. */
    public Optional<BlockDto> get(String key) {
        Optional<BlockDto> stored = key.matches("\\d+") ? repo.blockAt(Long.parseLong(key)) : repo.blockById(key);
        if (stored.isPresent()) {
            BlockDto b = finish(stored.get());
            List<TxDto> txs = repo.txsOfBlock(b.getHeight());
            txs.forEach(t -> t.setConfirmations(b.getConfirmations()));
            b.setTransactions(txs);
            return Optional.of(b);
        }
        Optional<JsonNode> header = key.matches("\\d+")
                ? headers.at(Long.parseLong(key))
                : node.get("/blocks/" + key + "/header");
        return header.map(h -> {
            BlockDto b = summary(h);
            Body body = body(b.getId());
            b.setTransactions(Parallel.map(body.txIds(), id -> transactions.get(id).orElse(null))
                    .stream().filter(java.util.Objects::nonNull).toList());
            return b;
        });
    }

    /**
     * Header + body stats, no transactions.
     */
    /** Full block JSON from the node, by height or id. */
    public Optional<JsonNode> raw(String key) {
        Optional<String> id = key.matches("\\d+")
                ? repo.blockAt(Long.parseLong(key)).map(BlockDto::getId).or(() -> headers.at(Long.parseLong(key)).map(h -> h.path("id").asText()))
                : Optional.of(key);
        return id.flatMap(i -> node.get("/blocks/" + i));
    }

    public BlockDto summary(JsonNode h) {
        BlockDto b = new BlockDto();
        b.setHeight(h.path("height").asLong());
        b.setId(h.path("id").asText());
        b.setParentId(h.path("parentId").asText());
        b.setTimestamp(h.path("timestamp").asLong());
        b.setDifficulty(Long.parseLong(h.path("difficulty").asText("0")));
        b.setNBits(Long.toHexString(h.path("nBits").asLong()));
        b.setVotes(votes(h.path("votes").asText("000000")));
        b.setVersion(h.path("version").asInt());
        b.setStateRoot(h.path("stateRoot").asText());
        b.setTxRoot(h.path("transactionsRoot").asText());
        b.setAdRoot(h.path("adProofsRoot").asText());
        b.setExtHash(h.path("extensionHash").asText());
        JsonNode ps = h.path("powSolutions");
        PowDto pow = new PowDto();
        pow.setPk(ps.path("pk").asText());
        pow.setW(ps.path("w").asText());
        pow.setN(ps.path("n").asText());
        pow.setD(ps.path("d").asText("0"));
        b.setPow(pow);
        b.setMinerAddress(ErgoAddress.fromPublicKey(pow.getPk()));
        b.setMiner(labels.label(b.getMinerAddress()));
        b.setConfirmations(chain.confirmations(b.getHeight()));

        Body body = body(b.getId());
        b.setTxCount(body.txCount());
        b.setSize(body.size());
        b.setEmission(body.emission());
        b.setReemitted(body.reemitted());
        b.setReward(body.emission() - body.reemitted());
        b.setFees(body.fees());
        return b;
    }

    private Body body(String blockId) {
        Body cached = bodies.getIfPresent(blockId);
        if (cached != null) {
            return cached;
        }
        Body body = loadBody(blockId);
        bodies.put(blockId, body);
        return body;
    }

    private Body loadBody(String id) {
        JsonNode bt = node.getOrThrow("/blocks/" + id + "/transactions");
        JsonNode txs = bt.path("transactions");
        List<String> ids = new ArrayList<>();
        for (JsonNode t : txs) {
            ids.add(t.path("id").asText());
        }
        long emission = 0;
        long reemitted = 0;
        String minerTree = null;
        int coinbaseIdx = vn.erg.explorer.index.BlockWriter.coinbaseIndex(txs);
        if (coinbaseIdx >= 0) {
            // EIP-27: the miner box holds the whole emission plus re-emission tokens for the part to forward
            JsonNode minerBox = txs.get(coinbaseIdx).path("outputs").get(1);
            emission = minerBox.path("value").asLong();
            minerTree = minerBox.path("ergoTree").asText();
            for (JsonNode a : minerBox.path("assets")) {
                if (ErgoConstants.REEMISSION_TOKEN.equals(a.path("tokenId").asText())) {
                    reemitted = a.path("amount").asLong();
                }
            }
        }
        long fees = 0;
        if (txs.size() > 1 && minerTree != null) {
            // Fees are collected by the last transaction into a single box guarded by the miner's tree
            JsonNode last = txs.get(txs.size() - 1);
            JsonNode outs = last.path("outputs");
            if (outs.size() == 1 && minerTree.equals(outs.get(0).path("ergoTree").asText())) {
                fees = outs.get(0).path("value").asLong();
            }
        }
        return new Body(txs.size(), bt.path("size").asLong(), emission, reemitted, fees, minerTree, ids);
    }

    /**
     * "000000" -> "0,0,0" (three signed parameter votes).
     */
    private static String votes(String hex) {
        try {
            byte[] v = Hex.decode(hex);
            return v[0] + "," + v[1] + "," + v[2];
        } catch (RuntimeException e) {
            return hex;
        }
    }

}
