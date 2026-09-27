package vn.erg.explorer.daos;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jdbi.v3.core.Jdbi;
import vn.erg.explorer.models.Block;
import vn.erg.explorer.utils.Hex;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Singleton
public class BlockDao extends BaseDao<Block> {

    @Inject
    public BlockDao(Jdbi jdbi) {
        super(Block.class, jdbi);
    }

    public Optional<Block> findByHeight(long height) {
        return one("SELECT * FROM block WHERE height = :h", q -> q.bind("h", height));
    }

    public Optional<Block> findByHash(String id) {
        return one("SELECT * FROM block WHERE id = :id", q -> q.bind("id", Hex.decode(id)));
    }

    /** Blocks with height in [from, to], newest first. */
    public List<Block> findRange(long from, long to) {
        return list("SELECT * FROM block WHERE height BETWEEN :from AND :to ORDER BY height DESC", q -> q.bind("from", from).bind("to", to));
    }

    /** Miner script id -> number of blocks among the newest {@code blocks}, most first. */
    public Map<Long, Integer> minerShare(int blocks) {
        return jdbi.withHandle(h -> {
            Map<Long, Integer> out = new LinkedHashMap<>();
            h.createQuery("SELECT miner_script_id, COUNT(*) AS n FROM (SELECT miner_script_id FROM block ORDER BY height DESC LIMIT :n) x"
                            + " GROUP BY miner_script_id ORDER BY n DESC")
                    .bind("n", blocks)
                    .map((rs, ctx) -> Map.entry(rs.getLong("miner_script_id"), rs.getInt("n")))
                    .forEach(e -> out.put(e.getKey(), e.getValue()));
            return out;
        });
    }

}
