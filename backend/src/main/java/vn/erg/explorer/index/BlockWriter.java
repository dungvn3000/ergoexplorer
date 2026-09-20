package vn.erg.explorer.index;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import vn.erg.explorer.dtos.RegisterDto;
import vn.erg.explorer.utils.ErgoAddress;
import vn.erg.explorer.utils.ErgoConstants;
import vn.erg.explorer.utils.Hex;
import vn.erg.explorer.utils.Registers;
import vn.erg.explorer.utils.Sha256;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

/**
 * Writes full blocks into MySQL with JDBC batches: one transaction per batch of blocks, so a crash
 * leaves the index at a block boundary. Assigns the sequential keys (tx.gix, box.gix, script.id),
 * dedupes ErgoTrees into {@code script}, records spends in {@code box_spent} and keeps the
 * aggregates (balances, holders, daily rollups) in the same transaction.
 * {@link #rollback(long)} removes one block again (reorg), reversing all of that.
 */
@Slf4j
@Singleton
public class BlockWriter {

    // ── SQL ─────────────────────────────────────────────────────────────────────────────────
    private static final String INSERT_SCRIPT = "INSERT IGNORE INTO script (id, tree_hash, addr_hash, ergo_tree, address, p2pk) VALUES (?,?,?,?,?,?)";
    private static final String INSERT_BLOCK = """
            INSERT INTO block (height, id, parent_id, timestamp, tx_count, size, miner_pk, miner_script_id, difficulty, n_bits,
              version, votes, emission, reemitted, fees, state_root, transactions_root, ad_proofs_root, extension_hash, pow_w, pow_n, pow_d)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""";
    private static final String INSERT_TX = "INSERT INTO tx (gix, id, block_height, idx, coinbase, timestamp, size, fee) VALUES (?,?,?,?,?,?,?,?)";
    private static final String INSERT_BOX = """
            INSERT INTO box (gix, id, tx_gix, idx, block_height, value, script_id, creation_height, registers)
            VALUES (?,?,?,?,?,?,?,?,?)""";
    private static final String INSERT_ASSET = "INSERT INTO box_asset (box_gix, idx, token_id, amount) VALUES (?,?,?,?)";
    private static final String INSERT_INPUT = "INSERT INTO tx_input (tx_gix, data_input, idx, box_gix) VALUES (?,?,?,?)";
    private static final String SPEND_BOX = "INSERT INTO box_spent (box_gix, tx_gix, height) VALUES (?,?,?)";
    private static final String INSERT_TOKEN = """
            INSERT IGNORE INTO token (id, box_gix, tx_gix, block_height, name, description, decimals, emission_amount)
            VALUES (?,?,?,?,?,?,?,?)""";
    private static final String INIT_STATE = "INSERT IGNORE INTO indexer_state (id, height, block_id, updated) VALUES (1, 0, UNHEX(REPEAT('0', 64)), 0)";
    /** Optimistic lock: only advances from the height this process believes is current. */
    private static final String SAVE_STATE = "UPDATE indexer_state SET height = ?, block_id = ?, updated = ? WHERE id = 1 AND height = ?";
    private static final String UPSERT_BALANCE = """
            INSERT INTO address_balance (script_id, nano_erg, box_count, tx_count, first_tx_gix, last_tx_gix) VALUES (?,?,?,?,?,?)
            ON DUPLICATE KEY UPDATE nano_erg = nano_erg + VALUES(nano_erg), box_count = box_count + VALUES(box_count),
              tx_count = tx_count + VALUES(tx_count), first_tx_gix = IF(first_tx_gix = 0, VALUES(first_tx_gix), LEAST(first_tx_gix, VALUES(first_tx_gix))),
              last_tx_gix = GREATEST(last_tx_gix, VALUES(last_tx_gix))""";
    private static final String INSERT_ADDRESS_TX = "INSERT IGNORE INTO address_tx (script_id, tx_gix) VALUES (?,?)";
    private static final String UPSERT_HOLDER = """
            INSERT INTO token_holder (token_id, script_id, amount) VALUES (?,?,?)
            ON DUPLICATE KEY UPDATE amount = amount + VALUES(amount)""";
    private static final String PRUNE_HOLDER = "DELETE FROM token_holder WHERE token_id = ? AND script_id = ? AND amount <= 0";
    private static final String UPSERT_DAY = """
            INSERT INTO daily_stats (day, blocks, txs, fees, size, difficulty_sum, emission, reemitted, boxes_created, boxes_spent, token_transfers, tokens_minted)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
            ON DUPLICATE KEY UPDATE blocks = blocks + VALUES(blocks), txs = txs + VALUES(txs), fees = fees + VALUES(fees), size = size + VALUES(size),
              difficulty_sum = difficulty_sum + VALUES(difficulty_sum), emission = emission + VALUES(emission), reemitted = reemitted + VALUES(reemitted),
              boxes_created = boxes_created + VALUES(boxes_created), boxes_spent = boxes_spent + VALUES(boxes_spent),
              token_transfers = token_transfers + VALUES(token_transfers), tokens_minted = tokens_minted + VALUES(tokens_minted)""";
    private static final String INSERT_ACTIVE = "INSERT IGNORE INTO daily_address (day, script_id) VALUES (?,?)";

