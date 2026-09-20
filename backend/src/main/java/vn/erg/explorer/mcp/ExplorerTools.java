package vn.erg.explorer.mcp;

import io.jooby.annotation.mcp.McpParam;
import io.jooby.annotation.mcp.McpServer;
import io.jooby.annotation.mcp.McpTool;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import vn.erg.explorer.db.ChainRepository;
import vn.erg.explorer.db.IndexStatus;
import vn.erg.explorer.dtos.*;
import vn.erg.explorer.node.NodeClient;
import vn.erg.explorer.services.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * MCP tools for AI agents — thin wrappers over the same services the REST API uses. Amounts are in
 * nanoERG (1 ERG = 1,000,000,000 nanoERG) and token amounts are raw integers (apply the token's
 * decimals); ids are 64-character hex.
 * <p>
 * Every tool answers a {@link Stamped} envelope: {@code asOf} says at which chain height / index height
 * and time the answer was computed and carries a random {@code responseId}, so an agent can tell two
 * calls apart and never mistakes a cached balance for a fresh one; {@code mutable} says whether the
 * data can change with the next block (balances, UTXOs, mempool, rich lists, stats) or is final
 * (confirmed blocks / transactions, boxes, token metadata). The REST API for the frontend is unchanged.
 */
@Singleton
@McpServer("ergo-explorer")
public class ExplorerTools {

    private static final int MAX_ROWS = 100;
    /** Blocks with at most this many confirmations may still be reorged, so their answers are marked mutable. */
    private static final int REORG_DEPTH = 10;

    private final StatsService stats;
    private final BlockService blocks;
    private final TransactionService transactions;
    private final AddressService addresses;
    private final TokenService tokens;
    private final ChartService charts;
    private final MempoolService mempool;
    private final SearchService search;
    private final ChainRepository repo;
    private final NodeClient node;
    private final BoxMapper boxMapper;
    private final ChainState chain;
    private final IndexStatus index;

    @Inject
    public ExplorerTools(StatsService stats, BlockService blocks, TransactionService transactions, AddressService addresses, TokenService tokens,
                         ChartService charts, MempoolService mempool, SearchService search, ChainRepository repo, NodeClient node, BoxMapper boxMapper,
                         ChainState chain, IndexStatus index) {
        this.stats = stats;
        this.blocks = blocks;
        this.transactions = transactions;
        this.addresses = addresses;
        this.tokens = tokens;
        this.charts = charts;
        this.mempool = mempool;
        this.search = search;
        this.repo = repo;
        this.node = node;
        this.boxMapper = boxMapper;
        this.chain = chain;
        this.index = index;
    }

    /**
     * Freshness marker of a tool answer.
     *
     * @param height        chain tip height (node's fullHeight) when the answer was computed
     * @param indexedHeight last block fully written to the explorer's own index (balances, rich lists and
     *                      charts are computed up to here; may lag {@code height} by a few blocks)
     * @param synced        true when the index is complete from genesis and within 3 blocks of the tip
     * @param timestamp     server time of the answer, epoch milliseconds
     * @param responseId    random id of this answer (not stored server-side), to tell two calls apart
     */
    public record AsOf(long height, long indexedHeight, boolean synced, long timestamp, String responseId) {
    }

    /**
     * Tool answer with its freshness marker.
     *
     * @param asOf    when / at which height the data was computed
     * @param mutable true when the data can change with the next block (re-fetch instead of reusing an old
     *                answer); false for final data that is safe to cache (confirmed block / tx, box, token metadata)
     * @param data    the answer itself
     */
    public record Stamped<T>(AsOf asOf, boolean mutable, T data) {
    }

    private <T> Stamped<T> stamp(T data, boolean mutable) {
        return new Stamped<>(new AsOf(chain.height(), index.indexedHeight(), index.isSynced(), System.currentTimeMillis(),
                UUID.randomUUID().toString()), mutable, data);
    }

    /** Data that changes with every block: balances, UTXOs, mempool, stats, rich lists, charts. */
    private <T> Stamped<T> live(T data) {
        return stamp(data, true);
    }

    /** Final data: confirmed blocks and transactions, boxes, token metadata. */
    private <T> Stamped<T> settled(T data) {
        return stamp(data, false);
    }

    /** Optional paging arguments: null or < 1 means the first page / the default size. */
    private static int page(Integer page) {
        return page == null ? 1 : Math.max(1, page);
    }

