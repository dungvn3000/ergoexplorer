package vn.erg.explorer.daos;

import io.ebean.Database;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import vn.erg.explorer.models.TokenHolder;
import vn.erg.explorer.models.TokenHolderId;
import vn.erg.explorer.models.query.QTokenHolder;
import vn.erg.explorer.utils.Hex;

import java.util.List;

@Singleton
public class TokenHolderDao extends BaseDao<TokenHolderId, TokenHolder> {

    @Inject
    public TokenHolderDao(Database database) {
        super(TokenHolder.class, database);
    }

    /** All tokens a script (address) holds, biggest raw amount first. */
    public List<TokenHolder> findByScript(long scriptId) {
        return new QTokenHolder().id.scriptId.eq(scriptId).amount.gt(0).orderBy().amount.desc().findList();
    }

    /** Holders of a token, biggest first (rich list page). */
    public List<TokenHolder> holders(String tokenId, int page, int rowsPerPage) {
        return new QTokenHolder().id.tokenId.eq(Hex.decode(tokenId)).amount.gt(0).orderBy().amount.desc()
                .setFirstRow((page - 1) * rowsPerPage).setMaxRows(rowsPerPage).findList();
    }

    public int holderCount(String tokenId) {
        return new QTokenHolder().id.tokenId.eq(Hex.decode(tokenId)).amount.gt(0).findCount();
    }

}
