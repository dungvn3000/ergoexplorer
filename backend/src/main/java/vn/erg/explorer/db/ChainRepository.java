package vn.erg.explorer.db;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import vn.erg.explorer.daos.*;
import vn.erg.explorer.dtos.*;
import vn.erg.explorer.models.*;
import vn.erg.explorer.utils.Hex;
import vn.erg.explorer.utils.Memo;
import vn.erg.explorer.utils.Registers;

import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read side of the MySQL chain index: composes the per-table DAOs into the DTOs the services
 * return. Hashes are BINARY in the DB and hex strings in the DTOs; addresses and ErgoTrees come
 * from the {@code script} table; transactions and boxes are referenced by their gix.
 */
@Singleton
public class ChainRepository {

    private final BlockDao blocks;
    private final TxDao txs;
    private final BoxDao boxes;
    private final BoxAssetDao assets;
    private final BoxSpentDao spends;
    private final TxInputDao inputs;
    private final TokenDao tokens;
    private final ScriptDao scripts;
    private final AddressBalanceDao balances;
    private final TokenHolderDao holders;
    private final IndexerStateDao state;
    private final ObjectMapper mapper;

    @Inject
    public ChainRepository(BlockDao blocks, TxDao txs, BoxDao boxes, BoxAssetDao assets, BoxSpentDao spends, TxInputDao inputs,
                           TokenDao tokens, ScriptDao scripts, AddressBalanceDao balances, TokenHolderDao holders,
                           IndexerStateDao state, ObjectMapper mapper) {
        this.blocks = blocks;
        this.txs = txs;
        this.boxes = boxes;
        this.assets = assets;
        this.spends = spends;
        this.inputs = inputs;
        this.tokens = tokens;
        this.scripts = scripts;
        this.balances = balances;
        this.holders = holders;
        this.state = state;
        this.mapper = mapper;
    }

    public long indexedHeight() {
        return state.height();
    }

    // ── storage ──────────────────────────────────────────────

    /** Size of the index on disk (information_schema, so an estimate); cached a minute. */
    private final Memo<Map<String, Object>> storage = new Memo<>(Duration.ofSeconds(60), this::loadStorage);

    public Map<String, Object> storage() {
        return storage.get();
    }

    private Map<String, Object> loadStorage() {
        io.ebean.SqlRow r = blocks.db().sqlQuery("SELECT COALESCE(SUM(data_length), 0) AS d, COALESCE(SUM(index_length), 0) AS i, COALESCE(SUM(table_rows), 0) AS r"
                + " FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history'").findOne();
        long data = r == null ? 0 : r.getLong("d"), index = r == null ? 0 : r.getLong("i");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("dataBytes", data);
        out.put("indexBytes", index);
        out.put("totalBytes", data + index);
        out.put("rows", r == null ? 0 : r.getLong("r"));
        return out;
    }

    private static String hex(byte[] b) {
        return b == null ? null : Hex.encode(b);
    }

    private Map<Long, Script> scriptsOf(Collection<Long> ids) {
        return scripts.findByIds(ids.stream().distinct().toList()).stream().collect(Collectors.toMap(Script::getId, Function.identity()));
    }

    // ── blocks ───────────────────────────────────────────────

    public Optional<BlockDto> blockAt(long height) {
        return blocks.findByHeight(height).map(this::toBlock);
    }

    public Optional<BlockDto> blockById(String id) {
        return blocks.findByHash(id).map(this::toBlock);
    }

    /** Blocks with height in [from, to], newest first. */
    public List<BlockDto> blocks(long from, long to) {
        List<Block> list = blocks.findRange(from, to);
        Map<Long, Script> miners = scriptsOf(list.stream().map(Block::getMinerScriptId).toList());
        return list.stream().map(b -> toBlock(b, miners.get(b.getMinerScriptId()))).toList();
    }

