package vn.erg.explorer.daos;

import io.ebean.Database;
import io.ebean.SqlRow;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import vn.erg.explorer.models.Block;
import vn.erg.explorer.models.query.QBlock;
import vn.erg.explorer.utils.Hex;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Singleton
public class BlockDao extends BaseDao<Long, Block> {

    @Inject
    public BlockDao(Database database) {
        super(Block.class, database);
    }

    public Optional<Block> findByHeight(long height) {
        return Optional.ofNullable(new QBlock().height.eq(height).findOne());
    }

    public Optional<Block> findByHash(String id) {
        return Optional.ofNullable(new QBlock().id.eq(Hex.decode(id)).findOne());
    }

    /** Blocks with height in [from, to], newest first. */
    public List<Block> findRange(long from, long to) {
        return new QBlock().height.between(from, to).orderBy().height.desc().findList();
    }

    /** Miner script id -> number of blocks among the newest {@code blocks}, most first. */
    public Map<Long, Integer> minerShare(int blocks) {
        Map<Long, Integer> out = new LinkedHashMap<>();
        List<SqlRow> rows = db().sqlQuery("SELECT miner_script_id, COUNT(*) AS n FROM (SELECT miner_script_id FROM block ORDER BY height DESC LIMIT :n) x"
                + " GROUP BY miner_script_id ORDER BY n DESC").setParameter("n", blocks).findList();
        for (SqlRow r : rows) {
            out.put(r.getLong("miner_script_id"), r.getInteger("n"));
        }
        return out;
    }

}
