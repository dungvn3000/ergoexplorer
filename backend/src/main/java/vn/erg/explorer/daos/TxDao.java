package vn.erg.explorer.daos;

import io.ebean.Database;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import vn.erg.explorer.models.Tx;
import vn.erg.explorer.models.query.QTx;
import vn.erg.explorer.utils.Hex;

import java.util.List;
import java.util.Optional;

@Singleton
public class TxDao extends BaseDao<Long, Tx> {

    /** Every transaction that created a box for the script (address) or spent one of its boxes. */
    @Inject
    public TxDao(Database database) {
        super(Tx.class, database);
    }

    public Optional<Tx> findByHash(String id) {
        return Optional.ofNullable(new QTx().id.eq(Hex.decode(id)).findOne());
    }

    public List<Tx> findByGixes(List<Long> gixes) {
        return gixes.isEmpty() ? List.of() : new QTx().gix.in(gixes).findList();
    }

    /** Transactions of a block in block order. */
    public List<Tx> findByBlock(long height) {
        return new QTx().blockHeight.eq(height).orderBy().idx.asc().findList();
    }

    /** Transactions touching the script (address), newest first — a reverse range over the address_tx primary key. */
    public List<Tx> findByScript(long scriptId, int page, int rowsPerPage) {
        return db().findNative(Tx.class, "SELECT t.* FROM address_tx a JOIN tx t ON t.gix = a.tx_gix WHERE a.script_id = :s"
                        + " ORDER BY a.tx_gix DESC LIMIT :limit OFFSET :offset")
                .setParameter("s", scriptId)
                .setParameter("limit", rowsPerPage)
                .setParameter("offset", (long) (page - 1) * rowsPerPage)
                .findList();
    }

}
