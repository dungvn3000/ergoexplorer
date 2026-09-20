package vn.erg.explorer.db;

import com.typesafe.config.Config;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import vn.erg.explorer.services.ChainState;

/** Whether the MySQL index can answer a question, or the node must be asked. */
@Singleton
public class IndexStatus {

    private final ChainRepository repo;
    private final ChainState chain;
    private final boolean fromGenesis;

    @Inject
    public IndexStatus(ChainRepository repo, ChainState chain, Config config) {
        this.repo = repo;
        this.chain = chain;
        this.fromGenesis = config.getLong("indexer.startHeight") <= 1;
    }

    public long indexedHeight() {
        return repo.indexedHeight();
    }

    /** The block at this height is in the DB. */
    public boolean has(long height) {
        return height <= indexedHeight();
    }

    /** Address balances and token holders are only right when every block since genesis is indexed. */
    public boolean isSynced() {
        return fromGenesis && chain.height() - indexedHeight() <= 3;
    }

}
