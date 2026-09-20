package vn.erg.explorer.services;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import vn.erg.explorer.db.ChainRepository;
import vn.erg.explorer.db.IndexStatus;
import vn.erg.explorer.dtos.*;
import vn.erg.explorer.node.NodeClient;
import vn.erg.explorer.utils.ErgoAddress;

import java.util.*;
import vn.erg.explorer.utils.Parallel;

@Singleton
public class AddressService {

    private final NodeClient node;
    private final TransactionService transactions;
    private final TokenService tokens;
    private final LabelService labels;
    private final HeaderService headers;
    private final ChainRepository repo;
    private final IndexStatus index;
    private final ChainState chain;
    private final BoxMapper boxes;

    @Inject
    public AddressService(NodeClient node, TransactionService transactions, TokenService tokens, LabelService labels, HeaderService headers,
                          ChainRepository repo, IndexStatus index, ChainState chain, BoxMapper boxes) {
        this.boxes = boxes;
        this.node = node;
        this.transactions = transactions;
        this.tokens = tokens;
        this.labels = labels;
        this.headers = headers;
        this.repo = repo;
        this.index = index;
        this.chain = chain;
    }

    /** From the MySQL index — exact history paging; only valid once the index is complete. */
    private AddressDto fromIndex(String address, int page, int rowsPerPage) {
        AddressDto a = new AddressDto();
        a.setAddress(address);
        a.setLabel(labels.label(address));
        a.setContract(!ErgoAddress.isP2PK(address));
        a.setBalance(repo.balance(address));
        a.setTokens(repo.tokenBalances(address));
        a.getTokens().forEach(t -> {
            if (t.getName() == null) {
                AssetDto meta = tokens.asset(t.getTokenId(), t.getAmount());
                t.setName(meta.getName());
                t.setDecimals(meta.getDecimals());
            }
        });
        a.setBoxes(repo.unspentBoxCount(address));
        a.setTxCount(repo.txCount(address));
        for (TxDto tx : repo.addressTxs(address, page, rowsPerPage)) {
            tx.setConfirmations(chain.confirmations(tx.getHeight()));
            a.getTxs().add(view(tx, address));
        }
        Long[] seen = repo.firstLastSeen(address);
        a.setFirstSeen(seen[0]);
        a.setLastSeen(seen[1]);
        a.setErgoTree(repo.ergoTree(address));
        // unconfirmed balance change is only known to the node's mempool
        node.postText("/blockchain/balance", address).ifPresent(bal -> a.setUnconfirmed(bal.path("unconfirmed").path("nanoErgs").asLong()));
        return a;
    }

    public boolean isValid(String address) {
        return ErgoAddress.looksLikeAddress(address)
                && node.get("/utils/address/" + address).map(n -> n.path("isValid").asBoolean()).orElse(false);
    }

    /**
     * Unspent boxes, newest first. From the index when it is complete; otherwise the node's indexer,
     * which pages but returns no total: one extra row is requested to know whether a next page exists
     * (the node scans every box of the address, so busy contracts take tens of seconds there).
     */
    public Optional<PageDto<BoxDto>> unspentBoxes(String address, int page, int rowsPerPage) {
        if (!isValid(address)) {
            return Optional.empty();
        }
        if (index.isSynced()) {
            return Optional.of(repo.unspentBoxes(address, page, rowsPerPage));
        }
        long offset = (long) (page - 1) * rowsPerPage;
        List<JsonNode> got = new ArrayList<>();
        node.postText("/blockchain/box/unspent/byAddress?offset=" + offset + "&limit=" + (rowsPerPage + 1) + "&sortDirection=desc", address)
                .ifPresent(res -> res.forEach(got::add));
        boolean more = got.size() > rowsPerPage;
        List<BoxDto> items = new ArrayList<>();
        for (JsonNode n : got.subList(0, Math.min(got.size(), rowsPerPage))) {
            BoxDto b = boxes.map(n, 0);
            b.setSpent(false);
            items.add(b);
        }
        return Optional.of(new PageDto<>(items, offset + items.size() + (more ? rowsPerPage : 0)));
    }

