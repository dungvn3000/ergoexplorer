package vn.erg.explorer.daos;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jdbi.v3.core.Jdbi;
import vn.erg.explorer.models.Script;
import vn.erg.explorer.utils.Sha256;

import java.util.List;
import java.util.Optional;

@Singleton
public class ScriptDao extends BaseDao<Script> {

    @Inject
    public ScriptDao(Jdbi jdbi) {
        super(Script.class, jdbi);
    }

    public Optional<Script> findByAddress(String address) {
        return one("SELECT * FROM script WHERE addr_hash = :h", q -> q.bind("h", Sha256.of(address)));
    }

    public Optional<Script> findById(long id) {
        return one("SELECT * FROM script WHERE id = :id", q -> q.bind("id", id));
    }

    public List<Script> findByIds(List<Long> ids) {
        return ids.isEmpty() ? List.of() : list("SELECT * FROM script WHERE id IN (<ids>)", q -> q.bindList("ids", ids));
    }

}