    /** Miner address -> number of blocks among the last {@code n} indexed blocks. */
    public Map<String, Integer> minerShare(int n) {
        Map<Long, Integer> byScript = blocks.minerShare(n);
        Map<Long, Script> miners = scriptsOf(byScript.keySet());
        Map<String, Integer> out = new LinkedHashMap<>();
        byScript.forEach((id, count) -> out.put(miners.get(id).getAddress(), count));
        return out;
    }

    private BlockDto toBlock(Block m) {
        return toBlock(m, scripts.findById(m.getMinerScriptId()).orElse(null));
    }

    private static BlockDto toBlock(Block m, Script miner) {
        BlockDto b = new BlockDto();
        b.setHeight(m.getHeight());
        b.setId(hex(m.getId()));
        b.setParentId(hex(m.getParentId()));
        b.setTimestamp(m.getTimestamp());
        b.setTxCount(m.getTxCount());
        b.setSize(m.getSize());
        b.setMinerAddress(miner == null ? null : miner.getAddress());
        b.setDifficulty(m.getDifficulty());
        b.setNBits(Long.toHexString(m.getNBits()));
        byte[] v = m.getVotes();
        b.setVotes(v == null || v.length < 3 ? "0,0,0" : v[0] + "," + v[1] + "," + v[2]);
        b.setVersion(m.getVersion());
        b.setEmission(m.getEmission());
        b.setReemitted(m.getReemitted());
        b.setReward(m.getReward());
        b.setFees(m.getFees());
        b.setStateRoot(hex(m.getStateRoot()));
        b.setTxRoot(hex(m.getTransactionsRoot()));
        b.setAdRoot(hex(m.getAdProofsRoot()));
        b.setExtHash(hex(m.getExtensionHash()));
        PowDto pow = new PowDto();
        pow.setPk(hex(m.getMinerPk()));
        pow.setW(hex(m.getPowW()));
        pow.setN(hex(m.getPowN()));
        pow.setD(m.getPowD());
        b.setPow(pow);
        return b;
    }

    // ── transactions ─────────────────────────────────────────

    public List<TxDto> txsOfBlock(long height) {
        return assemble(txs.findByBlock(height));
    }

    public Optional<TxDto> tx(String id) {
        return txs.findByHash(id).map(t -> assemble(List.of(t)).get(0));
    }

    /**
     * Entities -> DTOs with their boxes, in a handful of bulk queries: outputs, input references,
     * the boxes they point to, the scripts of all boxes, their assets and the token names.
     */
    private List<TxDto> assemble(List<Tx> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, TxDto> byGix = new LinkedHashMap<>();
        for (Tx t : list) {
            TxDto d = new TxDto();
            d.setId(hex(t.getId()));
            d.setHeight(t.getBlockHeight());
            d.setIndex(t.getIdx());
            d.setTimestamp(t.getTimestamp());
            d.setSize(t.getSize());
            d.setFee(t.getFee());
            d.setCoinbase(t.isCoinbase());
            byGix.put(t.getGix(), d);
        }
        List<Long> gixes = new ArrayList<>(byGix.keySet());

        List<Box> outputs = boxes.findOutputs(gixes);
        List<TxInput> refs = inputs.findByTxGixes(gixes);
        Map<Long, Box> referenced = boxes.findByGixes(refs.stream().map(TxInput::getBoxGix).distinct().toList())
                .stream().collect(Collectors.toMap(Box::getGix, Function.identity()));

        // Scripts and creating-tx hashes of every box involved
        List<Box> all = new ArrayList<>(outputs);
        all.addAll(referenced.values());
        Map<Long, Script> scriptById = scriptsOf(all.stream().map(Box::getScriptId).toList());
        Map<Long, String> txHashByGix = new HashMap<>();
        byGix.forEach((g, d) -> txHashByGix.put(g, d.getId()));
        List<Long> otherTxs = referenced.values().stream().map(Box::getTxGix).filter(g -> g > 0 && !txHashByGix.containsKey(g)).distinct().toList();
        txs.findByGixes(otherTxs).forEach(t -> txHashByGix.put(t.getGix(), hex(t.getId())));
        Map<Long, BoxSpent> spent = spends.findByBoxGixes(outputs.stream().map(Box::getGix).toList());
        List<Long> spendingTxs = spent.values().stream().map(BoxSpent::getTxGix).filter(g -> !txHashByGix.containsKey(g)).distinct().toList();
        txs.findByGixes(spendingTxs).forEach(t -> txHashByGix.put(t.getGix(), hex(t.getId())));

        // A box can appear twice in one block (output of tx A, input of tx B): keep every DTO per box gix
        Map<Long, List<BoxDto>> dtosByBox = new HashMap<>();
        for (Box box : outputs) {
            BoxDto d = toBox(box, scriptById.get(box.getScriptId()), txHashByGix.get(box.getTxGix()));
            BoxSpent s = spent.get(box.getGix());
            d.setSpent(s != null);
            d.setSpentBy(s == null ? null : txHashByGix.get(s.getTxGix()));
            dtosByBox.computeIfAbsent(box.getGix(), k -> new ArrayList<>()).add(d);
            byGix.get(box.getTxGix()).getOutputs().add(d);
        }
        for (TxInput ref : refs) {
            Box box = referenced.get(ref.getBoxGix());
            if (box == null) {
                continue;
            }
            BoxDto d = toBox(box, scriptById.get(box.getScriptId()), txHashByGix.get(box.getTxGix()));
            d.setIndex(ref.getId().getIdx());
            dtosByBox.computeIfAbsent(box.getGix(), k -> new ArrayList<>()).add(d);
            TxDto t = byGix.get(ref.getId().getTxGix());
            (ref.isDataInput() ? t.getDataInputs() : t.getInputs()).add(d);
        }

        attachAssets(dtosByBox);
        for (TxDto t : byGix.values()) {
            t.setKind(kindOf(t));
        }
        return new ArrayList<>(byGix.values());
    }

