package vn.erg.explorer.services;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.ebean.Database;
import io.ebean.SqlRow;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import vn.erg.explorer.utils.Emission;

import java.time.Duration;
import java.util.*;

import static vn.erg.explorer.utils.ErgoConstants.BLOCK_TIME_SEC;
import static vn.erg.explorer.utils.ErgoConstants.NANO;

/**
 * Chart series from the daily rollups (daily_stats, daily_address) and mempool samples.
 * Points are {@code {t: epoch millis (UTC day start), v: value}}; results are cached for a minute.
 */
@Singleton
public class ChartService {

    public record Point(long t, double v) {
    }

    public record Series(String name, String unit, List<Point> points) {
    }

    /** name -> (unit, SQL expression over daily_stats aliased d). Cumulative series are marked. */
    private static final Map<String, String[]> DAILY = new LinkedHashMap<>();

    static {
        DAILY.put("hashrate", new String[]{"TH/s", "d.difficulty_sum / NULLIF(d.blocks, 0) / " + BLOCK_TIME_SEC + " / 1e12"});
        DAILY.put("difficulty", new String[]{"", "d.difficulty_sum / NULLIF(d.blocks, 0)"});
        DAILY.put("blocks", new String[]{"blocks", "d.blocks"});
        DAILY.put("blockTime", new String[]{"s", "86400 / NULLIF(d.blocks, 0)"});
        DAILY.put("transactions", new String[]{"tx", "d.txs"});
        DAILY.put("fees", new String[]{"ERG", "d.fees / " + NANO});
        DAILY.put("avgFee", new String[]{"ERG", "d.fees / NULLIF(d.txs - d.blocks, 0) / " + NANO});
        DAILY.put("blockSize", new String[]{"bytes", "d.size / NULLIF(d.blocks, 0)"});
        DAILY.put("emission", new String[]{"ERG", "(d.emission - d.reemitted) / " + NANO});
        DAILY.put("boxesCreated", new String[]{"boxes", "d.boxes_created"});
        DAILY.put("boxesSpent", new String[]{"boxes", "d.boxes_spent"});
        DAILY.put("utxoSize", new String[]{"boxes", "d.utxo_boxes"});
        DAILY.put("tokenTransfers", new String[]{"transfers", "d.token_transfers"});
        DAILY.put("tokensMinted", new String[]{"tokens", "d.tokens_minted"});
        DAILY.put("fundedAddresses", new String[]{"addresses", "d.funded_addresses"});
    }

    private final Database db;
    private final Cache<String, Series> cache = Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(1)).maximumSize(200).build();

    @Inject
    public ChartService(Database db) {
        this.db = db;
    }

    public List<String> names() {
        List<String> out = new ArrayList<>(DAILY.keySet());
        out.add("activeAddresses");
        out.add("circulatingSupply");
        out.add("mempoolTxs");
        out.add("mempoolBytes");
        return out;
    }

    /** {@code days} = number of days back from today, 0 = everything. */
    public Optional<Series> series(String name, int days) {
        if (!names().contains(name)) {
            return Optional.empty();
        }
        String key = name + ":" + days;
        Series s = cache.getIfPresent(key);
        if (s == null) {
            s = compute(name, days);
            cache.put(key, s);
        }
        return Optional.of(s);
    }

    private Series compute(String name, int days) {
        int today = (int) (System.currentTimeMillis() / 86_400_000L);
        int from = days <= 0 ? 0 : today - days;
        return switch (name) {
            case "activeAddresses" -> daily("activeAddresses", "addresses",
                    "SELECT day, COUNT(*) AS v FROM daily_address WHERE day >= :from GROUP BY day ORDER BY day", from);
            case "circulatingSupply" -> circulating(from);
            case "mempoolTxs" -> mempool("tx", "tx_count", from);
            case "mempoolBytes" -> mempool("bytes", "bytes", from);
            default -> {
                String[] def = DAILY.get(name);
                yield daily(name, def[0], "SELECT d.day, " + def[1] + " AS v FROM daily_stats d WHERE d.day >= :from ORDER BY d.day", from);
            }
        };
    }

    private Series daily(String name, String unit, String sql, int from) {
        List<Point> pts = new ArrayList<>();
        for (SqlRow r : db.sqlQuery(sql).setParameter("from", from).findList()) {
            Double v = r.getDouble("v");
            if (v != null) {
                pts.add(new Point(r.getLong("day") * 86_400_000L, v));
            }
        }
        return new Series(name, unit, pts);
    }

    /** Circulating supply at the end of each day, from the emission schedule and the last height of the day. */
    private Series circulating(int from) {
        List<Point> pts = new ArrayList<>();
        // Last height of each day; issued/reemitted accumulate over the schedule as heights grow
        long issued = 0, reemitted = 0, prevH = 0;
        for (SqlRow r : db.sqlQuery("SELECT FLOOR(timestamp / 86400000) AS day, MAX(height) AS h FROM block WHERE timestamp >= :ts GROUP BY day ORDER BY day")
                .setParameter("ts", (long) from * 86_400_000L).findList()) {
            long h = r.getLong("h");
            if (prevH == 0 && from > 0) {
                // series starts mid-chain: seed with everything before the first day in range
                long start = db.sqlQuery("SELECT COALESCE(MIN(height), 1) AS h FROM block WHERE timestamp >= :ts").setParameter("ts", (long) from * 86_400_000L).findOne().getLong("h");
                for (long i = 1; i < start; i++) {
                    issued += Emission.emissionAt(i);
                    reemitted += Emission.reemittedAt(i);
                }
                prevH = start - 1;
            }
            for (long i = prevH + 1; i <= h; i++) {
                issued += Emission.emissionAt(i);
                reemitted += Emission.reemittedAt(i);
            }
            prevH = h;
            pts.add(new Point(r.getLong("day") * 86_400_000L, issued - reemitted));
        }
        return new Series("circulatingSupply", "ERG", pts);
    }

    private Series mempool(String unit, String column, int fromDay) {
        List<Point> pts = new ArrayList<>();
        // one point per hour (average) to keep the series small
        for (SqlRow r : db.sqlQuery("SELECT FLOOR(ts / 3600000) * 3600000 AS t, AVG(" + column + ") AS v FROM mempool_sample WHERE ts >= :ts GROUP BY t ORDER BY t")
                .setParameter("ts", (long) fromDay * 86_400_000L).findList()) {
            pts.add(new Point(r.getLong("t"), r.getDouble("v")));
        }
        return new Series("mempool" + (column.equals("bytes") ? "Bytes" : "Txs"), unit, pts);
    }

}
