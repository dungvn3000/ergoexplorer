package vn.erg.explorer.daos;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jdbi.v3.core.Jdbi;
import vn.erg.explorer.models.BoxAsset;

import java.util.List;

@Singleton
public class BoxAssetDao extends BaseDao<BoxAsset> {

    @Inject
    public BoxAssetDao(Jdbi jdbi) {
        super(BoxAsset.class, jdbi);
    }

    /** Assets of the given boxes, in (box, asset index) order. */
    public List<BoxAsset> findByBoxGixes(List<Long> gixes) {
        return gixes.isEmpty() ? List.of()
                : list("SELECT * FROM box_asset WHERE box_gix IN (<gixes>) ORDER BY box_gix, idx", q -> q.bindList("gixes", gixes));
    }

}
