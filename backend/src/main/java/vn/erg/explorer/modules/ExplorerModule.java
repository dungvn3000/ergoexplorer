package vn.erg.explorer.modules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.typesafe.config.Config;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.ebean.Database;
import io.jooby.Environment;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import vn.erg.explorer.index.ChainIndexer;
import vn.erg.explorer.index.MempoolSampler;
import vn.erg.explorer.node.NodeClient;

import javax.sql.DataSource;
import java.time.Duration;

@Slf4j
public class ExplorerModule extends AbstractModule {

    private final Environment env;
    private final ObjectMapper objectMapper;

    /**
     * @param env          the application environment; Jooby's own Guice module already binds it, its {@code Config}
     *                     and every service registered on the app (including the {@link ObjectMapper}), so this module
     *                     must not bind them again
     * @param objectMapper the app-wide mapper (registered as a Jooby service in {@code Main}), shared with the node client
     */
    public ExplorerModule(Environment env, ObjectMapper objectMapper) {
        this.env = env;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void configure() {
        Config config = env.getConfig();

        NodeClient nodeClient = new NodeClient(
                config.getStringList("ergo.nodes"),
                Duration.ofSeconds(config.getLong("ergo.timeoutSeconds")),
                objectMapper);
        bind(NodeClient.class).toInstance(nodeClient);
        log.info("Ergo nodes: {}", config.getStringList("ergo.nodes"));

        // MySQL chain index (Flyway keeps the schema current on every start). V1__schema.sql is the complete
        // schema; a database that already has the tables but no flyway_schema_history (an index built before
        // the baseline, after dropping its old history table) is baselined at version 1 instead of re-created.
        DataSource ds = initDataSource(config);
        bind(DataSource.class).toInstance(ds);
        Flyway.configure().dataSource(ds).baselineOnMigrate(true).baselineVersion("1").load().migrate();
        Database database = Database.builder().dataSource(ds).build();
        bind(Database.class).toInstance(database);

        if (config.getBoolean("indexer.enabled")) {
            bind(ChainIndexer.class).asEagerSingleton();
            bind(MempoolSampler.class).asEagerSingleton();
        }
    }

    private DataSource initDataSource(Config config) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.getString("db.connection.url"));
        hikariConfig.setUsername(config.getString("db.connection.username"));
        hikariConfig.setPassword(config.getString("db.connection.password"));
        hikariConfig.setMaximumPoolSize(16);
        hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        return new HikariDataSource(hikariConfig);
    }

}
