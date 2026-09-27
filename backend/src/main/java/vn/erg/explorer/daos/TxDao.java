package vn.erg.explorer.daos;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jdbi.v3.core.Jdbi;
import vn.erg.explorer.models.Tx;
import vn.erg.explorer.utils.Hex;

import java.util.List;
import java.util.Optional;

@Singleton
public class TxDao extends BaseDao<Tx> {

    @Inject
    public TxDao(Jdbi jdbi) {
        super(Tx.class, jdbi);
    }

    public Optional<Tx> findByHash(String id) {
        return one("SELECT * FROM tx WHERE id = :id", q -> q.bind("id", Hex.decode(id)));
    }

    public List<Tx> findByGixes(List<Long> gixes) {
        return gixes.isEmpty() ? List.of() : list("SELECT * FROM tx WHERE gix IN (<gixes>)", q -> q.bindList("gixes", gixes));
    }

    /** Transactions of a block in block order. */
    public List<Tx> findByBlock(long height) {
        return list("SELECT * FROM tx WHERE block_height = :h ORDER BY idx", q -> q.bind("h", height));
    }

    /** Transactions touching the script (address), newest first — a reverse range over the address_tx primary key. */
    public List<Tx> findByScript(long scriptId, int page, int rowsPerPage) {
        return list("SELECT t.* FROM address_tx a JOIN tx t ON t.gix = a.tx_gix WHERE a.script_id = :s ORDER BY a.tx_gix DESC LIMIT :limit OFFSET :offset",
                q -> q.bind("s", scriptId).bind("limit", rowsPerPage).bind("offset", (long) (page - 1) * rowsPerPage));
    }

}
