package vn.erg.explorer.daos;

import io.ebean.Database;
import io.ebean.SqlRow;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import vn.erg.explorer.models.Token;
import vn.erg.explorer.models.query.QToken;
import vn.erg.explorer.utils.Hex;

import java.util.List;
import java.util.Optional;

@Singleton
public class TokenDao extends BaseDao<byte[], Token> {

    @Inject
    public TokenDao(Database database) {
        super(Token.class, database);
    }

    public Optional<Token> findByHash(String id) {
        return Optional.ofNullable(new QToken().id.eq(Hex.decode(id)).findOne());
    }

    public List<Token> findByHashes(List<String> ids) {
        return ids.isEmpty() ? List.of() : new QToken().id.in(ids.stream().map(Hex::decode).toList()).findList();
    }

    /** Exact name match (search box), oldest first. */
    public List<Token> findByName(String name, int limit) {
        return new QToken().name.eq(name).orderBy().blockHeight.asc().setMaxRows(limit).findList();
    }

    /** Rows of tx id (hex), block_height, address, amount, timestamp — newest boxes carrying the token. */
    public List<SqlRow> recentTransfers(String tokenId, int limit) {
        return db().sqlQuery("SELECT HEX(t.id) AS tx_id, b.block_height, sc.address, ba.amount, t.timestamp FROM box_asset ba"
                        + " JOIN box b ON b.gix = ba.box_gix JOIN script sc ON sc.id = b.script_id JOIN tx t ON t.gix = b.tx_gix"
                        + " WHERE ba.token_id = :t ORDER BY ba.box_gix DESC LIMIT :n")
                .setParameter("t", Hex.decode(tokenId)).setParameter("n", limit).findList();
    }

}
