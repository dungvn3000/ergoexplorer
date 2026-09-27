package vn.erg.explorer.daos;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jdbi.v3.core.Jdbi;
import vn.erg.explorer.models.TxInput;

import java.util.List;

@Singleton
public class TxInputDao extends BaseDao<TxInput> {

    @Inject
    public TxInputDao(Jdbi jdbi) {
        super(TxInput.class, jdbi);
    }

    /** Inputs and data inputs of the given transactions, in (tx, kind, index) order. */
    public List<TxInput> findByTxGixes(List<Long> txGixes) {
        return txGixes.isEmpty() ? List.of()
                : list("SELECT * FROM tx_input WHERE tx_gix IN (<gixes>) ORDER BY tx_gix, data_input, idx", q -> q.bindList("gixes", txGixes));
    }

}
