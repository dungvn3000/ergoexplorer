package vn.erg.explorer.daos;

import io.ebean.Database;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import vn.erg.explorer.models.Script;
import vn.erg.explorer.models.query.QScript;
import vn.erg.explorer.utils.Sha256;

import java.util.List;
import java.util.Optional;

@Singleton
public class ScriptDao extends BaseDao<Long, Script> {

    @Inject
    public ScriptDao(Database database) {
        super(Script.class, database);
    }

    public Optional<Script> findByAddress(String address) {
        return Optional.ofNullable(new QScript().addrHash.eq(Sha256.of(address)).findOne());
    }

    public Optional<Script> findById(long id) {
        return Optional.ofNullable(new QScript().id.eq(id).findOne());
    }

    public List<Script> findByIds(List<Long> ids) {
        return ids.isEmpty() ? List.of() : new QScript().id.in(ids).findList();
    }

}