    public Optional<AddressDto> get(String address, int page, int rowsPerPage) {
        if (!isValid(address)) {
            return Optional.empty();
        }
        if (index.isSynced()) {
            return Optional.of(fromIndex(address, page, rowsPerPage));
        }
        AddressDto a = new AddressDto();
        a.setAddress(address);
        a.setLabel(labels.label(address));
        a.setContract(!ErgoAddress.isP2PK(address));

        node.postText("/blockchain/balance", address).ifPresent(bal -> {
            a.setBalance(bal.path("confirmed").path("nanoErgs").asLong());
            a.setUnconfirmed(bal.path("unconfirmed").path("nanoErgs").asLong());
            for (JsonNode t : bal.path("confirmed").path("tokens")) {
                AssetDto asset = tokens.asset(t.path("tokenId").asText(), t.path("amount").asLong());
                if (t.hasNonNull("name")) {
                    asset.setName(t.path("name").asText());
                    asset.setDecimals(t.path("decimals").asInt(0));
                }
                a.getTokens().add(asset);
            }
        });

        // Transaction count and the newest transaction: one cheap call (the node prices byAddress per returned tx)
        node.postText("/blockchain/transaction/byAddress?offset=0&limit=1", address).ifPresent(res -> {
            a.setTxCount(res.path("total").asLong());
            JsonNode items = res.path("items");
            if (!items.isEmpty()) {
                a.setLastSeen(items.get(0).path("timestamp").asLong());
            }
        });

        // History page: boxes of the address are cheap to list (newest page first, ascending inside a page);
        // every box names the tx that created it and, when spent, the tx that spent it. Those tx ids are
        // fetched in parallel. Paging is by boxes, so page boundaries are approximate for busy addresses.
        int boxesPerPage = rowsPerPage * 2;
        int offset = (page - 1) * boxesPerPage;
        List<JsonNode> boxes = new ArrayList<>();
        long[] boxTotal = {0};
        node.postText("/blockchain/box/byAddress?offset=" + offset + "&limit=" + boxesPerPage, address)
                .ifPresent(res -> {
                    boxTotal[0] = res.path("total").asLong();
                    res.path("items").forEach(boxes::add);
                });
        Collections.reverse(boxes);
        LinkedHashSet<String> txIds = new LinkedHashSet<>();
        for (JsonNode box : boxes) {
            String spentBy = box.path("spentTransactionId").asText(null);
            if (spentBy != null) {
                txIds.add(spentBy);
            }
            txIds.add(box.path("transactionId").asText());
            if (a.getErgoTree() == null) {
                a.setErgoTree(box.path("ergoTree").asText(null));
            }
        }
        List<TxDto> txs = Parallel.map(new ArrayList<>(txIds), id -> transactions.get(id).orElse(null));
        txs.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingLong(TxDto::getHeight).reversed())
                .limit(rowsPerPage)
                .forEach(tx -> a.getTxs().add(view(tx, address)));

        if (boxTotal[0] > 0) {
            // First activity: the oldest box received by the address
            node.postText("/blockchain/box/byAddress?offset=" + (boxTotal[0] - 1) + "&limit=1", address)
                    .ifPresent(res -> {
                        JsonNode items = res.path("items");
                        if (!items.isEmpty()) {
                            a.setFirstSeen(headers.timestampAt(items.get(0).path("inclusionHeight").asLong()));
                        }
                    });
        }
        if (a.getErgoTree() == null && ErgoAddress.isP2PK(address)) {
            node.get("/utils/addressToRaw/" + address).ifPresent(r -> a.setErgoTree("0008cd" + r.path("raw").asText()));
        }
        return Optional.of(a);
    }

    /** Net effect of a transaction on one address: direction, ERG delta and token deltas. */
    private static AddressTxDto view(TxDto tx, String address) {
        AddressTxDto v = new AddressTxDto();
        v.setId(tx.getId());
        v.setHeight(tx.getHeight());
        v.setTimestamp(tx.getTimestamp());
        v.setFee(tx.getFee());

        long in = 0, out = 0;
        Map<String, AssetDto> delta = new LinkedHashMap<>();
        for (BoxDto b : tx.getInputs()) {
            if (address.equals(b.getAddress())) {
                out += b.getValue();
                b.getAssets().forEach(t -> merge(delta, t, -t.getAmount()));
            }
        }
        for (BoxDto b : tx.getOutputs()) {
            if (address.equals(b.getAddress())) {
                in += b.getValue();
                b.getAssets().forEach(t -> merge(delta, t, t.getAmount()));
            }
        }
        v.setAmount(in - out);
        v.setDir(out == 0 ? "in" : in == 0 ? "out" : (in - out) < 0 ? "out" : "self");
        delta.values().stream().filter(t -> t.getAmount() != 0).forEach(v.getTokens()::add);
        return v;
    }

    private static void merge(Map<String, AssetDto> delta, AssetDto t, long amount) {
        delta.compute(t.getTokenId(), (k, cur) -> {
            if (cur == null) {
                cur = new AssetDto();
                cur.setTokenId(t.getTokenId());
                cur.setName(t.getName());
                cur.setDecimals(t.getDecimals());
            }
            cur.setAmount(cur.getAmount() + amount);
            return cur;
        });
    }

}