    private static int rows(Integer rows, int def) {
        return rows == null || rows <= 0 ? def : Math.min(rows, MAX_ROWS);
    }

    private static <T> T found(Optional<T> o, String what) {
        return o.orElseThrow(() -> new IllegalArgumentException(what + " not found on Ergo mainnet"));
    }

    private static <T> T orFail(Optional<T> o, String message) {
        return o.orElseThrow(() -> new IllegalArgumentException(message));
    }

    /**
     * Current state of the Ergo network: height, hashrate (TH/s), difficulty, average block time, circulating and max supply (nanoERG, EIP-27), mempool size, 30-day hashrate and 24h pool shares.
     */
    @McpTool(description = "Current state of the Ergo network: height, hashrate (TH/s), difficulty, average block time, circulating and max supply (nanoERG, EIP-27), mempool size, 30-day hashrate and 24h pool shares.")
    public Stamped<NetworkStatsDto> getNetworkState() {
        return live(stats.get());
    }

    /**
     * A block by height or by 64-hex id: header fields, miner (pool label when known), reward, fees and its transactions with inputs/outputs.
     * @param heightOrId block height (decimal) or block id (hex)
     */
    @McpTool(description = "A block by height or by 64-hex id: header fields, miner (pool label when known), reward, fees and its transactions with inputs/outputs.")
    public Stamped<BlockDto> getBlock(@McpParam(required = true) String heightOrId) {
        BlockDto b = found(blocks.get(heightOrId.trim()), "Block " + heightOrId);
        // A block near the tip can still be orphaned by a reorg; deeper ones are final
        return stamp(b, chain.confirmations(b.getHeight()) <= REORG_DEPTH);
    }

    /**
     * Newest blocks first, paged: height, id, timestamp, miner, tx count, size, reward, fees.
     * @param page 1-based page
     * @param rowsPerPage blocks per page (default 25, max 100)
     */
    @McpTool(description = "Newest blocks first, paged: height, id, timestamp, miner, tx count, size, reward, fees.")
    public Stamped<PageDto<BlockDto>> listBlocks(Integer page, Integer rowsPerPage) {
        return live(blocks.list(page(page), rows(rowsPerPage, 25)));
    }

    /**
     * A transaction by id, confirmed or still in the mempool: inputs, data inputs, outputs (boxes with address, value, tokens, registers), fee, size, confirmations.
     * @param id transaction id (64 hex characters)
     */
    @McpTool(description = "A transaction by id, confirmed or still in the mempool: inputs, data inputs, outputs (boxes with address, value, tokens, registers), fee, size, confirmations.")
    public Stamped<TxDto> getTransaction(@McpParam(required = true) String id) {
        TxDto t = found(transactions.get(id.trim().toLowerCase()), "Transaction " + id);
        // Pending: may be dropped or confirmed any time; freshly confirmed: may still be reorged
        return stamp(t, t.isPending() || chain.confirmations(t.getHeight()) <= REORG_DEPTH);
    }

    /**
     * An address: confirmed balance (nanoERG), unconfirmed change, token balances, transaction count, first/last activity and a page of its transactions (newest first) with direction and amount.
     * @param address Ergo mainnet address (base58, e.g. 9f... for P2PK)
     * @param page 1-based page of the transaction history (20 per page)
     */
    @McpTool(description = "An address: confirmed balance (nanoERG), unconfirmed change, token balances, transaction count, first/last activity and a page of its transactions (newest first) with direction and amount.")
    public Stamped<AddressDto> getAddress(@McpParam(required = true) String address, Integer page) {
        return live(found(addresses.get(address.trim(), page(page), 20), "Address " + address));
    }

    /**
     * Unspent boxes (UTXOs) of an address, newest first: box id, value, tokens, registers, creating tx.
     * @param address Ergo mainnet address
     * @param page 1-based page (20 boxes per page)
     */
    @McpTool(description = "Unspent boxes (UTXOs) of an address, newest first: box id, value, tokens, registers, creating tx.")
    public Stamped<PageDto<BoxDto>> getAddressBoxes(@McpParam(required = true) String address, Integer page) {
        return live(found(addresses.unspentBoxes(address.trim(), page(page), 20), "Address " + address));
    }

