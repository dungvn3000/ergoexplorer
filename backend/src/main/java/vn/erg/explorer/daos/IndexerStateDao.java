package vn.erg.explorer.daos;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jdbi.v3.core.Jdbi;
import vn.erg.explorer.models.IndexerState;

import java.util.Optional;

@Singleton
public class IndexerStateDao extends BaseDao<IndexerState> {

    @Inject
    public IndexerStateDao(Jdbi jdbi) {
        super(IndexerState.class, jdbi);
    }

    public Optional<IndexerState> find() {
        return one("SELECT * FROM indexer_state WHERE id = :id", q -> q.bind("id", IndexerState.SINGLETON_ID));
    }

    /** Last fully indexed height, 0 when the index is empty. */
    public long height() {
        return find().map(IndexerState::getHeight).orElse(0L);
    }

}
