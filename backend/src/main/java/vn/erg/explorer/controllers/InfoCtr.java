package vn.erg.explorer.controllers;

import io.jooby.MediaType;
import io.jooby.annotation.GET;
import io.jooby.annotation.Path;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import vn.erg.explorer.db.ChainRepository;
import vn.erg.explorer.db.IndexStatus;
import vn.erg.explorer.node.NodeClient;
import vn.erg.explorer.services.ChainState;
import vn.erg.explorer.services.StatsService;
import vn.erg.explorer.web.JsonResult;
import vn.erg.explorer.utils.ApplicationVersion;

import java.util.LinkedHashMap;
import java.util.Map;

import static vn.erg.explorer.web.JsonResult.ok;

@Singleton
@Path("/api/v1")
public class InfoCtr extends BaseCtr {

    @Inject
    private StatsService stats;

    @Inject
    private ChainState chain;

    @Inject
    private IndexStatus index;

    @Inject
    private NodeClient nodeClient;

    @Inject
    private ChainRepository repo;

    /** Explorer + node version and sync state. */
    @GET(path = "/info", produces = MediaType.JSON)
    public JsonResult info() {
        var node = chain.info();
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("version", ApplicationVersion.applicationVersion());
        info.put("buildDate", ApplicationVersion.applicationBuildDate());
        info.put("network", node.path("network").asText());
        info.put("nodeName", node.path("name").asText());
        info.put("nodeVersion", node.path("appVersion").asText());
        info.put("height", node.path("fullHeight").asLong());
        info.put("headersHeight", node.path("headersHeight").asLong());
        info.put("peers", node.path("peersCount").asInt());
        info.put("indexedHeight", index.indexedHeight());
        info.put("indexSynced", index.isSynced());
        info.put("nodes", nodeClient.nodes().stream().map(n -> Map.of("url", n, "healthy", nodeClient.isHealthy(n))).toList());
        info.put("db", repo.storage());
        return ok(info);
    }

    /** Dashboard numbers: height, hashrate, difficulty, supply, mempool, 30d hashrate, pools. */
    @GET(path = "/networkState", produces = MediaType.JSON)
    public JsonResult networkState() {
        return ok(stats.get());
    }

}
