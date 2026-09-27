package vn.erg.explorer.index;

import com.typesafe.config.Config;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import vn.erg.explorer.node.NodeClient;
import vn.erg.explorer.utils.Hex;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Fills in the full size of deep blocks. A block's size is header + transactions + extension + AD proofs,
 * but the node keeps the AD proofs only for its newest {@code adProofsSuffixLength} blocks (~115k, about
 * five months): the size it reports for an older block lacks them, and that is what the indexer stored
 * when it synced the history (a block indexed near the tip has its proofs and is right). The proofs cannot
 * be rebuilt, so the size of every block below the node's proof window is checked against an explorer that
 * kept it ({@code indexer.sizeRepair.source}) and corrected together with the daily rollup
 * ({@code daily_stats.size}). Runs through the history once, a page at a time, resuming from
 * {@code size_repair_state.height}; afterwards it only re-checks the blocks that have since dropped out
 * of the proof window (a few hundred a day). Runs in the indexer instance only.
 * <p>
 * The progress row is its own table on purpose: this task commits every second while the indexer's batch
 * transaction is open, and two writers on one row (the {@code indexer_state} singleton) fail under
 * MariaDB's snapshot isolation with "Record has changed since last read".
 */
@Slf4j
@Singleton
public class BlockSizeRepair {

    /** Between pages, so the source sees at most a few requests per second. */
    private static final long PAUSE_MS = 250;
    /** When every block below the proof window has been checked. */
    private static final long IDLE_MS = 3_600_000;
    private static final long RETRY_MS = 30_000;
    /** How long the computed proof window boundary is trusted (it moves up one block per block). */
    private static final long LIMIT_TTL_MS = 3_600_000;
    /** The ergoplatform.com explorer API, which stores the full size of every block. */
    private static final String DEFAULT_SOURCE = "https://api.ergoplatform.com/api/v1";

    private final NodeClient node;
    private final DataSource ds;
    private final JsonMapper mapper;
    private final HttpClient http;
    private final String source;
    private final int pageSize;
    private final Duration timeout;
    private long limit = -1;
    private long limitComputed = 0;

    @Inject
    public BlockSizeRepair(NodeClient node, DataSource ds, JsonMapper mapper, Config config) {
        this.node = node;
        this.ds = ds;
        this.mapper = mapper;
        // defaults apply when a deployed application.conf predates these keys
        this.source = (config.hasPath("indexer.sizeRepair.source") ? config.getString("indexer.sizeRepair.source") : DEFAULT_SOURCE)
                .replaceAll("/+$", "");
        this.pageSize = Math.max(1, Math.min(500, config.hasPath("indexer.sizeRepair.pageSize") ? config.getInt("indexer.sizeRepair.pageSize") : 500));
        this.timeout = Duration.ofSeconds(Math.max(5, config.getLong("ergo.timeoutSeconds")) * 3);
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
        Thread t = new Thread(this::run, "block-size-repair");
        t.setDaemon(true);
        t.start();
        log.info("Block size repair started (source {}, {} blocks per page)", source, pageSize);
    }

