# Ergo Explorer — frontend

Vite + React 19 + React Router 7 + Tailwind CSS v4, charts on Chart.js. Talks to the explorer backend (`../backend`, Jooby) at `/api/v1`.

## Run

```bash
pnpm install
pnpm dev          # http://localhost:5176 — /api/v1 is proxied to the backend on http://localhost:8080
pnpm build        # static files in dist/
pnpm lint         # ESLint (react, react-hooks, react-refresh); lint:fix to auto-fix
pnpm format       # Prettier; format:check in CI
```

In production, serve `dist/` from the same host that reverse-proxies `/api` to the backend
(see `../backend/deploy/Caddyfile`). Routes are plain paths (`/block/…`), so the server must answer unknown paths with
`index.html` — the Caddyfile already does (`try_files {path} /index.html`). Old hash links (`/#/block/…`) are redirected
to the plain path on load.

## Layout

```
index.html                  sets the theme before first paint; mounts #app
src/main.jsx                route table (createBrowserRouter), redirect of old /#/ links
src/charts-meta.js          chart catalogue and series loading
src/context.jsx             AppProvider: theme, toast, tip height (one context each, see app-context.js)
src/app-context.js          contexts + hooks: useTheme, useNotify, useToast, useHeight, useSetHeight
src/labels.js               address labels and token display names
src/hooks.js                useApi (loading / error / 404, stale-answer guard, keepPrevious for paged lists), useInterval
src/api.js                  fetch client for /api/v1 (404 → null)
src/components/Layout.jsx   header (nav, search, theme toggle), footer, toast, scroll restoration
src/components/ui.jsx       Panel, KvCard/Kv/KvRow, Crumbs, Table, Stat, Pill, CopyButton, AddressLink, TokenChip, Pager, Tabs …
src/components/Chart.jsx      lazy entry for charts: Chart.js is loaded in its own chunk on first use
src/components/LineChart.jsx  Chart.js area chart (react-chartjs-2), themed from CSS variables
src/components/widgets.jsx  QrCode, Loaded (page states), ErrorBox, Missing
src/components/icons.jsx    brand logo (icons come from lucide-react)
src/pages/*.jsx             one file per page
src/format.js               number, ERG, token, date formatting
src/styles.css              Tailwind theme (light/dark tokens) and component classes (.panel, .btn, .pill, .tbl, .kv …)
```

A page reads its params, calls `useApi(fetcher, deps)` and renders through
`<Loaded q={q} kind="block" id={id}>{data => …}</Loaded>`, which handles the spinner, the error with retry
and the not-found state. Parts that can be slow (home panels, rich-list shares, chart cards, node status) have their own
`useApi` call and render as soon as they answer.

## Notes

- Fonts (IBM Plex Sans / Mono) are self-hosted from `@fontsource`, imported in `main.jsx`: the site makes no third-party
  requests, as the Privacy page states. Keep it that way when adding assets.

- All on-chain text (token names, descriptions, register values) is rendered as React text, never as HTML.
  The only `dangerouslySetInnerHTML` is the QR code SVG, generated locally by the `qrcode` library.
- Charts drop the last daily point: it is the day the index is still working through, so its totals are partial.
- Home and Mempool refresh on a timer; paged lists keep the previous page on screen while the next loads,
  so the scroll position survives paging.
