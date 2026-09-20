package vn.erg.explorer.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import vn.erg.explorer.node.NodeClient;
import vn.erg.explorer.node.NodeException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Block headers by height, via {@code /blocks/chainSlice} (one call for a whole range). Headers
 * near the tip may still be reorged, so they are cached only briefly; older ones for long.
 */
@Singleton
public class HeaderService {

    private static final int REORG_DEPTH = 10;

    private final Cache<Long, JsonNode> stable = Caffeine.newBuilder().maximumSize(50_000).build();
    private final Cache<Long, JsonNode> recent = Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(15)).build();

    private final NodeClient node;
    private final ChainState chain;

    @Inject
    public HeaderService(NodeClient node, ChainState chain) {
        this.node = node;
        this.chain = chain;
    }

    /** Headers for [from, to] inclusive, ascending by height. */
    public List<JsonNode> range(long from, long to) {
        from = Math.max(1, from);
        List<JsonNode> out = new ArrayList<>();
        List<Long> missing = new ArrayList<>();
        for (long h = from; h <= to; h++) {
            JsonNode cached = cached(h);
            if (cached == null) {
                missing.add(h);
            }
        }
        if (!missing.isEmpty()) {
            long lo = missing.get(0), hi = missing.get(missing.size() - 1);
            JsonNode slice = node.get("/blocks/chainSlice?fromHeight=" + (lo - 1) + "&toHeight=" + hi)
                    .orElseThrow(() -> new NodeException("chainSlice failed", 502));
            long tip = chain.height();
            for (JsonNode header : slice) {
                long h = header.path("height").asLong();
                (h <= tip - REORG_DEPTH ? stable : recent).put(h, header);
            }
        }
        for (long h = from; h <= to; h++) {
            JsonNode header = cached(h);
            if (header != null) {
                out.add(header);
            }
        }
        return out;
    }

    public Optional<JsonNode> at(long height) {
        List<JsonNode> r = range(height, height);
        return r.isEmpty() ? Optional.empty() : Optional.of(r.get(0));
    }

    public long timestampAt(long height) {
        return at(height).map(h -> h.path("timestamp").asLong()).orElse(0L);
    }

    private JsonNode cached(long h) {
        JsonNode n = stable.getIfPresent(h);
        return n != null ? n : recent.getIfPresent(h);
    }

}