    private void run() {
        while (true) {
            long sleep;
            try {
                sleep = step() ? PAUSE_MS : IDLE_MS;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (SQLException | IOException | RuntimeException e) {
                log.warn("Block size repair: {} — retrying in {}s", e.toString(), RETRY_MS / 1000);
                sleep = RETRY_MS;
            }
            try {
                Thread.sleep(sleep);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** Checks one page of blocks above the last checked height; false when there is nothing to do now. */
    private boolean step() throws SQLException, IOException, InterruptedException {
        long done = repaired();
        long top = proofWindowStart();
        if (done >= top) {
            return false;
        }
        JsonNode items = fetchPage(done).path("items");
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                Map<Long, Stored> stored = load(c, done + 1, done + pageSize);
                long last = done;
                int fixed = 0;
                for (JsonNode item : items) {
                    long height = item.path("height").asLong();
                    if (height != last + 1 || height > top) {
                        break;
                    }
                    Stored s = stored.get(height);
                    if (s == null) {
                        break; // not indexed yet
                    }
                    if (!s.id().equalsIgnoreCase(item.path("id").asText())) {
                        log.warn("Block size repair: block {} is {} here but {} at {} — stopping at this height",
                                height, s.id(), item.path("id").asText(), source);
                        break;
                    }
                    int size = item.path("size").asInt();
                    if (size > 0 && size != s.size()) {
                        fix(c, height, s, size);
                        fixed++;
                    }
                    last = height;
                }
                if (last > done && !saveRepaired(c, done, last)) {
                    // moved by another writer (another instance, or by hand): drop this page and re-read
                    c.rollback();
                    log.info("Block size repair: size_repair_state.height is no longer {} — re-reading", done);
                    return true;
                }
                c.commit();
                if (last > done) {
                    log.info("Block size repair: {}..{} checked, {} corrected, {} blocks to go", done + 1, last, fixed, top - last);
                }
                return last > done;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    private record Stored(String id, int size, long timestamp) {
    }

    private Map<Long, Stored> load(Connection c, long from, long to) throws SQLException {
        Map<Long, Stored> out = new HashMap<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT height, id, size, timestamp FROM block WHERE height BETWEEN ? AND ?")) {
            ps.setLong(1, from);
            ps.setLong(2, to);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getLong(1), new Stored(Hex.encode(rs.getBytes(2)), rs.getInt(3), rs.getLong(4)));
                }
            }
        }
        return out;
    }

    /** Stores the full size and moves the day's rollup by the difference. */
    private void fix(Connection c, long height, Stored s, int size) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE block SET size = ? WHERE height = ?")) {
            ps.setInt(1, size);
            ps.setLong(2, height);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement("UPDATE daily_stats SET size = size + ? WHERE day = ?")) {
            ps.setLong(1, (long) size - s.size());
            ps.setInt(2, BlockWriter.dayOf(s.timestamp()));
            ps.executeUpdate();
        }
    }

    /** Last checked height (0 when none); creates the progress row on first use. */
    private long repaired() throws SQLException {
        try (Connection c = ds.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("INSERT IGNORE INTO size_repair_state (id, height) VALUES (1, 0)")) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT height FROM size_repair_state WHERE id = 1");
                 ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    /** Optimistic lock like the indexer's height: only advances from the value this pass started with. */
    private boolean saveRepaired(Connection c, long expected, long height) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE size_repair_state SET height = ? WHERE id = 1 AND height = ?")) {
            ps.setLong(1, height);
            ps.setLong(2, expected);
            return ps.executeUpdate() == 1;
        }
    }

    /** Blocks [offset + 1, offset + pageSize] from the source, oldest first: {@code {items: [{height, id, size, ...}]}}. */
    private JsonNode fetchPage(long offset) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(source + "/blocks?limit=" + pageSize + "&offset=" + offset
                        + "&sortBy=height&sortDirection=asc"))
                .timeout(timeout)
                .header("Accept", "application/json")
                .header("User-Agent", "ergo-explorer-size-repair")
                .GET()
                .build();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " from " + source + " for blocks after " + offset);
        }
        return mapper.readTree(response.body());
    }

    /**
     * Highest indexed height whose block the node serves without AD proofs (0 = the node has them all).
     * Binary search over the DB, since proofs are missing below one height and present above it.
     */
    private long proofWindowStart() throws SQLException {
        long now = System.currentTimeMillis();
        if (limit >= 0 && now - limitComputed < LIMIT_TTL_MS) {
            return limit;
        }
        long lo = 0, hi = indexedHeight();
        if (hi < 1 || hasProofs(1)) {
            hi = 0;
        }
        while (lo < hi) {
            long mid = (lo + hi + 1) / 2;
            if (hasProofs(mid)) {
                hi = mid - 1;
            } else {
                lo = mid;
            }
        }
        limit = lo;
        limitComputed = now;
        log.info("Block size repair: node has no AD proofs up to height {}", limit);
        return limit;
    }

    private boolean hasProofs(long height) throws SQLException {
        byte[] id;
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT id FROM block WHERE height = ?")) {
            ps.setLong(1, height);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return true; // not indexed: nothing to repair there
                }
                id = rs.getBytes(1);
            }
        }
        JsonNode proofs = node.getOrThrow("/blocks/" + Hex.encode(id)).path("adProofs");
        return !proofs.isMissingNode() && !proofs.isNull();
    }

    private long indexedHeight() throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT height FROM indexer_state WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

}
