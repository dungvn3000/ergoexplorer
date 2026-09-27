package vn.erg.explorer.daos;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.reflect.ConstructorMapper;
import vn.erg.explorer.models.Token;
import vn.erg.explorer.utils.Hex;

import java.util.List;
import java.util.Optional;

@Singleton
public class TokenDao extends BaseDao<Token> {

    /** A box carrying the token: creating tx (hex id), block, receiving address, raw amount, tx timestamp. */
    public record Transfer(String txId, long blockHeight, String address, long amount, long timestamp) {
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

    /** Newest boxes carrying the token. */
    public List<Transfer> recentTransfers(String tokenId, int limit) {
        return jdbi.withHandle(h -> h.createQuery("SELECT LOWER(HEX(t.id)) AS tx_id, b.block_height, sc.address, ba.amount, t.timestamp FROM box_asset ba"
                        + " JOIN box b ON b.gix = ba.box_gix JOIN script sc ON sc.id = b.script_id JOIN tx t ON t.gix = b.tx_gix"
                        + " WHERE ba.token_id = :t ORDER BY ba.box_gix DESC LIMIT :n")
                .bind("t", Hex.decode(tokenId)).bind("n", limit)
                .map(ConstructorMapper.of(Transfer.class))
                .list());
    }

}
