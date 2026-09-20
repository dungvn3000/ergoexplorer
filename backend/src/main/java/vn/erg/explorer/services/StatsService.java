package vn.erg.explorer.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import vn.erg.explorer.utils.Memo;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import vn.erg.explorer.dtos.NetworkStatsDto;
import vn.erg.explorer.node.NodeClient;
import vn.erg.explorer.utils.Emission;
import vn.erg.explorer.utils.ErgoAddress;
import vn.erg.explorer.utils.ErgoConstants;
import vn.erg.explorer.utils.Parallel;

import java.time.Duration;
import java.util.*;
import java.util.stream.IntStream;

import static vn.erg.explorer.utils.ErgoConstants.*;

/** Dashboard numbers. Everything derives from headers: hashrate = difficulty / target block time. */
@Singleton
public class StatsService {

    private final Memo<NetworkStatsDto> stats;
    /** Daily hashrate samples: the header one day back never changes. */
    private final Cache<Long, Double> dailyHashrate = Caffeine.newBuilder().maximumSize(1000).build();

    private final NodeClient node;
    private final ChainState chain;
    private final HeaderService headers;
    private final LabelService labels;

    @Inject
    public StatsService(NodeClient node, ChainState chain, HeaderService headers, LabelService labels) {
        this.node = node;
        this.chain = chain;
        this.headers = headers;
        this.labels = labels;
        this.stats = new Memo<>(Duration.ofSeconds(15), this::compute);
    }

    public NetworkStatsDto get() {
        return stats.get();
    }

    private NetworkStatsDto compute() {
        JsonNode info = chain.info();
        long height = info.path("fullHeight").asLong();
        NetworkStatsDto s = new NetworkStatsDto();
        s.setHeight(height);
        s.setEpoch(height / EPOCH_LENGTH);
        s.setBlocksToNextEpoch(EPOCH_LENGTH - height % EPOCH_LENGTH);
        s.setPeers(info.path("peersCount").asInt());
        s.setNodeVersion(info.path("appVersion").asText());
        s.setNodeName(info.path("name").asText());
        s.setMempoolCount(info.path("unconfirmedCount").asInt());
        s.setMaxSupply(MAX_SUPPLY);

        // Last 24h of headers: tip time, block interval, difficulty, miner distribution
        List<JsonNode> day = headers.range(height - BLOCKS_PER_DAY, height);
        JsonNode tip = day.get(day.size() - 1);
        s.setTipTimestamp(tip.path("timestamp").asLong());
        s.setDifficulty(Long.parseLong(tip.path("difficulty").asText("0")));
        s.setHashrate(terahash(s.getDifficulty()));
        if (day.size() > 1) {
            long span = tip.path("timestamp").asLong() - day.get(0).path("timestamp").asLong();
            s.setAvgBlockTimeSec(span / 1000.0 / (day.size() - 1));
        }
        Map<String, Integer> byPk = new HashMap<>();
        for (JsonNode h : day) {
            byPk.merge(h.path("powSolutions").path("pk").asText(), 1, Integer::sum);
        }
        s.setPoolShare24h(byPk.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(8)
                .map(e -> {
                    NetworkStatsDto.PoolShare p = new NetworkStatsDto.PoolShare();
                    p.setAddress(ErgoAddress.fromPublicKey(e.getKey()));
                    String label = labels.label(p.getAddress());
                    p.setName(label != null ? label : p.getAddress().substring(0, 8) + "…");
                    p.setBlocks(e.getValue());
                    return p;
                }).toList());

        // 30 daily samples of hashrate, one header each (cached per height)
        List<Long> sampleHeights = IntStream.rangeClosed(0, 29)
                .mapToObj(i -> height - (long) (29 - i) * BLOCKS_PER_DAY).toList();
        List<Double> samples = Parallel.map(sampleHeights, h -> {
            if (h == height) {
                return s.getHashrate();
            }
            Double cached = dailyHashrate.getIfPresent(h);
            if (cached == null) {
                cached = headers.at(h).map(n -> terahash(Long.parseLong(n.path("difficulty").asText("0")))).orElse(0.0);
                dailyHashrate.put(h, cached);
            }
            return cached;
        });
        List<NetworkStatsDto.Point> series = new ArrayList<>();
        for (int i = 0; i < samples.size(); i++) {
            NetworkStatsDto.Point p = new NetworkStatsDto.Point();
            p.setDay(s.getTipTimestamp() - (long) (29 - i) * 86_400_000L);
            p.setValue(samples.get(i));
            series.add(p);
        }
        s.setHashrate30d(series);
        double weekAgo = samples.get(samples.size() - 8);
        s.setHashrateChange7d(weekAgo > 0 ? Math.round((s.getHashrate() / weekAgo - 1) * 1000) / 10.0 : 0);

        // Circulating = issued by the emission box − everything EIP-27 earmarked for re-emission so far
        // (in the re-emission box, pay-to-reemission boxes and unspent reward boxes). Same formula as
        // the official explorer; the earmarked total follows the schedule exactly (utils/Emission).
        node.get("/emission/at/" + height).ifPresent(e -> s.setIssued(e.path("totalCoinsIssued").asLong() / NANO));
        s.setReemissionLocked(Emission.totalReemitted(height));
        s.setCirculating(s.getIssued() - s.getReemissionLocked());

        // Mempool bytes: the node reports only the count; a light listing gives sizes
        node.get("/transactions/unconfirmed?limit=200&offset=0").ifPresent(list -> {
            long bytes = 0;
            for (JsonNode t : list) {
                bytes += t.path("size").asLong();
            }
            s.setMempoolBytes(bytes);
        });
        return s;
    }

    private static double terahash(long difficulty) {
        return Math.round(difficulty / (double) BLOCK_TIME_SEC / 1e12 * 100) / 100.0;
    }

}
