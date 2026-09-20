package vn.erg.explorer.daos;

import io.ebean.Database;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import vn.erg.explorer.models.Box;
import vn.erg.explorer.models.query.QBox;
import vn.erg.explorer.utils.Hex;

import java.util.List;
import java.util.Optional;

@Singleton
public class BoxDao extends BaseDao<Long, Box> {

    @Inject
    public BoxDao(Database database) {
        super(Box.class, database);
    }

    public Optional<Box> findByHash(String id) {
        return Optional.ofNullable(new QBox().id.eq(Hex.decode(id)).findOne());
    }

    public List<Box> findByGixes(List<Long> gixes) {
        return gixes.isEmpty() ? List.of() : new QBox().gix.in(gixes).findList();
    }

    /** Outputs of the given transactions, in (tx, output index) order. */
    public List<Box> findOutputs(List<Long> txGixes) {
        return txGixes.isEmpty() ? List.of() : new QBox().txGix.in(txGixes).orderBy().txGix.asc().idx.asc().findList();
    }

    /**
     * Unspent boxes of a script (address), newest first: walks (script_id, gix) backwards and probes
     * box_spent per row, so it stops as soon as the page is full.
     */
    public List<Box> findUnspent(long scriptId, int page, int rowsPerPage) {
        return db().findNative(Box.class, "SELECT b.* FROM box b WHERE b.script_id = :s AND NOT EXISTS (SELECT 1 FROM box_spent s WHERE s.box_gix = b.gix)"
                        + " ORDER BY b.gix DESC LIMIT :limit OFFSET :offset")
                .setParameter("s", scriptId).setParameter("limit", rowsPerPage).setParameter("offset", (long) (page - 1) * rowsPerPage).findList();
    }

}
