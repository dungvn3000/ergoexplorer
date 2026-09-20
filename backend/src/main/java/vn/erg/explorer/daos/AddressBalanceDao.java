package vn.erg.explorer.daos;

import io.ebean.Database;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import vn.erg.explorer.models.AddressBalance;
import vn.erg.explorer.models.query.QAddressBalance;

import java.util.List;
import java.util.Optional;

@Singleton
public class AddressBalanceDao extends BaseDao<Long, AddressBalance> {

    @Inject
    public AddressBalanceDao(Database database) {
        super(AddressBalance.class, database);
    }

    public Optional<AddressBalance> findByScript(long scriptId) {
        return Optional.ofNullable(new QAddressBalance().scriptId.eq(scriptId).findOne());
    }

    /** Richest scripts (addresses) first. */
    public List<AddressBalance> richList(int page, int rowsPerPage) {
        return new QAddressBalance().nanoErg.gt(0).orderBy().nanoErg.desc()
                .setFirstRow((page - 1) * rowsPerPage).setMaxRows(rowsPerPage).findList();
    }

    public int countFunded() {
        return new QAddressBalance().nanoErg.gt(0).findCount();
    }

}
