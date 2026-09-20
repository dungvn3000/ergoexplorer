package vn.erg.explorer.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.typesafe.config.Config;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import vn.erg.explorer.db.ChainRepository;
import vn.erg.explorer.db.IndexStatus;
import vn.erg.explorer.dtos.AssetDto;
import vn.erg.explorer.dtos.TokenDto;
import vn.erg.explorer.node.NodeClient;
import vn.erg.explorer.utils.Parallel;

import java.time.Duration;
import java.util.*;

@Singleton
public class TokenService {

    /** Token metadata never changes once minted. */
    private final Cache<String, Optional<TokenDto>> meta = Caffeine.newBuilder().maximumSize(100_000).build();
    private final Cache<String, TokenDto> details = Caffeine.newBuilder()
            .maximumSize(2_000).expireAfterWrite(Duration.ofMinutes(2)).build();

    private final NodeClient node;
    private final HeaderService headers;
    private final List<String> featured;
    private final ChainRepository repo;
    private final IndexStatus index;

    @Inject
    public TokenService(NodeClient node, HeaderService headers, Config config, ChainRepository repo, IndexStatus index) {
        this.node = node;
        this.headers = headers;
        this.featured = config.hasPath("ergo.tokens") ? config.getStringList("ergo.tokens") : List.of();
        this.repo = repo;
        this.index = index;
    }

    /** Name/decimals/supply of a token (no holders), or empty when unknown to the indexer. */
    public Optional<TokenDto> meta(String tokenId) {
        Optional<TokenDto> cached = meta.getIfPresent(tokenId);
        if (cached == null) {
            // fetched outside the cache lock (see Memo); a duplicate fetch under a race is harmless
            // DB first; the node only for tokens minted before the indexed range
            cached = repo.token(tokenId).or(() -> node.get("/blockchain/token/byId/" + tokenId).map(this::toMeta));
            meta.put(tokenId, cached);
        }
        return cached;
    }

    /** Loads metadata of every not-yet-cached token in parallel, so a tx with 100 tokens costs one round-trip, not 100. */
    public void prefetch(Collection<String> tokenIds) {
        List<String> missing = tokenIds.stream().distinct().filter(id -> meta.getIfPresent(id) == null).toList();
        if (missing.size() > 1) {
            Parallel.map(missing, this::meta);
        }
    }

    /** Fills name and decimals of an asset from the token metadata. */
    public AssetDto asset(String tokenId, long amount) {
        AssetDto a = new AssetDto();
        a.setTokenId(tokenId);
        a.setAmount(amount);
        meta(tokenId).ifPresent(t -> {
            a.setName(t.getName());
            a.setDecimals(t.getDecimals());
        });
        if (a.getName() == null) {
            a.setName(tokenId.substring(0, 8));
            a.setDecimals(0);
        }
        return a;
    }

    public List<TokenDto> featured() {
        return Parallel.map(featured, id -> meta(id).orElse(null)).stream().filter(Objects::nonNull).toList();
    }

    public Optional<TokenDto> detail(String tokenId) {
        TokenDto cached = details.getIfPresent(tokenId);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<TokenDto> m = meta(tokenId);
        if (m.isEmpty()) {
            return Optional.empty();
        }
        TokenDto t = copy(m.get());
        t.setIssueTx(m.get().getIssueTx());
        t.setIssueHeight(m.get().getIssueHeight());

        if (index.isSynced()) {
            t.setHolderCount(repo.holderCount(tokenId));
            t.setHolders(repo.topHolders(tokenId, 10));
            t.setTransfers(repo.recentTransfers(tokenId, 12));
            details.put(tokenId, t);
            return Optional.of(t);
        }

        // Issuing transaction = the one that created the issuing box
        if (t.getIssueTx() == null) node.get("/blockchain/box/byId/" + t.getIssueBox()).ifPresent(box -> {
            t.setIssueTx(box.path("transactionId").asText(null));
            t.setIssueHeight(box.path("inclusionHeight").asLong());
        });

        // Holders: aggregate unspent boxes carrying the token (capped scan, so counts are approximate for big tokens)
        Map<String, Long> byAddress = new HashMap<>();
        node.get("/blockchain/box/unspent/byTokenId/" + tokenId + "?offset=0&limit=1000").ifPresent(list -> {
            for (JsonNode box : list) {
                byAddress.merge(box.path("address").asText(), amountOf(box, tokenId), Long::sum);
            }
        });
        t.setHolderCount(byAddress.size());
        t.setHolders(byAddress.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .map(e -> {
                    TokenDto.HolderDto h = new TokenDto.HolderDto();
                    h.setAddress(e.getKey());
                    h.setAmount(e.getValue());
                    return h;
                }).toList());

        // Recent transfers: newest boxes carrying the token
        List<TokenDto.TransferDto> transfers = new ArrayList<>();
        node.get("/blockchain/box/byTokenId/" + tokenId + "?offset=0&limit=12&sortDirection=desc").ifPresent(page -> {
            for (JsonNode box : page.path("items")) {
                TokenDto.TransferDto x = new TokenDto.TransferDto();
                x.setId(box.path("transactionId").asText());
                x.setHeight(box.path("inclusionHeight").asLong());
                x.setTo(box.path("address").asText());
                x.setAmount(amountOf(box, tokenId));
                transfers.add(x);
            }
        });
        List<Long> stamps = Parallel.map(transfers, x -> headers.timestampAt(x.getHeight()));
        for (int i = 0; i < transfers.size(); i++) {
            transfers.get(i).setTimestamp(stamps.get(i));
        }
        t.setTransfers(transfers);

        details.put(tokenId, t);
        return Optional.of(t);
    }

    private static long amountOf(JsonNode box, String tokenId) {
        for (JsonNode a : box.path("assets")) {
            if (tokenId.equals(a.path("tokenId").asText())) {
                return a.path("amount").asLong();
            }
        }
        return 0;
    }

    private TokenDto toMeta(JsonNode n) {
        TokenDto t = new TokenDto();
        t.setId(n.path("id").asText());
        t.setName(n.path("name").asText(""));
        t.setDecimals(n.path("decimals").asInt(0));
        t.setSupply(n.path("emissionAmount").asLong());
        t.setDesc(n.path("description").asText(""));
        t.setIssueBox(n.path("boxId").asText(null));
        return t;
    }

    private static TokenDto copy(TokenDto m) {
        TokenDto t = new TokenDto();
        t.setId(m.getId());
        t.setName(m.getName());
        t.setDecimals(m.getDecimals());
        t.setSupply(m.getSupply());
        t.setDesc(m.getDesc());
        t.setIssueBox(m.getIssueBox());
        return t;
    }

}