    /** Loads the assets of all boxes at once and the names of the tokens involved. */
    private void attachAssets(Map<Long, List<BoxDto>> dtosByBox) {
        if (dtosByBox.isEmpty()) {
            return;
        }
        List<BoxAsset> list = assets.findByBoxGixes(new ArrayList<>(dtosByBox.keySet()));
        Map<String, Token> meta = tokens.findByHashes(list.stream().map(a -> hex(a.getTokenId())).distinct().toList())
                .stream().collect(Collectors.toMap(t -> hex(t.getId()), Function.identity()));
        for (BoxAsset a : list) {
            String tokenId = hex(a.getTokenId());
            for (BoxDto box : dtosByBox.get(a.getId().getBoxGix())) {
                box.getAssets().add(toAsset(tokenId, a.getAmount(), meta.get(tokenId)));
            }
        }
    }

    private static AssetDto toAsset(String tokenId, long amount, Token meta) {
        AssetDto a = new AssetDto();
        a.setTokenId(tokenId);
        a.setAmount(amount);
        if (meta != null) {
            a.setName(meta.getName());
            a.setDecimals(meta.getDecimals());
        }
        return a;
    }

    private static String kindOf(TxDto t) {
        if (t.isCoinbase()) {
            return "Block reward";
        }
        return t.getOutputs().stream().anyMatch(o -> !o.getAssets().isEmpty()) ? "Token transfer" : "Transfer";
    }

    private BoxDto toBox(Box m, Script script, String txHash) {
        BoxDto b = new BoxDto();
        b.setBoxId(hex(m.getId()));
        b.setTransactionId(txHash);
        b.setIndex(m.getIdx());
        b.setValue(m.getValue());
        if (script != null) {
            b.setAddress(script.getAddress());
            b.setErgoTree(script.getErgoTree());
        }
        b.setCreationHeight(m.getCreationHeight());
        String regs = m.getRegisters();
        if (regs != null && !regs.isBlank()) {
            try {
                JsonNode n = mapper.readTree(regs);
                for (Iterator<Map.Entry<String, JsonNode>> it = n.fields(); it.hasNext(); ) {
                    Map.Entry<String, JsonNode> e = it.next();
                    b.getRegisters().add(Registers.decode(e.getKey(), e.getValue().asText()));
                }
            } catch (Exception ignored) {
                // malformed registers JSON: shown as none
            }
        }
        return b;
    }

