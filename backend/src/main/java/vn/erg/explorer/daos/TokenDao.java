package vn.erg.explorer.daos;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.reflect.ConstructorMapper;
import vn.erg.explorer.models.Token;
import vn.erg.explorer.utils.Hex;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

@Singleton
public class TokenDao extends BaseDao<Token> {

    /** A transaction that created or spent boxes carrying the token: gix, hex id, block, timestamp. */
    public record TransferTx(long txGix, String txId, long blockHeight, long timestamp) {
    }

    /** Raw amount of the token an address sent (inputs) or received (outputs) in a transaction. */
    public record Flow(long txGix, String address, long amount) {
    }

    @Inject
    public TokenDao(Jdbi jdbi) {
        super(Token.class, jdbi);
    }

    public Optional<Token> findByHash(String id) {
        return one("SELECT * FROM token WHERE id = :id", q -> q.bind("id", Hex.decode(id)));
    }

    public List<Token> findByHashes(List<String> ids) {
        return ids.isEmpty() ? List.of()
                : list("SELECT * FROM token WHERE id IN (<ids>)", q -> q.bindList("ids", ids.stream().map(Hex::decode).toList()));
    }

    /** Exact name match (search box), oldest first. */
    public List<Token> findByName(String name, int limit) {
        return list("SELECT * FROM token WHERE name = :name ORDER BY block_height LIMIT :n", q -> q.bind("name", name).bind("n", limit));
    }

    /** Newest boxes of a token whose spending transaction is looked up (a primary-key probe of box_spent each). */
    private static final int SPENT_BOXES = 1_000;

    /**
     * Newest transactions that created or spent boxes carrying the token, one per transaction. Created: walks the
     * token's boxes newest first in batches (a transaction paying many outputs has many boxes, so LIMIT on boxes is
     * not enough). Spent: only needed for a burn of the whole amount, which creates no box of the token; looked up for
     * the newest boxes only, so the burn of a long-idle box of a busy token can be missed.
     */
    public List<TransferTx> recentTransferTxs(String tokenId, int limit) {
        byte[] t = Hex.decode(tokenId);
        TreeSet<Long> gixes = new TreeSet<>(Comparator.reverseOrder());
        long before = Long.MAX_VALUE;
        for (int round = 0; round < 5 && gixes.size() < limit; round++) {
            long cursor = before;
            List<long[]> rows = jdbi.withHandle(h -> h.createQuery("SELECT ba.box_gix, b.tx_gix FROM box_asset ba JOIN box b ON b.gix = ba.box_gix"
                            + " WHERE ba.token_id = :t AND ba.box_gix < :before ORDER BY ba.box_gix DESC LIMIT :n")
                    .bind("t", t).bind("before", cursor).bind("n", limit * 4)
                    .map((rs, ctx) -> new long[]{rs.getLong(1), rs.getLong(2)})
                    .list());
            for (long[] r : rows) {
                if (gixes.size() < limit) {
                    gixes.add(r[1]);
                }
                before = r[0];
            }
            if (rows.size() < limit * 4) {
                break;
            }
        }
        gixes.addAll(jdbi.withHandle(h -> h.createQuery("SELECT DISTINCT s.tx_gix FROM"
                        + " (SELECT box_gix FROM box_asset WHERE token_id = :t ORDER BY box_gix DESC LIMIT :cap) r"
                        + " JOIN box_spent s ON s.box_gix = r.box_gix ORDER BY s.tx_gix DESC LIMIT :n")
                .bind("t", t).bind("cap", SPENT_BOXES).bind("n", limit)
                .mapTo(Long.class)
                .list()));
        List<Long> newest = gixes.stream().limit(limit).toList();
        if (newest.isEmpty()) {
            return List.of();
        }
        return jdbi.withHandle(h -> h.createQuery("SELECT gix AS tx_gix, LOWER(HEX(id)) AS tx_id, block_height, timestamp FROM tx"
                        + " WHERE gix IN (<gixes>) ORDER BY gix DESC")
                .bindList("gixes", newest)
                .map(ConstructorMapper.of(TransferTx.class))
                .list());
    }

    /** Token amounts received per (transaction, address) in the outputs of the given transactions. */
    public List<Flow> outputs(String tokenId, List<Long> txGixes) {
        return flows("SELECT b.tx_gix, sc.address, SUM(ba.amount) AS amount FROM box b"
                + " JOIN box_asset ba ON ba.box_gix = b.gix AND ba.token_id = :t JOIN script sc ON sc.id = b.script_id"
                + " WHERE b.tx_gix IN (<gixes>) GROUP BY b.tx_gix, sc.address", tokenId, txGixes);
    }

    /** Token amounts sent per (transaction, address) by the spent inputs of the given transactions. */
    public List<Flow> inputs(String tokenId, List<Long> txGixes) {
        return flows("SELECT i.tx_gix, sc.address, SUM(ba.amount) AS amount FROM tx_input i JOIN box b ON b.gix = i.box_gix"
                + " JOIN box_asset ba ON ba.box_gix = b.gix AND ba.token_id = :t JOIN script sc ON sc.id = b.script_id"
                + " WHERE i.tx_gix IN (<gixes>) AND i.data_input = 0 GROUP BY i.tx_gix, sc.address", tokenId, txGixes);
    }

    private List<Flow> flows(String sql, String tokenId, List<Long> txGixes) {
        return txGixes.isEmpty() ? List.of() : jdbi.withHandle(h -> h.createQuery(sql)
                .bind("t", Hex.decode(tokenId)).bindList("gixes", txGixes)
                .map(ConstructorMapper.of(Flow.class))
                .list());
    }

}