    /**
     * A box (transaction output) by id: address, value, tokens, decoded registers R4-R9, creating transaction and whether/where it was spent.
     * @param id box id (64 hex characters)
     */
    @McpTool(description = "A box (transaction output) by id: address, value, tokens, decoded registers R4-R9, creating transaction and whether/where it was spent.")
    public Stamped<BoxDto> getBox(@McpParam(required = true) String id) {
        String boxId = id.trim().toLowerCase();
        BoxDto b = found(repo.box(boxId).or(() -> node.get("/blockchain/box/byId/" + boxId).map(n -> boxMapper.map(n, 0))), "Box " + id);
        // The box itself never changes, but an unspent box can be spent by the next block
        return stamp(b, !Boolean.TRUE.equals(b.getSpent()));
    }

    /**
     * An EIP-4 token by id: name, description, decimals, total supply, issuing transaction, holder count, top holders and recent transfers.
     * @param id token id (64 hex characters)
     */
    @McpTool(description = "An EIP-4 token by id: name, description, decimals, total supply, issuing transaction, holder count, top holders and recent transfers.")
    public Stamped<TokenDto> getToken(@McpParam(required = true) String id) {
        // Metadata is final but the holder count / top holders / recent transfers move with every block
        return live(found(tokens.detail(id.trim().toLowerCase()), "Token " + id));
    }

    /**
     * Rich list of a token: holders by raw amount, with rank and address.
     * @param id token id (64 hex characters)
     * @param page 1-based page (50 holders per page)
     */
    @McpTool(description = "Rich list of a token: holders by raw amount, with rank and address. Needs the chain index.")
    public Stamped<PageDto<TokenDto.HolderDto>> getTokenHolders(@McpParam(required = true) String id, Integer page) {
        return live(repo.holdersPage(id.trim().toLowerCase(), page(page), 50));
    }

    /**
     * Featured tokens listed by the explorer (name, decimals, supply).
     */
    @McpTool(description = "Featured tokens listed by the explorer (name, decimals, supply).")
    public Stamped<List<TokenDto>> listTokens() {
        return live(tokens.featured());
    }

    /**
     * ERG rich list: addresses by balance (nanoERG) with rank and unspent box count.
     * @param page 1-based page (50 addresses per page)
     */
    @McpTool(description = "ERG rich list: addresses by balance (nanoERG) with rank and unspent box count. Needs the chain index.")
    public Stamped<PageDto<TokenDto.HolderDto>> getRichList(Integer page) {
        return live(repo.richList(page(page), 50));
    }

    /**
     * Names of the available chart series (hashrate, difficulty, blocks, transactions, fees, emission, circulatingSupply, activeAddresses, mempoolTxs, ...).
     */
    @McpTool(description = "Names of the available chart series (hashrate, difficulty, blocks, transactions, fees, emission, circulatingSupply, activeAddresses, mempoolTxs, ...).")
    public Stamped<List<String>> listCharts() {
        return settled(charts.names());
    }

    /**
     * A chart series as points {t: epoch ms, v: value} with its unit.
     * @param name series name, see listCharts
     * @param days number of most recent days (0 = all)
     */
    @McpTool(description = "A chart series as points {t: epoch ms, v: value} with its unit. Daily series are per UTC day; mempool series are hourly.")
    public Stamped<ChartService.Series> getChart(@McpParam(required = true) String name, Integer days) {
        return live(found(charts.series(name.trim(), days == null ? 0 : Math.max(0, days)), "Chart " + name));
    }

    /**
     * Unconfirmed transactions currently in the node's mempool with total size and fees.
     */
    @McpTool(description = "Unconfirmed transactions currently in the node's mempool with total size and fees.")
    public Stamped<MempoolTxDto> getMempool() {
        return live(mempool.list());
    }

    /**
     * What a search string is — block height, block id, transaction id, box id, token id or address — so the right tool can be called next.
     * @param q height, 64-hex id or address
     */
    @McpTool(description = "What a search string is — block height, block id, transaction id, box id, token id or address — so the right tool can be called next.")
    public Stamped<SearchResultDto> search(@McpParam(required = true) String q) {
        return settled(orFail(search.resolve(q), "Nothing on Ergo mainnet matches '" + q + "' (not a height, id or valid address)"));
    }

    /**
     * Explorer and node versions, node/index heights and whether the chain index is fully synced (balances, rich lists and charts are exact only when it is).
     */
    @McpTool(description = "Index size on disk and indexed height; the asOf envelope carries chain height, indexed height and sync state.")
    public Stamped<Map<String, Object>> getStatus() {
        return live(Map.of("indexedHeight", repo.indexedHeight(), "db", repo.storage()));
    }

}
