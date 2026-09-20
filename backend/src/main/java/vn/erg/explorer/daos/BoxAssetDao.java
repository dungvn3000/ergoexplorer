package vn.erg.explorer.daos;

import io.ebean.Database;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import vn.erg.explorer.models.BoxAsset;
import vn.erg.explorer.models.BoxAssetId;
import vn.erg.explorer.models.query.QBoxAsset;

import java.util.List;

@Singleton
public class BoxAssetDao extends BaseDao<BoxAssetId, BoxAsset> {

    @Inject
    public BoxAssetDao(Database database) {
        super(BoxAsset.class, database);
    }

    /** Assets of the given boxes, in (box, asset index) order. */
    public List<BoxAsset> findByBoxGixes(List<Long> gixes) {
        return gixes.isEmpty() ? List.of() : new QBoxAsset().id.boxGix.in(gixes).orderBy().id.boxGix.asc().id.idx.asc().findList();
    }

}
