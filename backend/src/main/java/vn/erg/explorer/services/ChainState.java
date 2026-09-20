package vn.erg.explorer.services;

import com.fasterxml.jackson.databind.JsonNode;
import vn.erg.explorer.utils.Memo;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import vn.erg.explorer.node.NodeClient;

import java.time.Duration;

/** The node's /info, refreshed every few seconds — the source of the current height for confirmations. */
@Singleton
public class ChainState {

    private final Memo<JsonNode> info;

    @Inject
    public ChainState(NodeClient node) {
        this.info = new Memo<>(Duration.ofSeconds(5), () -> node.getOrThrow("/info"));
    }

    public JsonNode info() {
        return info.get();
    }

    public long height() {
        return info().path("fullHeight").asLong();
    }

    public long confirmations(long inclusionHeight) {
        return Math.max(0, height() - inclusionHeight + 1);
    }

}
