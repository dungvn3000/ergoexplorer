# Ergo Explorer — frontend

Vue 3 + Quasar 2 single-page app (hash router) for the explorer. It calls the backend REST API in
`../backend` and nothing else: no accounts, no analytics, no cookies.

```bash
yarn                 # install
yarn dev             # http://localhost:9000, API from http://localhost:8080/api/v1 (services/Api.js)
yarn lint            # eslint
yarn build           # -> dist/spa, served by Caddy next to /api (see ../backend/deploy)
```

## Where things are

| path | role |
|---|---|
| `src/pages/` | one page per route: home, blocks, block, tx, box, address, tokens, token, rich list, mempool, charts, api, about, privacy |
| `src/components/` | `E*` explorer widgets (hash, address, ERG amount, token chip, box, raw JSON viewer), `AreaChart` (Chart.js) |
| `src/services/Explorer.js` | every API call; `ErgoUtils.js` formats nanoERG, difficulty, dates |
| `src/router/routes.js` | routes, all public |
| `public/llms.txt` | site summary for AI agents (REST + MCP) |
| `mockup/` | the original static HTML mockup the UI was built from |

Amounts arrive from the API in nanoERG and token amounts as raw integers; formatting happens in
`ErgoUtils.js`. The production `baseURL` is `/api/v1` on the same host, so CORS is only needed in dev
(the backend allows the Quasar dev ports by default).
