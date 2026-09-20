package vn.erg.explorer.daos;

import io.ebean.Database;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import vn.erg.explorer.models.IndexerState;
import vn.erg.explorer.models.query.QIndexerState;

import java.util.Optional;

@Singleton
public class IndexerStateDao extends BaseDao<Integer, IndexerState> {

    @Inject
    public IndexerStateDao(Database database) {
        super(IndexerState.class, database);
    }

    public Optional<IndexerState> find() {
        return Optional.ofNullable(new QIndexerState().id.eq(IndexerState.SINGLETON_ID).findOne());
    }

    /** Last fully indexed height, 0 when the index is empty. */
    public long height() {
        return find().map(IndexerState::getHeight).orElse(0L);
    }

}
