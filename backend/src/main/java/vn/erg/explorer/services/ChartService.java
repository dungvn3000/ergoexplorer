package vn.erg.explorer.services;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jdbi.v3.core.Jdbi;
import vn.erg.explorer.utils.Emission;
import vn.erg.explorer.utils.Memo;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static vn.erg.explorer.utils.ErgoConstants.BLOCK_TIME_SEC;
import static vn.erg.explorer.utils.ErgoConstants.NANO;

/**
 * Chart series from the daily rollups (daily_stats, daily_address) and mempool samples.
 * Points are {@code {t: epoch millis (UTC day start), v: value}}. A request for the last N days is cut from the series
 * of the smallest range in {@link #RANGES} that covers it (else the whole history), each kept for a minute: callers
 * cannot make the DB compute a new series by varying {@code days}.
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

    private final Jdbi jdbi;
    /** Day ranges actually computed (0 = all history); everything else is sliced from one of them. */
    private static final int[] RANGES = {30, 90, 365};

    /** "name:range" -> series, recomputed a minute after it was made; at most RANGES.length + 1 entries per chart. */
    private final Map<String, Memo<Series>> cache = new ConcurrentHashMap<>();

    @Inject
    public ChartService(Jdbi jdbi) {
        this.jdbi = jdbi;
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
        int range = 0;
        for (int r : RANGES) {
            if (days > 0 && days <= r) {
                range = r;
                break;
            }
        }
        int computed = range;
        // Memo: one query per series at a time, run outside any map lock (see Memo)
        Series s = cache.computeIfAbsent(name + ":" + range, k -> new Memo<>(Duration.ofMinutes(1), () -> compute(name, computed))).get();
        if (days <= 0 || days == range) {
            return Optional.of(s);
        }
        long from = (System.currentTimeMillis() / 86_400_000L - days) * 86_400_000L;
        return Optional.of(new Series(s.name(), s.unit(), s.points().stream().filter(p -> p.t() >= from).toList()));
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

    /** One point per day from a {@code (day, v)} query; days whose value is NULL (no data yet) are skipped. */
    private Series daily(String name, String unit, String sql, int from) {
        List<Point> pts = jdbi.withHandle(h -> h.createQuery(sql).bind("from", from)
                .map((rs, ctx) -> {
                    double v = rs.getDouble("v");
                    return rs.wasNull() ? null : new Point(rs.getLong("day") * 86_400_000L, v);
                })
                .stream().filter(Objects::nonNull).toList());
        return new Series(name, unit, pts);
    }

    /** Circulating supply at the end of each day, from the emission schedule and the last height of the day. */
    private Series circulating(int from) {
        long fromTs = (long) from * 86_400_000L;
        // Last height of each day; issued/reemitted accumulate over the schedule as heights grow
        List<long[]> days = jdbi.withHandle(h -> h.createQuery("SELECT FLOOR(timestamp / 86400000) AS day, MAX(height) AS h FROM block WHERE timestamp >= :ts GROUP BY day ORDER BY day")
                .bind("ts", fromTs)
                .map((rs, ctx) -> new long[]{rs.getLong("day"), rs.getLong("h")})
                .list());
        List<Point> pts = new ArrayList<>();
        long issued = 0, reemitted = 0, prevH = 0;
        for (long[] d : days) {
            long h = d[1];
            if (prevH == 0 && from > 0) {
                // series starts mid-chain: seed with everything before the first day in range
                long start = jdbi.withHandle(hd -> hd.createQuery("SELECT COALESCE(MIN(height), 1) AS h FROM block WHERE timestamp >= :ts")
                        .bind("ts", fromTs).mapTo(long.class).one());
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
            pts.add(new Point(d[0] * 86_400_000L, issued - reemitted));
        }
        return new Series("circulatingSupply", "ERG", pts);
    }

    private Series mempool(String unit, String column, int fromDay) {
        // one point per hour (average) to keep the series small
        List<Point> pts = jdbi.withHandle(h -> h.createQuery("SELECT FLOOR(ts / 3600000) * 3600000 AS t, AVG(" + column + ") AS v FROM mempool_sample WHERE ts >= :ts GROUP BY t ORDER BY t")
                .bind("ts", (long) fromDay * 86_400_000L)
                .map((rs, ctx) -> new Point(rs.getLong("t"), rs.getDouble("v")))
                .list());
        return new Series("mempool" + (column.equals("bytes") ? "Bytes" : "Txs"), unit, pts);
    }

}