    /** UTC day of the Ergo genesis (2019-07-01), for the genesis boxes' daily rollup. */
    public static final int GENESIS_DAY = 18078;
    /** tx_gix of the genesis boxes (they are not created by any transaction; tx row 0 does not exist). */
    public static final long GENESIS_TX_GIX = 0;

    /** Another process advanced the index in the meantime (two indexers on one DB). */
    public static class ConflictException extends RuntimeException {
        public ConflictException(String message) {
            super(message);
        }
    }

    // ── batch-local structures ──────────────────────────────────────────────────────────────
    /** What the aggregates need to know about a box: where it sits and what it holds. */
    private record BoxRef(long gix, long scriptId, long value, Map<String, Long> assets) {
    }

    /** A reference from a transaction to a box: spent input (data = false) or data input; resolved after the boxes are known. */
    private record Ref(String boxId, long txGix, int idx, boolean data, long height, int day) {
    }

    /** Net change of the aggregate tables produced by one UTC day of a batch (or genesis). */
    private static final class Deltas {
        final Map<Long, long[]> balances = new HashMap<>();          // script -> [nanoErg, boxes]
        final Map<Long, Set<Long>> txs = new HashMap<>();             // script -> gixes of the txs touching it (address_tx)
        final Map<String, long[]> holders = new HashMap<>();         // tokenId|script -> [amount]
        final Map<Integer, long[]> days = new TreeMap<>();            // day -> stats (UPSERT_DAY column order minus day)
        /** difficulty_sum per day, kept apart: 720 blocks × ~4e16 (post-merge difficulty) overflow a long. */
        final Map<Integer, BigInteger> difficulty = new HashMap<>();
        final Set<String> active = new HashSet<>();                  // day|script

        long[] day(int day) {
            return days.computeIfAbsent(day, d -> new long[11]);
        }

        void difficulty(int day, BigInteger d) {
            difficulty.merge(day, d, BigInteger::add);
        }

        /** An output created (sign +1) or spent (sign -1) by tx {@code txGix} at {@code day}. */
        void box(long scriptId, long value, Map<String, Long> assets, int sign, int day, long txGix) {
            active.add(day + "|" + scriptId);
            if (txGix > GENESIS_TX_GIX) {
                txs.computeIfAbsent(scriptId, k -> new HashSet<>()).add(txGix);
            }
            long[] st = day(day);
            if (sign > 0) {
                st[7]++;
                st[9] += assets.size();
            } else {
                st[8]++;
            }
            long[] b = balances.computeIfAbsent(scriptId, k -> new long[2]);
            b[0] += sign * value;
            b[1] += sign;
            for (Map.Entry<String, Long> e : assets.entrySet()) {
                holders.computeIfAbsent(e.getKey() + "|" + scriptId, k -> new long[1])[0] += sign * e.getValue();
            }
        }

        void apply(Connection c) throws SQLException {
            try (PreparedStatement psBal = c.prepareStatement(UPSERT_BALANCE);
                 PreparedStatement psAddrTx = c.prepareStatement(INSERT_ADDRESS_TX);
                 PreparedStatement psHold = c.prepareStatement(UPSERT_HOLDER);
                 PreparedStatement psPruneHold = c.prepareStatement(PRUNE_HOLDER);
                 PreparedStatement psDay = c.prepareStatement(UPSERT_DAY);
                 PreparedStatement psActive = c.prepareStatement(INSERT_ACTIVE)) {
                // Every script touched has at least one tx (genesis excepted); a tx lives in one day, so the
                // (script, tx) pairs of this Deltas are new and tx_count can be advanced by their number.
                Set<Long> scripts = new HashSet<>(balances.keySet());
                scripts.addAll(txs.keySet());
                for (long scriptId : scripts) {
                    long[] v = balances.getOrDefault(scriptId, new long[2]);
                    Set<Long> gixes = txs.getOrDefault(scriptId, Set.of());
                    if (v[0] == 0 && v[1] == 0 && gixes.isEmpty()) {
                        continue;
                    }
                    psBal.setLong(1, scriptId);
                    psBal.setLong(2, v[0]);
                    psBal.setLong(3, v[1]);
                    psBal.setLong(4, gixes.size());
                    psBal.setLong(5, gixes.isEmpty() ? 0 : Collections.min(gixes));
                    psBal.setLong(6, gixes.isEmpty() ? 0 : Collections.max(gixes));
                    psBal.addBatch();
                    for (long gix : gixes) {
                        psAddrTx.setLong(1, scriptId);
                        psAddrTx.setLong(2, gix);
                        psAddrTx.addBatch();
                    }
                }
                for (Map.Entry<String, long[]> e : holders.entrySet()) {
                    long amount = e.getValue()[0];
                    if (amount == 0) {
                        continue;
                    }
                    int sep = e.getKey().indexOf('|');
                    byte[] tokenId = Hex.decode(e.getKey().substring(0, sep));
                    long scriptId = Long.parseLong(e.getKey().substring(sep + 1));
                    psHold.setBytes(1, tokenId);
                    psHold.setLong(2, scriptId);
                    psHold.setLong(3, amount);
                    psHold.addBatch();
                    if (amount < 0) {
                        psPruneHold.setBytes(1, tokenId);
                        psPruneHold.setLong(2, scriptId);
                        psPruneHold.addBatch();
                    }
                }
                for (Map.Entry<Integer, long[]> e : days.entrySet()) {
                    psDay.setInt(1, e.getKey());
                    long[] v = e.getValue();
                    for (int i = 0; i < v.length; i++) {
                        if (i == 4) {
                            psDay.setBigDecimal(i + 2, new BigDecimal(difficulty.getOrDefault(e.getKey(), BigInteger.ZERO)));
                        } else {
                            psDay.setLong(i + 2, v[i]);
                        }
                    }
                    psDay.addBatch();
                }
                for (String key : active) {
                    int sep = key.indexOf('|');
                    psActive.setInt(1, Integer.parseInt(key.substring(0, sep)));
                    psActive.setLong(2, Long.parseLong(key.substring(sep + 1)));
                    psActive.addBatch();
                }
                psBal.executeBatch();
                psAddrTx.executeBatch();
                psHold.executeBatch();
                psPruneHold.executeBatch();  // token rows that went to zero (token fully moved)
                psDay.executeBatch();
                psActive.executeBatch();
            }
        }
    }

