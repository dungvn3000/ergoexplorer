package vn.erg.explorer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jooby.Environment;
import io.jooby.Jooby;
import io.jooby.ServerOptions;
import io.jooby.handler.Cors;
import io.jooby.guice.GuiceModule;
import io.jooby.handler.CorsHandler;
import io.jooby.mcp.McpInspectorModule;
import io.jooby.mcp.McpModule;
import io.jooby.mcp.jackson2.McpJackson2Module;
import io.jooby.netty.NettyServer;
import vn.erg.explorer.controllers.*;
import vn.erg.explorer.mcp.ExplorerToolsMcp_;
import vn.erg.explorer.modules.ExplorerModule;
import vn.erg.explorer.node.NodeException;
import vn.erg.explorer.web.JsonModule;
import vn.erg.explorer.web.JsonResult;

import java.util.concurrent.Executors;

import static io.jooby.ExecutionMode.WORKER;

/**
 * Ergo Explorer API: a stateless aggregation + cache layer in front of Ergo full nodes
 * (extra indexer enabled). No database — every answer is built from the node's REST API.
 */
public class Main extends Jooby {

    {
        // Every request may fan out to many node calls (one per tx/box); virtual threads keep that cheap.
        setWorker(Executors.newVirtualThreadPerTaskExecutor());

        Environment env = getEnvironment();

        Cors cors = new Cors()
                .setOrigin(env.getConfig().getStringList("cors.origins").toArray(String[]::new))
                .setHeaders("*")
                .setMethods("GET", "POST");
        use(new CorsHandler(cors));

        // One ObjectMapper for everything: REST answers (JsonModule), MCP (McpJackson2Module) and node JSON parsing.
        // Registered as a Jooby service, so Jooby's Guice module binds it and services can inject it.
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        getServices().put(ObjectMapper.class, objectMapper);

        // Guice wires services and controllers (every endpoint is public and read-only: no auth filters);
        // JsonModule renders JsonResult and maps its errorCode to the HTTP status
        install(new GuiceModule(new ExplorerModule(env, objectMapper)));
        install(new JsonModule());

        // MCP server for AI agents (tools in mcp/ExplorerTools, endpoint + transport in application.conf).
        // Stateless streamable HTTP: one POST per call, nothing kept per session — fits the no-tracking policy.
        install(new McpJackson2Module());
        install(new McpModule(new ExplorerToolsMcp_()).transport(McpModule.Transport.STATELESS_STREAMABLE_HTTP));
        if (env.isActive("dev")) {
            install(new McpInspectorModule().path("/mcp-inspector").defaultServer("ergo-explorer").autoConnect(true));
        }

        // Node unreachable / bad answer -> 502 with a JSON body instead of a stack trace
        error(NodeException.class, (ctx, cause, code) -> {
            ctx.setResponseCode(502);
            ctx.render(JsonResult.error(cause.getMessage(), 502));
        });

        mvc(new InfoCtr_());
        mvc(new BlockCtr_());
        mvc(new TransactionCtr_());
        mvc(new AddressCtr_());
        mvc(new TokenCtr_());
        mvc(new MempoolCtr_());
        mvc(new SearchCtr_());
        mvc(new BoxCtr_());
        mvc(new RichListCtr_());
        mvc(new ChartCtr_());
    }

    public static void main(String[] args) {
        var options = new ServerOptions().setMaxRequestSize(1048576);
        runApp(args, new NettyServer(options), WORKER, Main::new);
    }

}
