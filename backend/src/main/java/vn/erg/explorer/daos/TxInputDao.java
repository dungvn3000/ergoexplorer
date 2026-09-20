package vn.erg.explorer.daos;

import io.ebean.Database;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import vn.erg.explorer.models.TxInput;
import vn.erg.explorer.models.TxInputId;
import vn.erg.explorer.models.query.QTxInput;

import java.util.List;

@Singleton
public class TxInputDao extends BaseDao<TxInputId, TxInput> {

    @Inject
    public TxInputDao(Database database) {
        super(TxInput.class, database);
    }

    /** Inputs and data inputs of the given transactions, in (tx, kind, index) order. */
    public List<TxInput> findByTxGixes(List<Long> txGixes) {
        return txGixes.isEmpty() ? List.of()
                : new QTxInput().id.txGix.in(txGixes).orderBy().id.txGix.asc().id.dataInput.asc().id.idx.asc().findList();
    }

}
