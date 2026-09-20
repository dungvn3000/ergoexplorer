package vn.erg.explorer.node;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Thin HTTP client over the Ergo node REST API, load-balanced over all configured nodes:
 * requests are spread round-robin, so with two nodes each takes half the traffic. A node that
 * fails (I/O error, timeout, 5xx) is skipped for the request (the next node retries it) and
 * left out of the rotation for {@link #COOLDOWN} before being tried again. A 404 is not a
 * failure — it means "no such object" on every node.
 */
@Slf4j
public class NodeClient {

    private final List<String> nodes;
    private final Duration timeout;
    private final ObjectMapper mapper;
    private final HttpClient http;
    /** Round-robin position; each request starts at the next node. */
    private final AtomicInteger rotation = new AtomicInteger(0);
    /** Per node: epoch millis until which it is considered down (0 = healthy). */
    private final AtomicLongArray downUntil;
    /** How long a failed node stays out of the rotation. */
    private static final Duration COOLDOWN = Duration.ofSeconds(30);
    /** Upper bound on concurrent requests to the nodes, whatever the fan-out of a single API call. */
    private final Semaphore inFlight = new Semaphore(32);

    public NodeClient(List<String> nodes, Duration timeout, ObjectMapper mapper) {
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("ergo.nodes must list at least one node");
        }
        this.nodes = nodes.stream().map(n -> n.endsWith("/") ? n.substring(0, n.length() - 1) : n).toList();
        this.downUntil = new AtomicLongArray(this.nodes.size());
        this.timeout = timeout;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** GET; empty when the node answers 404. */
    public Optional<JsonNode> get(String path) {
        return send(path, HttpRequest.BodyPublishers.noBody(), "GET");
    }

    /** GET that must succeed. */
    public JsonNode getOrThrow(String path) {
        return get(path).orElseThrow(() -> new NodeException("Not found: " + path, 404));
    }

    /** POST with a plain-text body (the indexer takes an address as text/plain); empty on 404. */
    public Optional<JsonNode> postText(String path, String body) {
        return send(path, HttpRequest.BodyPublishers.ofString(body), "POST");
    }

    /** Nodes in the order to try for one request: healthy ones first (round-robin), then the ones cooling down. */
    private int[] order() {
        int n = nodes.size();
        int start = Math.floorMod(rotation.getAndIncrement(), n);
        long now = System.currentTimeMillis();
        int[] out = new int[n];
        int k = 0;
        for (int i = 0; i < n; i++) {
            int idx = (start + i) % n;
            if (downUntil.get(idx) <= now) {
                out[k++] = idx;
            }
        }
        for (int i = 0; i < n; i++) {
            int idx = (start + i) % n;
            if (downUntil.get(idx) > now) {
                out[k++] = idx;
            }
        }
        return out;
    }

    private void markDown(int idx) {
        downUntil.set(idx, System.currentTimeMillis() + COOLDOWN.toMillis());
    }

    /** True when the node is currently in the rotation (for /info). */
    public boolean isHealthy(String base) {
        int idx = nodes.indexOf(base);
        return idx >= 0 && downUntil.get(idx) <= System.currentTimeMillis();
    }

    public List<String> nodes() {
        return nodes;
    }

    private Optional<JsonNode> send(String path, HttpRequest.BodyPublisher body, String method) {
        NodeException last = null;
        for (int idx : order()) {
            String base = nodes.get(idx);
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(base + path))
                        .timeout(timeout)
                        .header("Accept", "application/json")
                        .header("Content-Type", "text/plain")
                        .method(method, body)
                        .build();
                long started = System.nanoTime();
                HttpResponse<byte[]> response;
                inFlight.acquire();
                try {
                    response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
                } finally {
                    inFlight.release();
                }
                int status = response.statusCode();
                if (log.isDebugEnabled()) {
                    log.debug("{} {}{} -> {} in {} ms", method, base, path, status, (System.nanoTime() - started) / 1_000_000);
                }
                if (status == 404) {
                    return Optional.empty();
                }
                if (status >= 200 && status < 300) {
                    downUntil.set(idx, 0);
                    return Optional.of(mapper.readTree(response.body()));
                }
                // 400 from the indexer also means "unknown id" for some endpoints — treat as not found
                if (status == 400) {
                    log.debug("{} {} -> 400: {}", method, path, new String(response.body()));
                    return Optional.empty();
                }
                last = new NodeException(base + path + " -> HTTP " + status, status);
                markDown(idx);
                log.warn("Node {} answered {} for {} — out of rotation for {}s", base, status, path, COOLDOWN.toSeconds());
            } catch (IOException | RuntimeException e) {
                last = new NodeException(base + path + " -> " + e.getMessage(), e);
                markDown(idx);
                log.warn("Node {} failed for {}: {} — out of rotation for {}s", base, path, e.toString(), COOLDOWN.toSeconds());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new NodeException("Interrupted while calling " + path, e);
            }
        }
        throw last != null ? last : new NodeException("No node configured");
    }

}
