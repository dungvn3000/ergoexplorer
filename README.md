# Ergo Explorer

Fan-made block explorer for the [Ergo](https://ergoplatform.org) mainnet, live at
**[explorer.erg.vn](https://explorer.erg.vn)**. It indexes the chain from Ergo full nodes into MySQL and
serves blocks, transactions, boxes, addresses, tokens, rich lists and charts through a web UI, a JSON REST
API and an [MCP](https://modelcontextprotocol.io) server for AI agents.

> This is an independent community project, not affiliated with or endorsed by the Ergo Foundation or the
> Ergo Platform team. The official explorer is [explorer.ergoplatform.com](https://explorer.ergoplatform.com/).

## Layout

| directory | what | docs |
|---|---|---|
| [`backend/`](backend) | Java 21 · Jooby 4 · Guice · Ebean · MySQL — REST API, chain indexer, MCP server | [backend/README.md](backend/README.md) |
| [`frontend/`](frontend) | Vue 3 · Quasar 2 single-page app | [frontend/README.md](frontend/README.md) |

## Quick start

Requirements: JDK 21, Maven 3, Node 20+ with Yarn, MySQL 8 or MariaDB 10.6+, and an Ergo full node with
the extra indexer enabled (`ergo.node.extraIndex = true`) or access to a public one.

The `backend/conf/` and `backend/deploy/` directories (application config, logback, MySQL tuning, systemd and
Caddy files) are site-specific and not part of this repository. Create `backend/conf/application.conf` with
the keys listed in [backend/README.md](backend/README.md#run) before starting the backend.

```bash
# database (dev defaults: root / root)
mysql -uroot -e "CREATE DATABASE ergo CHARACTER SET utf8mb4"

# backend on http://localhost:8080 (Flyway creates the schema on first start)
cd backend
mvn process-classes && mvn jooby:run

# frontend on http://localhost:9000, proxied to the backend
cd ../frontend
yarn && yarn dev
```

Point `ergo.nodes` in your `application.conf` at your node(s) and set `indexer.enabled = true`
to build the chain index; the explorer answers from the node while the first sync runs.

## API and MCP

- REST: `https://explorer.erg.vn/api/v1/...` — endpoints in [backend/README.md](backend/README.md#endpoints).
- MCP (Streamable HTTP, stateless, read-only): `https://explorer.erg.vn/api/mcp`

```bash
claude mcp add --transport http ergo-explorer https://explorer.erg.vn/api/mcp
```

Every MCP answer carries an `asOf` block (chain height, index height, timestamp, response id) and a
`mutable` flag, so agents know whether a balance is still current. See
[llms.txt](frontend/public/llms.txt) for the agent-facing summary.

## Contributing

Bugs, wrong figures and missing pool labels are best reported as issues with the block, transaction or
address in question. Pull requests are welcome; run `yarn lint` in `frontend/` and `mvn package` in
`backend/` before opening one.

## License

[MIT](LICENSE) © 2026 Nguyen Duc Dung
