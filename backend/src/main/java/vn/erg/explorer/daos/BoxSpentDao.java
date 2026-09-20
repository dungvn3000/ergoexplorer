package vn.erg.explorer.daos;

import io.ebean.Database;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import vn.erg.explorer.models.BoxSpent;
import vn.erg.explorer.models.query.QBoxSpent;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Singleton
public class BoxSpentDao extends BaseDao<Long, BoxSpent> {

    @Inject
    public BoxSpentDao(Database database) {
        super(BoxSpent.class, database);
    }

    /** Spend records of the given boxes, by box gix (missing = unspent). */
    public Map<Long, BoxSpent> findByBoxGixes(List<Long> gixes) {
        if (gixes.isEmpty()) {
            return Map.of();
        }
        return new QBoxSpent().boxGix.in(gixes).findList().stream()
                .collect(Collectors.toMap(BoxSpent::getBoxGix, Function.identity()));
    }

}
