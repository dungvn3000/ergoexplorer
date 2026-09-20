package vn.erg.explorer.index;

import com.fasterxml.jackson.databind.JsonNode;
import com.typesafe.config.Config;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import vn.erg.explorer.node.NodeClient;
import vn.erg.explorer.node.NodeException;
import vn.erg.explorer.services.ChainState;
import vn.erg.explorer.utils.Parallel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Background sync of the chain into MySQL. Catches up from the last indexed height in batches
 * (blocks fetched from the node in parallel, written in one transaction), then polls for new blocks.
 * A block whose parent is not the block stored below it means a reorg: the stored block is rolled
 * back and re-fetched. Only headers already in the node's main chain are indexed.
 */
@Slf4j
@Singleton
public class ChainIndexer {

    private final NodeClient node;
    private final ChainState chain;
    private final BlockWriter writer;
    private final long startHeight;
    private final int batchSize;
    private final long pollMillis;
    private final AtomicLong indexedHeight = new AtomicLong(0);
    private volatile boolean running = true;
    private boolean genesisDone = false;

    @Inject
    public ChainIndexer(NodeClient node, ChainState chain, BlockWriter writer, Config config) {
        this.node = node;
        this.chain = chain;
        this.writer = writer;
        this.startHeight = Math.max(1, config.getLong("indexer.startHeight"));
        this.batchSize = Math.max(1, config.getInt("indexer.batchSize"));
        this.pollMillis = config.getLong("indexer.pollSeconds") * 1000;
        indexedHeight.set(Math.max(writer.state(), startHeight - 1));
        Thread t = new Thread(this::run, "chain-indexer");
        t.setDaemon(true);
        t.start();
        log.info("Chain indexer started at height {} (batch {}, poll {}s)", indexedHeight.get(), batchSize, pollMillis / 1000);
    }

    /** Last height fully written to the DB. */
    public long indexedHeight() {
        return indexedHeight.get();
    }

    /** True once the DB is within a few blocks of the node and the index starts at genesis. */
    public boolean isSynced() {
        try {
            return startHeight == 1 && chain.height() - indexedHeight.get() <= 3;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public void stop() {
        running = false;
    }

    private void run() {
        while (running) {
            try {
                long nodeHeight = chain.height();
                long from = indexedHeight.get() + 1;
                if (!genesisDone) {
                    // The emission, treasury and no-premine boxes exist before block 1; they are spent by later
                    // blocks, so they are indexed as block 0 to keep every input resolvable.
                    writer.writeGenesis(node.getOrThrow("/utxo/genesis"));
                    genesisDone = true;
                }
                if (from > nodeHeight) {
                    sleep(pollMillis);
                    continue;
                }
                long to = Math.min(nodeHeight, from + batchSize - 1);
                List<IndexedBlock> blocks = fetch(from, to);
                if (blocks.isEmpty()) {
                    sleep(pollMillis);
                    continue;
                }
                // Reorg check against what is stored below the batch
                if (from > startHeight) {
                    String stored = writer.blockIdAt(from - 1);
                    String parent = blocks.get(0).json().path("header").path("parentId").asText();
                    if (stored != null && !stored.equals(parent)) {
                        log.warn("Reorg at height {}: stored {} but parent is {} — rolling back", from - 1, stored, parent);
                        writer.rollback(from - 1);
                        indexedHeight.set(from - 2);
                        continue;
                    }
                }
                long started = System.currentTimeMillis();
                writer.write(blocks, from - 1 < startHeight ? 0 : from - 1);
                indexedHeight.set(blocks.get(blocks.size() - 1).height());
                long took = System.currentTimeMillis() - started;
                if (to - from + 1 >= batchSize || log.isDebugEnabled()) {
                    log.info("Indexed {}..{} ({} blocks, write {} ms, {} behind)", from, to, blocks.size(), took, nodeHeight - to);
                } else {
                    log.info("Indexed block {} ({} tx)", to, blocks.get(blocks.size() - 1).json().path("blockTransactions").path("transactions").size());
                }
            } catch (BlockWriter.ConflictException e) {
                // Another process is indexing the same DB: follow it instead of fighting it
                long dbHeight = writer.state();
                log.warn("{} — resyncing from DB height {}", e.getMessage(), dbHeight);
                indexedHeight.set(Math.max(dbHeight, startHeight - 1));
                sleep(pollMillis);
            } catch (NodeException e) {
                log.warn("Indexer: node error, retrying in {}s: {}", pollMillis / 1000, e.getMessage());
                sleep(pollMillis);
            } catch (RuntimeException e) {
                log.error("Indexer failed, retrying in {}s", pollMillis / 1000, e);
                sleep(pollMillis);
            }
        }
    }

    /** Headers of [from, to] in one call, then the full blocks in parallel. */
    private List<IndexedBlock> fetch(long from, long to) {
        JsonNode slice = node.getOrThrow("/blocks/chainSlice?fromHeight=" + (from - 1) + "&toHeight=" + to);
        List<JsonNode> headers = new ArrayList<>();
        for (JsonNode h : slice) {
            long height = h.path("height").asLong();
            if (height >= from && height <= to) {
                headers.add(h);
            }
        }
        List<IndexedBlock> blocks = new ArrayList<>(Parallel.map(headers, h -> {
            String id = h.path("id").asText();
            JsonNode full = node.getOrThrow("/blocks/" + id);
            return new IndexedBlock(h.path("height").asLong(), id, full);
        }));
        blocks.sort((a, b) -> Long.compare(a.height(), b.height()));
        // contiguous prefix only, in case the slice had a gap
        List<IndexedBlock> out = new ArrayList<>();
        long expect = from;
        for (IndexedBlock b : blocks) {
            if (b.height() != expect) {
                break;
            }
            out.add(b);
            expect++;
        }
        return out;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