    public Optional<BoxDto> box(String id) {
        return boxes.findByHash(id).map(m -> {
            String txHash = m.getTxGix() == 0 ? null : txs.findByGixes(List.of(m.getTxGix())).stream().findFirst().map(t -> hex(t.getId())).orElse(null);
            BoxDto d = toBox(m, scripts.findById(m.getScriptId()).orElse(null), txHash);
            BoxSpent s = spends.findByBoxGixes(List.of(m.getGix())).get(m.getGix());
            d.setSpent(s != null);
            d.setSpentBy(s == null ? null : txs.findByGixes(List.of(s.getTxGix())).stream().findFirst().map(t -> hex(t.getId())).orElse(null));
            attachAssets(Map.of(m.getGix(), List.of(d)));
            return d;
        });
    }

    // ── addresses ────────────────────────────────────────────

    /** The script of an address, empty when the address never appeared on chain (within the index). */
    public Optional<Script> script(String address) {
        return scripts.findByAddress(address);
    }

    public long balance(String address) {
        return script(address).flatMap(s -> balances.findByScript(s.getId())).map(AddressBalance::getNanoErg).orElse(0L);
    }

    public int unspentBoxCount(String address) {
        return script(address).flatMap(s -> balances.findByScript(s.getId())).map(AddressBalance::getBoxCount).orElse(0);
    }

    /** Unspent boxes of the address, newest first, with assets and creating tx; total from address_balance. */
    public PageDto<BoxDto> unspentBoxes(String address, int page, int rowsPerPage) {
        Optional<Script> s = script(address);
        if (s.isEmpty()) {
            return new PageDto<>(List.of(), 0);
        }
        List<Box> list = boxes.findUnspent(s.get().getId(), page, rowsPerPage);
        Map<Long, String> txHash = txs.findByGixes(list.stream().map(Box::getTxGix).distinct().toList()).stream()
                .collect(Collectors.toMap(Tx::getGix, t -> hex(t.getId())));
        Map<Long, List<BoxDto>> byGix = new LinkedHashMap<>();
        List<BoxDto> items = new ArrayList<>();
        for (Box m : list) {
            BoxDto d = toBox(m, s.get(), txHash.get(m.getTxGix()));
            d.setSpent(false);
            items.add(d);
            byGix.put(m.getGix(), List.of(d));
        }
        attachAssets(byGix);
        return new PageDto<>(items, unspentBoxCount(address));
    }

    /** From token_holder, with names from the token table. */
    public List<AssetDto> tokenBalances(String address) {
        Optional<Script> s = script(address);
        if (s.isEmpty()) {
            return List.of();
        }
        List<TokenHolder> held = holders.findByScript(s.get().getId());
        Map<String, Token> meta = tokens.findByHashes(held.stream().map(h -> hex(h.getId().getTokenId())).toList())
                .stream().collect(Collectors.toMap(t -> hex(t.getId()), Function.identity()));
        return held.stream().map(h -> toAsset(hex(h.getId().getTokenId()), h.getAmount(), meta.get(hex(h.getId().getTokenId())))).toList();
    }

    /** Richest addresses; items carry rank and balance. */
    public PageDto<TokenDto.HolderDto> richList(int page, int rowsPerPage) {
        List<AddressBalance> list = balances.richList(page, rowsPerPage);
        Map<Long, Script> byId = scriptsOf(list.stream().map(AddressBalance::getScriptId).toList());
        List<TokenDto.HolderDto> items = new ArrayList<>();
        int rank = (page - 1) * rowsPerPage;
        for (AddressBalance b : list) {
            TokenDto.HolderDto h = new TokenDto.HolderDto();
            h.setRank(++rank);
            h.setAddress(byId.get(b.getScriptId()).getAddress());
            h.setAmount(b.getNanoErg());
            h.setBoxCount(b.getBoxCount());
            items.add(h);
        }
        return new PageDto<>(items, balances.countFunded());
    }

