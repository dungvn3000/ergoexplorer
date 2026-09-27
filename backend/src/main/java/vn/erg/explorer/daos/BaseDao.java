package vn.erg.explorer.daos;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.Query;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * One DAO per table on top of Jdbi. Rows are mapped onto the table's bean ({@code models/}) by
 * Jdbi's bean mapper: snake_case columns to camelCase properties, BINARY to {@code byte[]}. Every call
 * borrows a connection from the pool and returns it (reads only, autocommit).
 */
public abstract class BaseDao<M> {

    protected final Jdbi jdbi;

    private final Class<M> type;

    protected BaseDao(Class<M> type, Jdbi jdbi) {
        this.type = type;
        this.jdbi = jdbi;
    }

    /** Rows of {@code sql} as beans; {@code bind} sets the named parameters. */
    protected List<M> list(String sql, Consumer<Query> bind) {
        return jdbi.withHandle(h -> {
            Query q = h.createQuery(sql);
            bind.accept(q);
            return q.mapToBean(type).list();
        });
    }

    /** The single row of {@code sql}, empty when none (throws on more than one). */
    protected Optional<M> one(String sql, Consumer<Query> bind) {
        return jdbi.withHandle(h -> {
            Query q = h.createQuery(sql);
            bind.accept(q);
            return q.mapToBean(type).findOne();
        });
    }

    /** A single integer (count) query. */
    protected int count(String sql, Consumer<Query> bind) {
        return jdbi.withHandle(h -> {
            Query q = h.createQuery(sql);
            bind.accept(q);
            return q.mapTo(int.class).one();
        });
    }

}
