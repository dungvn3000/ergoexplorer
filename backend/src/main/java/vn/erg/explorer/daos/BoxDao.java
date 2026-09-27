package vn.erg.explorer.daos;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jdbi.v3.core.Jdbi;
import vn.erg.explorer.models.Box;
import vn.erg.explorer.utils.Hex;

import java.util.List;
import java.util.Optional;

@Singleton
public class BoxDao extends BaseDao<Box> {

    @Inject
    public BoxDao(Jdbi jdbi) {
        super(Box.class, jdbi);
    }

    public Optional<Box> findByHash(String id) {
        return one("SELECT * FROM box WHERE id = :id", q -> q.bind("id", Hex.decode(id)));
    }

    public List<Box> findByGixes(List<Long> gixes) {
        return gixes.isEmpty() ? List.of() : list("SELECT * FROM box WHERE gix IN (<gixes>)", q -> q.bindList("gixes", gixes));
    }

    /** Outputs of the given transactions, in (tx, output index) order. */
    public List<Box> findOutputs(List<Long> txGixes) {
        return txGixes.isEmpty() ? List.of()
                : list("SELECT * FROM box WHERE tx_gix IN (<gixes>) ORDER BY tx_gix, idx", q -> q.bindList("gixes", txGixes));
    }

    /** Unspent boxes of a script (address), newest first: a reverse range over the box_unspent primary key. */
    public List<Box> findUnspent(long scriptId, int page, int rowsPerPage) {
        return list("SELECT b.* FROM box_unspent u JOIN box b ON b.gix = u.gix WHERE u.script_id = :s"
                        + " ORDER BY u.gix DESC LIMIT :limit OFFSET :offset",
                q -> q.bind("s", scriptId).bind("limit", rowsPerPage).bind("offset", (long) (page - 1) * rowsPerPage));
    }

}
