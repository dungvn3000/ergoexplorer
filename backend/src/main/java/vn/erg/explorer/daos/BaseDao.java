package vn.erg.explorer.daos;

import io.ebean.BeanRepository;
import io.ebean.Database;

/** Same shape as the template's BaseDao, with the id type as a parameter (natural keys: hash, height, embedded). */
public abstract class BaseDao<K, M> extends BeanRepository<K, M> {

    public BaseDao(Class<M> type, Database database) {
        super(type, database);
    }

}
