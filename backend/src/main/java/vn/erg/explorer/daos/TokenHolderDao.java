package vn.erg.explorer.daos;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jdbi.v3.core.Jdbi;
import vn.erg.explorer.models.TokenHolder;
import vn.erg.explorer.utils.Hex;

import java.util.List;

@Singleton
public class TokenHolderDao extends BaseDao<TokenHolder> {

    @Inject
    public TokenHolderDao(Jdbi jdbi) {
        super(TokenHolder.class, jdbi);
    }

    /** All tokens a script (address) holds, biggest raw amount first. */
    public List<TokenHolder> findByScript(long scriptId) {
        return list("SELECT * FROM token_holder WHERE script_id = :s AND amount > 0 ORDER BY amount DESC", q -> q.bind("s", scriptId));
    }

    /** Holders of a token, biggest first (rich list page). */
    public List<TokenHolder> holders(String tokenId, int page, int rowsPerPage) {
        return list("SELECT * FROM token_holder WHERE token_id = :t AND amount > 0 ORDER BY amount DESC LIMIT :limit OFFSET :offset",
                q -> q.bind("t", Hex.decode(tokenId)).bind("limit", rowsPerPage).bind("offset", (long) (page - 1) * rowsPerPage));
    }

    public int holderCount(String tokenId) {
        return count("SELECT COUNT(*) FROM token_holder WHERE token_id = :t AND amount > 0", q -> q.bind("t", Hex.decode(tokenId)));
    }

}
