package vn.erg.explorer.daos;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jdbi.v3.core.Jdbi;
import vn.erg.explorer.models.AddressBalance;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static vn.erg.explorer.utils.ErgoConstants.NANO;

@Singleton
public class AddressBalanceDao extends BaseDao<AddressBalance> {

    @Inject
    public AddressBalanceDao(Jdbi jdbi) {
        super(AddressBalance.class, jdbi);
    }

    public Optional<AddressBalance> findByScript(long scriptId) {
        return one("SELECT * FROM address_balance WHERE script_id = :s", q -> q.bind("s", scriptId));
    }

    /** Richest scripts (addresses) first. */
    public List<AddressBalance> richList(int page, int rowsPerPage) {
        return list("SELECT * FROM address_balance WHERE nano_erg > 0 ORDER BY nano_erg DESC LIMIT :limit OFFSET :offset",
                q -> q.bind("limit", rowsPerPage).bind("offset", (long) (page - 1) * rowsPerPage));
    }

    /**
     * Lower bounds (nanoERG, inclusive) of the balance buckets, richest first: ≥ 1M, 100k, 10k, 1k, 100, 10 and 1 ERG,
     * then everything else above zero. Bucket {@code i} holds balances in {@code [FLOORS[i], FLOORS[i-1])}.
     */
    public static final long[] BUCKET_FLOORS = {1_000_000 * NANO, 100_000 * NANO, 10_000 * NANO, 1_000 * NANO, 100 * NANO, 10 * NANO, NANO, 1};

    // CASE generated from BUCKET_FLOORS so the SQL and the labels can never disagree; the WHERE / SUM only read
    // nano_erg, so MySQL / MariaDB answer from idx_balance_rich (a range scan of the funded rows, no table reads)
    private static final String BUCKETS_SQL = "SELECT CASE "
            + IntStream.range(0, BUCKET_FLOORS.length - 1).mapToObj(i -> "WHEN nano_erg >= " + BUCKET_FLOORS[i] + " THEN " + i).collect(Collectors.joining(" "))
            + " ELSE " + (BUCKET_FLOORS.length - 1) + " END AS bucket, COUNT(*) AS addresses, SUM(nano_erg) AS nano_erg"
            + " FROM address_balance WHERE nano_erg > 0 GROUP BY bucket";

    /** Funded addresses per balance bucket: {@code [bucket index, addresses, nanoErg]}; empty buckets are absent. */
    public List<long[]> balanceBuckets() {
        return jdbi.withHandle(h -> h.createQuery(BUCKETS_SQL)
                .map((rs, ctx) -> new long[]{rs.getInt("bucket"), rs.getLong("addresses"), rs.getLong("nano_erg")})
                .list());
    }

    public int countFunded() {
        return count("SELECT COUNT(*) FROM address_balance WHERE nano_erg > 0", q -> {
        });
    }

}