    // ── state ───────────────────────────────────────────────────────────────────────────────
    private final DataSource ds;
    /** ErgoTree hex -> script id; contracts repeat constantly, so this hits almost always. */
    private final Cache<String, Long> scripts = Caffeine.newBuilder().maximumSize(500_000).build();
    private long nextBoxGix = -1;
    private long nextTxGix = -1;
    private long nextScriptId = -1;
    /** UTC day of the last indexed block (snapshots are taken when a later day starts); -1 = unknown. */
    private int lastDay = -1;

    @Inject
    public BlockWriter(DataSource ds) {
        this.ds = ds;
    }

    public static int dayOf(long timestampMillis) {
        return (int) (timestampMillis / 86_400_000L);
    }

    /** Last indexed height (0 when the index is empty); creates the state row on first use. */
    public long state() {
        try (Connection c = ds.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(INIT_STATE)) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT height FROM indexer_state WHERE id = 1");
                 ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("indexer_state", e);
        }
    }

    public String blockIdAt(long height) {
        try (Connection c = ds.getConnection()) {
            return blockIdAtIn(c, height);
        } catch (SQLException e) {
            throw new IllegalStateException("block lookup", e);
        }
    }

    private void loadCounters(Connection c) throws SQLException {
        if (nextBoxGix < 0) {
            nextBoxGix = maxOf(c, "SELECT COALESCE(MAX(gix), 0) FROM box") + 1;
        }
        if (nextTxGix < 0) {
            nextTxGix = maxOf(c, "SELECT COALESCE(MAX(gix), 0) FROM tx") + 1;
        }
        if (nextScriptId < 0) {
            nextScriptId = maxOf(c, "SELECT COALESCE(MAX(id), 0) FROM script") + 1;
        }
    }

    private void resetCounters() {
        nextBoxGix = -1;
        nextTxGix = -1;
        nextScriptId = -1;
        lastDay = -1;
    }

    private static long maxOf(Connection c, String sql) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    // ── scripts ─────────────────────────────────────────────────────────────────────────────

    /**
     * Script ids for the given ErgoTrees: cache first; unknown trees are inserted (IGNORE, so a tree
     * already in the table keeps its id) and then read back by tree hash.
     */
    private Map<String, Long> resolveScripts(Connection c, Set<String> trees) throws SQLException {
        Map<String, Long> out = new HashMap<>();
        List<String> missing = new ArrayList<>();
        for (String tree : trees) {
            Long id = scripts.getIfPresent(tree);
            if (id != null) {
                out.put(tree, id);
            } else {
                missing.add(tree);
            }
        }
        if (missing.isEmpty()) {
            return out;
        }
        Map<String, byte[]> hashOf = new HashMap<>();
        try (PreparedStatement ps = c.prepareStatement(INSERT_SCRIPT)) {
            for (String tree : missing) {
                byte[] treeHash = Sha256.of(Hex.decode(tree));
                hashOf.put(tree, treeHash);
                String address = ErgoAddress.fromErgoTree(tree);
                ps.setLong(1, nextScriptId++);
                ps.setBytes(2, treeHash);
                ps.setBytes(3, Sha256.of(address));
                ps.setString(4, tree);
                ps.setString(5, address);
                ps.setInt(6, ErgoAddress.isP2PK(address) ? 1 : 0);
                ps.addBatch();
            }
            ps.executeBatch();
        }
        Map<String, String> treeByHashHex = new HashMap<>();
        for (Map.Entry<String, byte[]> e : hashOf.entrySet()) {
            treeByHashHex.put(Hex.encode(e.getValue()), e.getKey());
        }
        List<byte[]> hashes = new ArrayList<>(hashOf.values());
        for (int i = 0; i < hashes.size(); i += 1000) {
            List<byte[]> chunk = hashes.subList(i, Math.min(hashes.size(), i + 1000));
            try (PreparedStatement ps = c.prepareStatement("SELECT id, tree_hash FROM script WHERE tree_hash IN (" + placeholders(chunk.size()) + ")")) {
                for (int j = 0; j < chunk.size(); j++) {
                    ps.setBytes(j + 1, chunk.get(j));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String tree = treeByHashHex.get(Hex.encode(rs.getBytes(2)));
                        out.put(tree, rs.getLong(1));
                        scripts.put(tree, rs.getLong(1));
                    }
                }
            }
        }
        return out;
    }

    private static String placeholders(int n) {
        return "?,".repeat(n - 1) + "?";
    }

    // ── write ───────────────────────────────────────────────────────────────────────────────

    /**
     * Writes the blocks (ascending, contiguous) in one transaction. {@code expectedHeight} is the
     * indexed height this process last saw; if the DB moved on, nothing is written and a
     * {@link ConflictException} is thrown.
     */
    public void write(List<IndexedBlock> blocks, long expectedHeight) {
        if (blocks.isEmpty()) {
            return;
        }
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                loadCounters(c);
                // 1. scripts of every output and every miner in the batch
                Set<String> trees = new HashSet<>();
                for (IndexedBlock b : blocks) {
                    trees.add("0008cd" + b.json().path("header").path("powSolutions").path("pk").asText());
                    for (JsonNode tx : b.json().path("blockTransactions").path("transactions")) {
                        for (JsonNode out : tx.path("outputs")) {
                            trees.add(out.path("ergoTree").asText());
                        }
                    }
                }
                Map<String, Long> scriptIds = resolveScripts(c, trees);

                // 2. days: one Deltas per UTC day so end-of-day snapshots can be taken between days of the batch
                int firstDay = dayOf(blocks.get(0).json().path("header").path("timestamp").asLong());
                if (lastDay < 0) {
                    lastDay = lastIndexedDay(c, firstDay);
                }
                snapshotDays(c, lastDay, firstDay - 1);   // days that ended before this batch (incl. empty days)

                // 3. rows
                Map<String, BoxRef> batchBoxes = new HashMap<>();
                List<Ref> refs = new ArrayList<>();
                Map<Integer, Deltas> deltasByDay = new TreeMap<>();
                try (PreparedStatement psBlock = c.prepareStatement(INSERT_BLOCK);
                     PreparedStatement psTx = c.prepareStatement(INSERT_TX);
                     PreparedStatement psBox = c.prepareStatement(INSERT_BOX);
                     PreparedStatement psAsset = c.prepareStatement(INSERT_ASSET);
                     PreparedStatement psToken = c.prepareStatement(INSERT_TOKEN)) {
                    for (IndexedBlock b : blocks) {
                        int day = dayOf(b.json().path("header").path("timestamp").asLong());
                        Deltas deltas = deltasByDay.computeIfAbsent(day, d -> new Deltas());
                        writeBlock(b, psBlock, psTx, psBox, psAsset, psToken, scriptIds, batchBoxes, refs, deltas);
                    }
                    psBlock.executeBatch();
                    psTx.executeBatch();
                    psBox.executeBatch();
                    psAsset.executeBatch();
                    psToken.executeBatch();
                }

                // 4. inputs: box gix (and, for spends, script/value/assets) from this batch or the DB
                Map<String, BoxRef> boxes = resolveBoxes(c, refs, batchBoxes);
                try (PreparedStatement psInput = c.prepareStatement(INSERT_INPUT);
                     PreparedStatement psSpend = c.prepareStatement(SPEND_BOX)) {
                    for (Ref r : refs) {
                        BoxRef box = boxes.get(r.boxId());
                        if (box == null) {
                            log.warn("Input {} of tx gix {} at height {} is not in the index (indexed range starts later?)", r.boxId(), r.txGix(), r.height());
                            continue;
                        }
                        psInput.setLong(1, r.txGix());
                        psInput.setInt(2, r.data() ? 1 : 0);
                        psInput.setInt(3, r.idx());
                        psInput.setLong(4, box.gix());
                        psInput.addBatch();
                        if (!r.data()) {
                            psSpend.setLong(1, box.gix());
                            psSpend.setLong(2, r.txGix());
                            psSpend.setLong(3, r.height());
                            psSpend.addBatch();
                            deltasByDay.get(r.day()).box(box.scriptId(), box.value(), box.assets(), -1, r.day(), r.txGix());
                        }
                    }
                    psInput.executeBatch();
                    psSpend.executeBatch();
                }

                // 5. aggregates day by day, snapshot each completed day
                List<Integer> days = new ArrayList<>(deltasByDay.keySet());
                for (int i = 0; i < days.size(); i++) {
                    deltasByDay.get(days.get(i)).apply(c);
                    if (i < days.size() - 1) {
                        snapshotDays(c, days.get(i), days.get(i + 1) - 1);
                    }
                }
                lastDay = days.get(days.size() - 1);

                // 6. state
                IndexedBlock last = blocks.get(blocks.size() - 1);
                try (PreparedStatement psState = c.prepareStatement(SAVE_STATE)) {
                    psState.setLong(1, last.height());
                    psState.setBytes(2, Hex.decode(last.id()));
                    psState.setLong(3, System.currentTimeMillis());
                    psState.setLong(4, expectedHeight);
                    if (psState.executeUpdate() != 1) {
                        throw new ConflictException("indexer_state is no longer at " + expectedHeight + " — another indexer is writing to this database");
                    }
                }
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                resetCounters(); // re-read from the DB next time (another writer may have used our key ranges)
                throw e;
            }
        } catch (SQLException e) {
            if (e instanceof java.sql.SQLIntegrityConstraintViolationException) {
                throw new ConflictException("Duplicate rows while writing " + blocks.get(0).height() + ".." + blocks.get(blocks.size() - 1).height()
                        + " — another indexer is writing to this database (" + e.getMessage() + ")");
            }
            throw new IllegalStateException("Writing blocks " + blocks.get(0).height() + ".." + blocks.get(blocks.size() - 1).height(), e);
        }
    }

    private void writeBlock(IndexedBlock b, PreparedStatement psBlock, PreparedStatement psTx, PreparedStatement psBox,
                            PreparedStatement psAsset, PreparedStatement psToken, Map<String, Long> scriptIds,
                            Map<String, BoxRef> batchBoxes, List<Ref> refs, Deltas deltas) throws SQLException {
        JsonNode h = b.json().path("header");
        JsonNode txs = b.json().path("blockTransactions").path("transactions");
        long height = b.height();
        long timestamp = h.path("timestamp").asLong();
        int day = dayOf(timestamp);
        long[] dayStats = deltas.day(day);

        long emission = 0, reemitted = 0, fees = 0;
        String minerTree = null;
        int coinbaseIdx = coinbaseIndex(txs);
        if (coinbaseIdx >= 0) {
            JsonNode minerBox = txs.get(coinbaseIdx).path("outputs").get(1);
            emission = minerBox.path("value").asLong();
            minerTree = minerBox.path("ergoTree").asText();
            for (JsonNode a : minerBox.path("assets")) {
                if (ErgoConstants.REEMISSION_TOKEN.equals(a.path("tokenId").asText())) {
                    reemitted = a.path("amount").asLong();
                }
            }
        }
        int ti = 0;
        for (JsonNode tx : txs) {
            String txId = tx.path("id").asText();
            long txGix = nextTxGix++;
            long fee = 0;
            String firstInput = tx.path("inputs").isEmpty() ? null : tx.path("inputs").get(0).path("boxId").asText();
            int oi = 0;
            for (JsonNode out : tx.path("outputs")) {
                String tree = out.path("ergoTree").asText();
                if (ErgoConstants.FEE_TREE.equals(tree)) {
                    fee += out.path("value").asLong();
                }
                String boxId = out.path("boxId").asText();
                long gix = nextBoxGix++;
                long scriptId = scriptIds.get(tree);
                long value = out.path("value").asLong();
                Map<String, Long> assetMap = assetsOf(out);
                batchBoxes.put(boxId, new BoxRef(gix, scriptId, value, assetMap));
                deltas.box(scriptId, value, assetMap, +1, day, txGix);
                psBox.setLong(1, gix);
                psBox.setBytes(2, Hex.decode(boxId));
                psBox.setLong(3, txGix);
                psBox.setInt(4, oi);
                psBox.setLong(5, height);
                psBox.setLong(6, value);
                psBox.setLong(7, scriptId);
                psBox.setLong(8, out.path("creationHeight").asLong());
                JsonNode regs = out.path("additionalRegisters");
                psBox.setString(9, regs.isEmpty() ? null : regs.toString());
                psBox.addBatch();
                int ai = 0;
                for (JsonNode a : out.path("assets")) {
                    String tokenId = a.path("tokenId").asText();
                    psAsset.setLong(1, gix);
                    psAsset.setInt(2, ai++);
                    psAsset.setBytes(3, Hex.decode(tokenId));
                    psAsset.setLong(4, a.path("amount").asLong());
                    psAsset.addBatch();
                    if (tokenId.equals(firstInput)) {
                        addToken(psToken, tokenId, gix, txGix, height, regs, a.path("amount").asLong());
                        dayStats[10]++;
                    }
                }
                oi++;
            }
            int ii = 0;
            for (JsonNode in : tx.path("inputs")) {
                refs.add(new Ref(in.path("boxId").asText(), txGix, ii++, false, height, day));
            }
            ii = 0;
            for (JsonNode in : tx.path("dataInputs")) {
                refs.add(new Ref(in.path("boxId").asText(), txGix, ii++, true, height, day));
            }
            psTx.setLong(1, txGix);
            psTx.setBytes(2, Hex.decode(txId));
            psTx.setLong(3, height);
            psTx.setInt(4, ti);
            psTx.setInt(5, ti == coinbaseIdx ? 1 : 0);
            psTx.setLong(6, timestamp);
            psTx.setInt(7, tx.path("size").asInt());
            psTx.setLong(8, fee);
            psTx.addBatch();
            // Fees are collected by the last transaction into one box paying the miner's tree
            if (ti == txs.size() - 1 && ti > 0 && minerTree != null) {
                JsonNode outs = tx.path("outputs");
                if (outs.size() == 1 && minerTree.equals(outs.get(0).path("ergoTree").asText())) {
                    fees = outs.get(0).path("value").asLong();
                }
            }
            ti++;
        }

        JsonNode pow = h.path("powSolutions");
        String pk = pow.path("pk").asText();
        int i = 1;
        psBlock.setLong(i++, height);
        psBlock.setBytes(i++, Hex.decode(b.id()));
        psBlock.setBytes(i++, Hex.decode(h.path("parentId").asText()));
        psBlock.setLong(i++, timestamp);
        psBlock.setInt(i++, txs.size());
        psBlock.setInt(i++, b.json().path("size").asInt());
        psBlock.setBytes(i++, Hex.decode(pk));
        psBlock.setLong(i++, scriptIds.get("0008cd" + pk));
        psBlock.setLong(i++, Long.parseLong(h.path("difficulty").asText("0")));
        psBlock.setLong(i++, h.path("nBits").asLong());
        psBlock.setInt(i++, h.path("version").asInt());
        psBlock.setBytes(i++, Hex.decode(h.path("votes").asText("000000")));
        psBlock.setLong(i++, emission);
        psBlock.setLong(i++, reemitted);
        psBlock.setLong(i++, fees);
        psBlock.setBytes(i++, Hex.decode(h.path("stateRoot").asText()));
        psBlock.setBytes(i++, Hex.decode(h.path("transactionsRoot").asText()));
        psBlock.setBytes(i++, Hex.decode(h.path("adProofsRoot").asText()));
        psBlock.setBytes(i++, Hex.decode(h.path("extensionHash").asText()));
        psBlock.setBytes(i++, Hex.decode(pow.path("w").asText()));
        psBlock.setBytes(i++, Hex.decode(pow.path("n").asText()));
        psBlock.setString(i, pow.path("d").asText("0"));
        psBlock.addBatch();
        dayStats[0]++;
        dayStats[1] += txs.size();
        dayStats[2] += fees;
        dayStats[3] += b.json().path("size").asLong();
        deltas.difficulty(day, new BigInteger(h.path("difficulty").asText("0")));
        dayStats[5] += emission;
        dayStats[6] += reemitted;
    }

    /**
     * Index of the emission (coinbase) transaction: output 0 re-creates the emission box (its
     * script never changed). Usually 0, but early blocks placed it just before the fee tx.
     */
    public static int coinbaseIndex(JsonNode txs) {
        for (int i = 0; i < txs.size(); i++) {
            JsonNode outs = txs.get(i).path("outputs");
            if (outs.size() >= 2 && ErgoConstants.EMISSION_TREE.equals(outs.get(0).path("ergoTree").asText())) {
                return i;
            }
        }
        return -1;
    }

    private static Map<String, Long> assetsOf(JsonNode box) {
        Map<String, Long> assets = new LinkedHashMap<>();
        for (JsonNode a : box.path("assets")) {
            assets.merge(a.path("tokenId").asText(), a.path("amount").asLong(), Long::sum);
        }
        return assets;
    }

    /** Every referenced box: from the batch map when created in this batch, otherwise from the DB by hash (chunked IN queries). */
    private Map<String, BoxRef> resolveBoxes(Connection c, List<Ref> refs, Map<String, BoxRef> batchBoxes) throws SQLException {
        Map<String, BoxRef> out = new HashMap<>(batchBoxes);
        List<String> missing = refs.stream().map(Ref::boxId).filter(id -> !out.containsKey(id)).distinct().toList();
        Map<Long, String> idByGix = new HashMap<>();
        for (int i = 0; i < missing.size(); i += 1000) {
            List<String> chunk = missing.subList(i, Math.min(missing.size(), i + 1000));
            try (PreparedStatement ps = c.prepareStatement("SELECT id, gix, script_id, value FROM box WHERE id IN (" + placeholders(chunk.size()) + ")")) {
                for (int j = 0; j < chunk.size(); j++) {
                    ps.setBytes(j + 1, Hex.decode(chunk.get(j)));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String id = Hex.encode(rs.getBytes(1));
                        out.put(id, new BoxRef(rs.getLong(2), rs.getLong(3), rs.getLong(4), new LinkedHashMap<>()));
                        idByGix.put(rs.getLong(2), id);
                    }
                }
            }
        }
        List<Long> gixes = new ArrayList<>(idByGix.keySet());
        for (int i = 0; i < gixes.size(); i += 1000) {
            List<Long> chunk = gixes.subList(i, Math.min(gixes.size(), i + 1000));
            try (PreparedStatement ps = c.prepareStatement("SELECT box_gix, token_id, amount FROM box_asset WHERE box_gix IN (" + placeholders(chunk.size()) + ")")) {
                for (int j = 0; j < chunk.size(); j++) {
                    ps.setLong(j + 1, chunk.get(j));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.get(idByGix.get(rs.getLong(1))).assets().merge(Hex.encode(rs.getBytes(2)), rs.getLong(3), Long::sum);
                    }
                }
            }
        }
        return out;
    }

    /** EIP-4: R4 = name, R5 = description, R6 = decimals (all Coll[Byte] UTF-8). */
    private void addToken(PreparedStatement ps, String tokenId, long boxGix, long txGix, long height, JsonNode regs, long amount)
            throws SQLException {
        ps.setBytes(1, Hex.decode(tokenId));
        ps.setLong(2, boxGix);
        ps.setLong(3, txGix);
        ps.setLong(4, height);
        ps.setString(5, truncate(text(regs, "R4"), 512));
        ps.setString(6, truncate(text(regs, "R5"), 8000));
        int decimals = 0;
        try {
            decimals = Integer.parseInt(text(regs, "R6").trim());
        } catch (NumberFormatException ignored) {
            // no decimals register or not numeric: 0
        }
        ps.setInt(7, decimals);
        ps.setLong(8, amount);
        ps.addBatch();
    }

    private static String text(JsonNode regs, String key) {
        if (!regs.hasNonNull(key)) {
            return "";
        }
        RegisterDto r = Registers.decode(key, regs.path(key).asText());
        String v = r.getValue() == null ? "" : r.getValue();
        return v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"") ? v.substring(1, v.length() - 1) : v;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    // ── genesis ─────────────────────────────────────────────────────────────────────────────

    /** Genesis boxes (node /utxo/genesis) as outputs of pseudo tx 0 at height 0; no-op if already there. */
    public void writeGenesis(JsonNode boxes) {
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                if (maxOf(c, "SELECT COUNT(*) FROM box WHERE tx_gix = " + GENESIS_TX_GIX) > 0) {
                    return;
                }
                loadCounters(c);
                Set<String> trees = new HashSet<>();
                boxes.forEach(b -> trees.add(b.path("ergoTree").asText()));
                Map<String, Long> scriptIds = resolveScripts(c, trees);
                Deltas deltas = new Deltas();
                try (PreparedStatement psBox = c.prepareStatement(INSERT_BOX);
                     PreparedStatement psAsset = c.prepareStatement(INSERT_ASSET)) {
                    int oi = 0;
                    for (JsonNode out : boxes) {
                        long gix = nextBoxGix++;
                        long scriptId = scriptIds.get(out.path("ergoTree").asText());
                        deltas.box(scriptId, out.path("value").asLong(), assetsOf(out), +1, GENESIS_DAY, GENESIS_TX_GIX);
                        psBox.setLong(1, gix);
                        psBox.setBytes(2, Hex.decode(out.path("boxId").asText()));
                        psBox.setLong(3, GENESIS_TX_GIX);
                        psBox.setInt(4, oi++);
                        psBox.setLong(5, 0);
                        psBox.setLong(6, out.path("value").asLong());
                        psBox.setLong(7, scriptId);
                        psBox.setLong(8, 0);
                        JsonNode regs = out.path("additionalRegisters");
                        psBox.setString(9, regs.isEmpty() ? null : regs.toString());
                        psBox.addBatch();
                        int ai = 0;
                        for (JsonNode a : out.path("assets")) {
                            psAsset.setLong(1, gix);
                            psAsset.setInt(2, ai++);
                            psAsset.setBytes(3, Hex.decode(a.path("tokenId").asText()));
                            psAsset.setLong(4, a.path("amount").asLong());
                            psAsset.addBatch();
                        }
                    }
                    psBox.executeBatch();
                    psAsset.executeBatch();
                }
                deltas.apply(c);
                c.commit();
                log.info("Indexed {} genesis boxes", boxes.size());
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                resetCounters();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Writing genesis boxes", e);
        }
    }

    // ── snapshots ───────────────────────────────────────────────────────────────────────────

    /** Day of the highest indexed block, or {@code fallback} when the index is empty. */
    private static int lastIndexedDay(Connection c, int fallback) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT timestamp FROM block ORDER BY height DESC LIMIT 1");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? dayOf(rs.getLong(1)) : fallback;
        }
    }

    /** End-of-day snapshots (funded addresses, UTXO size) for every day in [from, to]. */
    private static void snapshotDays(Connection c, int from, int to) throws SQLException {
        if (from > to) {
            return;
        }
        long funded = 0, boxes = 0;
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*), COALESCE(SUM(box_count), 0) FROM address_balance WHERE nano_erg > 0");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                funded = rs.getLong(1);
                boxes = rs.getLong(2);
            }
        }
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO daily_stats (day, funded_addresses, utxo_boxes) VALUES (?,?,?)"
                + " ON DUPLICATE KEY UPDATE funded_addresses = VALUES(funded_addresses), utxo_boxes = VALUES(utxo_boxes)")) {
            for (int d = from; d <= to; d++) {
                ps.setInt(1, d);
                ps.setLong(2, funded);
                ps.setLong(3, boxes);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // ── rollback ────────────────────────────────────────────────────────────────────────────

    /** Removes the block at {@code height} (reorg): its tokens, boxes, inputs and txs; boxes it spent become unspent; aggregates reversed. */
    public void rollback(long height) {
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                exec(c, "DELETE FROM token WHERE block_height = ?", height);
                // address_tx rows of the block's txs, then the counters of the addresses involved recomputed from what is left
                List<Long> touched = new ArrayList<>();
                try (PreparedStatement ps = c.prepareStatement("SELECT DISTINCT a.script_id FROM address_tx a JOIN tx t ON t.gix = a.tx_gix WHERE t.block_height = ?")) {
                    ps.setLong(1, height);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            touched.add(rs.getLong(1));
                        }
                    }
                }
                exec(c, "DELETE a FROM address_tx a JOIN tx t ON t.gix = a.tx_gix WHERE t.block_height = ?", height);
                for (int i = 0; i < touched.size(); i += 500) {
                    List<Long> chunk = touched.subList(i, Math.min(touched.size(), i + 500));
                    String in = placeholders(chunk.size());
                    try (PreparedStatement ps = c.prepareStatement("UPDATE address_balance ab LEFT JOIN (SELECT script_id, COUNT(*) n, MIN(tx_gix) f, MAX(tx_gix) l"
                            + " FROM address_tx WHERE script_id IN (" + in + ") GROUP BY script_id) x ON x.script_id = ab.script_id"
                            + " SET ab.tx_count = COALESCE(x.n, 0), ab.first_tx_gix = COALESCE(x.f, 0), ab.last_tx_gix = COALESCE(x.l, 0)"
                            + " WHERE ab.script_id IN (" + in + ")")) {
                        for (int j = 0; j < chunk.size(); j++) {
                            ps.setLong(j + 1, chunk.get(j));
                            ps.setLong(j + 1 + chunk.size(), chunk.get(j));
                        }
                        ps.executeUpdate();
                    }
                }
                // Aggregates: undo the outputs of the block (-) and the spends it made (+). Target columns are
                // qualified: unqualified names would be ambiguous with the SELECT tables in ON DUPLICATE KEY UPDATE.
                exec(c, "INSERT INTO address_balance (script_id, nano_erg, box_count)"
                        + " SELECT script_id, -SUM(value), -COUNT(*) FROM box WHERE block_height = ? GROUP BY script_id"
                        + " ON DUPLICATE KEY UPDATE address_balance.nano_erg = address_balance.nano_erg + VALUES(nano_erg),"
                        + " address_balance.box_count = address_balance.box_count + VALUES(box_count)", height);
                exec(c, "INSERT INTO token_holder (token_id, script_id, amount)"
                        + " SELECT ba.token_id, b.script_id, -SUM(ba.amount) FROM box b JOIN box_asset ba ON ba.box_gix = b.gix"
                        + " WHERE b.block_height = ? GROUP BY ba.token_id, b.script_id"
                        + " ON DUPLICATE KEY UPDATE token_holder.amount = token_holder.amount + VALUES(amount)", height);
                exec(c, "INSERT INTO address_balance (script_id, nano_erg, box_count)"
                        + " SELECT b.script_id, SUM(b.value), COUNT(*) FROM box_spent s JOIN box b ON b.gix = s.box_gix WHERE s.height = ? GROUP BY b.script_id"
                        + " ON DUPLICATE KEY UPDATE address_balance.nano_erg = address_balance.nano_erg + VALUES(nano_erg),"
                        + " address_balance.box_count = address_balance.box_count + VALUES(box_count)", height);
                exec(c, "INSERT INTO token_holder (token_id, script_id, amount)"
                        + " SELECT ba.token_id, b.script_id, SUM(ba.amount) FROM box_spent s JOIN box b ON b.gix = s.box_gix"
                        + " JOIN box_asset ba ON ba.box_gix = b.gix WHERE s.height = ? GROUP BY ba.token_id, b.script_id"
                        + " ON DUPLICATE KEY UPDATE token_holder.amount = token_holder.amount + VALUES(amount)", height);
                String[] cols = {"blocks", "txs", "fees", "size", "difficulty_sum", "emission", "reemitted", "boxes_created", "boxes_spent", "token_transfers", "tokens_minted"};
                StringBuilder odku = new StringBuilder();
                for (String col : cols) {
                    odku.append(odku.isEmpty() ? "" : ", ").append("daily_stats.").append(col).append(" = daily_stats.").append(col).append(" + VALUES(").append(col).append(")");
                }
                exec(c, "INSERT INTO daily_stats (day, blocks, txs, fees, size, difficulty_sum, emission, reemitted, boxes_created, boxes_spent, token_transfers, tokens_minted)"
                        + " SELECT FLOOR(b.timestamp / 86400000), -1, -b.tx_count, -b.fees, -b.size, -b.difficulty, -b.emission, -b.reemitted,"
                        + " -(SELECT COUNT(*) FROM box x WHERE x.block_height = b.height), -(SELECT COUNT(*) FROM box_spent s WHERE s.height = b.height),"
                        + " -(SELECT COUNT(*) FROM box x JOIN box_asset ba ON ba.box_gix = x.gix WHERE x.block_height = b.height),"
                        + " -(SELECT COUNT(*) FROM token t WHERE t.block_height = b.height) FROM block b WHERE b.height = ?"
                        + " ON DUPLICATE KEY UPDATE " + odku, height);
                // daily_address is left as is: an address active in a reorged block is almost always active in its replacement
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM token_holder WHERE amount <= 0")) {
                    ps.executeUpdate();
                }
                exec(c, "DELETE FROM box_spent WHERE height = ?", height);
                exec(c, "DELETE ba FROM box_asset ba JOIN box b ON b.gix = ba.box_gix WHERE b.block_height = ?", height);
                exec(c, "DELETE FROM box WHERE block_height = ?", height);
                exec(c, "DELETE ti FROM tx_input ti JOIN tx t ON t.gix = ti.tx_gix WHERE t.block_height = ?", height);
                exec(c, "DELETE FROM tx WHERE block_height = ?", height);
                exec(c, "DELETE FROM block WHERE height = ?", height);
                String prevId = blockIdAtIn(c, height - 1);
                try (PreparedStatement ps = c.prepareStatement(SAVE_STATE)) {
                    ps.setLong(1, height - 1);
                    ps.setBytes(2, prevId == null ? new byte[32] : Hex.decode(prevId));
                    ps.setLong(3, System.currentTimeMillis());
                    ps.setLong(4, height);
                    if (ps.executeUpdate() != 1) {
                        throw new ConflictException("indexer_state moved during rollback of " + height);
                    }
                }
                c.commit();
                resetCounters(); // the key sequences continue from what is left
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                resetCounters();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Rollback of block " + height, e);
        }
    }

    private static String blockIdAtIn(Connection c, long height) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT id FROM block WHERE height = ?")) {
            ps.setLong(1, height);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Hex.encode(rs.getBytes(1)) : null;
            }
        }
    }

    private static void exec(Connection c, String sql, long param) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, param);
            ps.executeUpdate();
        }
    }

}
