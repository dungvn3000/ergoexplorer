package vn.erg.explorer.daos;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jdbi.v3.core.Jdbi;
import vn.erg.explorer.models.BoxSpent;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Singleton
public class BoxSpentDao extends BaseDao<BoxSpent> {

    @Inject
    public BoxSpentDao(Jdbi jdbi) {
        super(BoxSpent.class, jdbi);
    }

    /** Spend records of the given boxes, by box gix (missing = unspent). */
    public Map<Long, BoxSpent> findByBoxGixes(List<Long> gixes) {
        if (gixes.isEmpty()) {
            return Map.of();
        }
        return list("SELECT * FROM box_spent WHERE box_gix IN (<gixes>)", q -> q.bindList("gixes", gixes)).stream()
                .collect(Collectors.toMap(BoxSpent::getBoxGix, Function.identity()));
    }

}
