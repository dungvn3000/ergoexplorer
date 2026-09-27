package vn.erg.explorer.modules;

import tools.jackson.databind.json.JsonMapper;
import com.google.inject.AbstractModule;
import com.typesafe.config.Config;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.jooby.Environment;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.jdbi.v3.core.Jdbi;
import vn.erg.explorer.index.BlockSizeRepair;
import vn.erg.explorer.index.ChainIndexer;
import vn.erg.explorer.index.MempoolSampler;
import vn.erg.explorer.node.NodeClient;

import javax.sql.DataSource;
import java.time.Duration;

@Slf4j
public class ExplorerModule extends AbstractModule {

    private final Environment env;
    private final JsonMapper objectMapper;

    /**
     * @param env          the application environment; Jooby's own Guice module already binds it, its {@code Config}
     *                     and every service registered on the app (including the {@link JsonMapper}), so this module
     *                     must not bind them again
     * @param objectMapper the app-wide mapper (registered as a Jooby service in {@code Main}), shared with the node client
     */
    public ExplorerModule(Environment env, JsonMapper objectMapper) {
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

        // MySQL chain index (Flyway keeps the schema current on every start)
        DataSource ds = initDataSource(config);
        bind(DataSource.class).toInstance(ds);
        Flyway.configure().dataSource(ds).load().migrate();
        // Jdbi over the same pool for the read side (daos/, ChartService); the indexer writes through the DataSource
        bind(Jdbi.class).toInstance(Jdbi.create(ds));

        if (config.getBoolean("indexer.enabled")) {
            bind(ChainIndexer.class).asEagerSingleton();
            bind(MempoolSampler.class).asEagerSingleton();
            // on by default: application.conf is not versioned, so a deployed one may predate the key
            if (!config.hasPath("indexer.sizeRepair.enabled") || config.getBoolean("indexer.sizeRepair.enabled")) {
                bind(BlockSizeRepair.class).asEagerSingleton();
            }
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