    public long txCount(String address) {
        return script(address).flatMap(s -> balances.findByScript(s.getId())).map(AddressBalance::getTxCount).orElse(0L);
    }

    /** Transactions touching the address, newest first. */
    public List<TxDto> addressTxs(String address, int page, int rowsPerPage) {
        return script(address).map(s -> assemble(txs.findByScript(s.getId(), page, rowsPerPage))).orElse(List.of());
    }

    /** Timestamps of the first and last transaction of the address: [first, last], nulls when none. */
    public Long[] firstLastSeen(String address) {
        Optional<AddressBalance> b = script(address).flatMap(s -> balances.findByScript(s.getId()));
        if (b.isEmpty() || b.get().getTxCount() == 0) {
            return new Long[]{null, null};
        }
        Map<Long, Long> ts = txs.findByGixes(List.of(b.get().getFirstTxGix(), b.get().getLastTxGix())).stream()
                .collect(Collectors.toMap(Tx::getGix, Tx::getTimestamp));
        return new Long[]{ts.get(b.get().getFirstTxGix()), ts.get(b.get().getLastTxGix())};
    }

    public String ergoTree(String address) {
        return script(address).map(Script::getErgoTree).orElse(null);
    }

    // ── tokens ───────────────────────────────────────────────

    public Optional<TokenDto> token(String id) {
        return tokens.findByHash(id).map(this::toToken);
    }

    private TokenDto toToken(Token m) {
        TokenDto t = new TokenDto();
        t.setId(hex(m.getId()));
        t.setName(m.getName());
        t.setDesc(m.getDescription());
        t.setDecimals(m.getDecimals());
        t.setSupply(m.getEmissionAmount());
        t.setIssueHeight(m.getBlockHeight());
        boxes.findByGixes(List.of(m.getBoxGix())).stream().findFirst().ifPresent(b -> t.setIssueBox(hex(b.getId())));
        txs.findByGixes(List.of(m.getTxGix())).stream().findFirst().ifPresent(x -> t.setIssueTx(hex(x.getId())));
        return t;
    }

    public int holderCount(String tokenId) {
        return holders.holderCount(tokenId);
    }

    public List<TokenDto.HolderDto> topHolders(String tokenId, int limit) {
        return holdersPage(tokenId, 1, limit).getItems();
    }

    /** Rich list of a token: holders by amount, with rank. */
    public PageDto<TokenDto.HolderDto> holdersPage(String tokenId, int page, int rowsPerPage) {
        List<TokenHolder> list = holders.holders(tokenId, page, rowsPerPage);
        Map<Long, Script> byId = scriptsOf(list.stream().map(h -> h.getId().getScriptId()).toList());
        List<TokenDto.HolderDto> items = new ArrayList<>();
        int rank = (page - 1) * rowsPerPage;
        for (TokenHolder t : list) {
            TokenDto.HolderDto h = new TokenDto.HolderDto();
            h.setRank(++rank);
            h.setAddress(byId.get(t.getId().getScriptId()).getAddress());
            h.setAmount(t.getAmount());
            items.add(h);
        }
        return new PageDto<>(items, holders.holderCount(tokenId));
    }

    public List<TokenDto.TransferDto> recentTransfers(String tokenId, int limit) {
        return tokens.recentTransfers(tokenId, limit).stream().map(r -> {
            TokenDto.TransferDto x = new TokenDto.TransferDto();
            x.setId(r.getString("tx_id").toLowerCase());
            x.setHeight(r.getLong("block_height"));
            x.setTimestamp(r.getLong("timestamp"));
            x.setTo(r.getString("address"));
            x.setAmount(r.getLong("amount"));
            return x;
        }).toList();
    }

    /** Tokens whose name matches (search box). */
    public List<TokenDto> tokensByName(String name, int limit) {
        return tokens.findByName(name, limit).stream().map(this::toToken).toList();
    }

}
