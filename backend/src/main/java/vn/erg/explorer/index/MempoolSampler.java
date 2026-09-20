package vn.erg.explorer.index;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import vn.erg.explorer.node.NodeClient;
import vn.erg.explorer.utils.ErgoConstants;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Stores a mempool snapshot (tx count, bytes, fees waiting) every minute — the only chart input
 * that cannot be rebuilt from the chain. Runs in the indexer instance only. Keeps one year.
 */
@Slf4j
@Singleton
public class MempoolSampler {

    private static final long INTERVAL_MS = 60_000;
    private static final long RETENTION_MS = 365L * 86_400_000L;

    private final NodeClient node;
    private final DataSource ds;

    @Inject
    public MempoolSampler(NodeClient node, DataSource ds) {
        this.node = node;
        this.ds = ds;
        Thread t = new Thread(this::run, "mempool-sampler");
        t.setDaemon(true);
        t.start();
    }

    private void run() {
        while (true) {
            try {
                sample();
            } catch (RuntimeException | SQLException e) {
                log.warn("Mempool sample failed: {}", e.toString());
            }
            try {
                Thread.sleep(INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void sample() throws SQLException {
        JsonNode list = node.getOrThrow("/transactions/unconfirmed?limit=1000&offset=0");
        int count = 0;
        long bytes = 0, fees = 0;
        for (JsonNode tx : list) {
            count++;
            bytes += tx.path("size").asLong();
            for (JsonNode out : tx.path("outputs")) {
                if (ErgoConstants.FEE_TREE.equals(out.path("ergoTree").asText())) {
                    fees += out.path("value").asLong();
                }
            }
        }
        long now = System.currentTimeMillis();
        try (Connection c = ds.getConnection();
             PreparedStatement ins = c.prepareStatement("INSERT IGNORE INTO mempool_sample (ts, tx_count, bytes, fees) VALUES (?,?,?,?)");
             PreparedStatement del = c.prepareStatement("DELETE FROM mempool_sample WHERE ts < ?")) {
            ins.setLong(1, now);
            ins.setInt(2, count);
            ins.setLong(3, bytes);
            ins.setLong(4, fees);
            ins.executeUpdate();
            del.setLong(1, now - RETENTION_MS);
            del.executeUpdate();
        }
    }

}
