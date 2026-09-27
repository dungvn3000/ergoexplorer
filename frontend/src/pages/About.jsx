// About: what the explorer is, how it is built and where the data comes from (content carried over from the v1 frontend).
import { getInfo } from '../api.js'
import { useApi } from '../hooks.js'
import { Panel, PanelHead, Aside, Crumbs, PageHead, Sub, Pill, Stats, Stat, ExternalLink } from '../components/ui.jsx'
import { fmtInt, bytes } from '../format.js'

const STACK = [
  {
    title: 'Frontend',
    subtitle: 'Single-page app, history routes',
    items: [
      { name: 'React', version: '19.3', url: 'https://react.dev', role: 'UI library (function components, hooks)' },
      { name: 'React Router', version: '7.18', url: 'https://reactrouter.com', role: 'Routing (createBrowserRouter, scroll restoration)' },
      { name: 'Tailwind CSS', version: '4.3', url: 'https://tailwindcss.com', role: 'Styling: theme tokens for light and dark, component classes' },
      { name: 'Vite', version: '7.3', url: 'https://vite.dev', role: 'Dev server and bundler' },
      { name: 'Chart.js', version: '4.5', url: 'https://www.chartjs.org', role: 'Area charts (react-chartjs-2, date-fns adapter), loaded on demand' },
      { name: 'date-fns', version: '4.4', url: 'https://date-fns.org', role: 'Time axis of the charts' },
      { name: 'qrcode', version: '1.5', url: 'https://github.com/soldair/node-qrcode', role: 'Address QR codes' },
      { name: 'Lucide', version: '1', url: 'https://lucide.dev', role: 'Icons (lucide-react), only the ones used are bundled' },
      { name: 'IBM Plex (Fontsource)', version: '5.3', url: 'https://fontsource.org', role: 'Sans and Mono fonts, self-hosted: no request leaves the site' },
    ],
  },
  {
    title: 'Backend',
    subtitle: 'REST API and chain indexer, one Java process',
    items: [
      { name: 'Java', version: '21', url: 'https://openjdk.org/projects/jdk/21/', role: 'Virtual threads for node fan-out' },
      { name: 'Jooby', version: '4.5', url: 'https://jooby.io', role: 'Web framework on Netty, MVC controllers' },
      { name: 'Guice', version: '7', url: 'https://github.com/google/guice', role: 'Dependency injection' },
      { name: 'Jdbi', version: '3.54', url: 'https://jdbi.org', role: 'SQL data access: plain queries mapped onto beans, one DAO per table' },
      { name: 'Flyway', version: '11', url: 'https://flywaydb.org', role: 'Schema migrations' },
      { name: 'HikariCP', version: '6', url: 'https://github.com/brettwooldridge/HikariCP', role: 'JDBC connection pool' },
      { name: 'Caffeine', version: '3', url: 'https://github.com/ben-manes/caffeine', role: 'In-memory caches (blocks, tokens, stats)' },
      { name: 'Bouncy Castle', version: '1.86', url: 'https://www.bouncycastle.org/java.html', role: 'Blake2b-256 for ErgoTree → address' },
      { name: 'Jackson', version: '3', url: 'https://github.com/FasterXML/jackson', role: 'JSON (REST and MCP)' },
    ],
  },
  {
    title: 'Data',
    subtitle: 'Where the numbers come from',
    items: [
      { name: 'MySQL 8 / MariaDB 10.6+', url: 'https://mariadb.org', role: 'Chain index: blocks, transactions, boxes, tokens, aggregates and daily rollups' },
      { name: 'Ergo full nodes', url: 'https://github.com/ergoplatform/ergo', role: 'sv1.erg.vn and sv2.erg.vn, load-balanced; extra indexer enabled for fallback reads and the mempool' },
      {
        name: 'Emission schedule',
        url: 'https://github.com/ergoplatform/eips/blob/master/eip-0027.md',
        role: 'Circulating supply = issued − re-emission share (EIP-27), computed from the schedule',
      },
    ],
  },
  {
    title: 'Deployment',
    subtitle: 'Production layout',
    items: [
      { name: 'Caddy', version: '2', url: 'https://caddyserver.com', role: 'HTTPS, static frontend with SPA fallback, /api reverse proxy' },
      { name: 'systemd', url: 'https://systemd.io', role: 'Backend service (stork launcher)' },
      { name: 'Maven', version: '3', url: 'https://maven.apache.org', role: 'Build and stork launcher packaging' },
      { name: 'Docker', url: 'https://www.docker.com', role: 'Optional image for the backend' },
    ],
  },
]

const FLOW = [
  {
    title: '1. Index',
    text: 'The indexer pulls full blocks from the nodes in parallel batches and writes blocks, transactions and boxes with sequential keys in chain order, binary hashes, ErgoTrees and addresses deduplicated into a script table, spends as separate rows, plus per-address / per-token / per-day aggregates — one transaction per batch. Reorgs roll the affected block back.',
  },
  {
    title: '2. Serve',
    text: 'The API answers from the database first; heights not indexed yet fall back to the node, so the explorer works while the initial sync runs. Balances, rich lists and charts need a complete index.',
  },
  {
    title: '3. Show',
    text: 'This frontend calls /api/v1 on the same host. Addresses are derived from ErgoTrees by the backend, registers R4–R9 are decoded, and the emission schedule gives the circulating supply.',
  },
]

const ERGO = [
  {
    title: 'The Ergo Manifesto',
    url: 'https://ergoplatform.org/en/blog/2021-04-26-the-ergo-manifesto/',
    badge: 'must read',
    text: 'Why Ergo exists: the principles behind the protocol — decentralization, financial tools for the many, open research and a fair launch without premine or VC allocation.',
  },
  { title: 'ergoplatform.org', url: 'https://ergoplatform.org', text: 'Official site: documentation, wallets, the node and the research the protocol is built on.' },
]

const SOURCE = [
  {
    title: 'Open source, MIT',
    text: 'The whole explorer is published under the MIT license at github.com/dungvn3000/ergoexplorer: the Java backend (REST API, chain indexer, MCP server) and the frontend.',
  },
  {
    title: 'Run your own',
    text: 'Build the backend with Maven, point ergo.nodes at your full node with the extra indexer enabled, and Flyway creates the MySQL schema on first start. Deployment notes are in backend/deploy.',
  },
  {
    title: 'Contribute',
    text: 'Bugs, wrong numbers and missing pool labels are best reported as GitHub issues with the block, transaction or address in question. Pull requests are welcome.',
  },
]

// explorer / node / index / database figures from /info; i is null while pending
function StatusStats({ i }) {
  return (
    <Stats>
      <Stat k="Explorer" v={i ? `v${i.version}` : '…'} d={i && `built ${i.buildDate}`} />
      <Stat k="Ergo node" v={i ? `v${i.nodeVersion.split('-')[0]}` : '…'} d={i && `${i.nodeName} · ${i.peers} peers`} />
      <Stat
        k="Chain index"
        v={i ? `${fmtInt(i.indexedHeight)} / ${fmtInt(i.height)}` : '…'}
        d={i && (i.indexSynced ? 'fully synced' : `${((i.indexedHeight / i.height) * 100).toFixed(1)}% indexed`)}
      />
      <Stat k="Database" v={i ? bytes(i.db.totalBytes) : '…'} d={i && `${bytes(i.db.dataBytes)} data · ${bytes(i.db.indexBytes)} indexes`} />
    </Stats>
  )
}

export function About() {
  const info = useApi(getInfo, [])
  return (
    <main className="wrap">
      <Crumbs>About</Crumbs>
      <PageHead
        title={<h1>About</h1>}
        sub={<Sub>Ergo Explorer indexes the Ergo mainnet from full nodes into its own database and serves blocks, transactions, addresses, tokens, rich lists and charts from it.</Sub>}
      />

      <div className="mb-5 rounded-lg border border-warn/30 bg-warn-soft px-4 py-3 text-ink">
        <span className="font-semibold text-warn">Fan-made explorer.</span> This site is an independent, community-run project and is not affiliated with or endorsed by the Ergo Foundation or the Ergo
        Platform team. The official explorer is <ExternalLink href="https://explorer.ergoplatform.com/">explorer.ergoplatform.com</ExternalLink>. Data here is read from public Ergo nodes and may lag
        or differ; always verify important balances and transactions there or on your own node.
      </div>

      <StatusStats i={info.data || null} />

      <div className="grid-2">
        {STACK.map((group) => (
          <Panel key={group.title}>
            <PanelHead title={group.title}>
              <Aside>{group.subtitle}</Aside>
            </PanelHead>
            <ul className="divide-y divide-line">
              {group.items.map((item) => (
                <li key={item.name} className="px-4 py-2.5">
                  <ExternalLink href={item.url} className="font-medium">
                    {item.name}
                  </ExternalLink>
                  {item.version && <span className="ml-1.5 text-muted">{item.version}</span>}
                  <div className="text-sm text-muted">{item.role}</div>
                </li>
              ))}
            </ul>
          </Panel>
        ))}
      </div>

      <Panel className="mb-5">
        <PanelHead title="How it works" />
        <div className="grid grid-cols-3 gap-5 p-4 max-[900px]:grid-cols-1">
          {FLOW.map((step) => (
            <div key={step.title}>
              <div className="font-semibold">{step.title}</div>
              <div className="mt-1 text-sm text-muted">{step.text}</div>
            </div>
          ))}
        </div>
      </Panel>

      <div className="grid-2">
        <Panel>
          <PanelHead title="About Ergo" />
          <ul className="divide-y divide-line">
            {ERGO.map((item) => (
              <li key={item.title} className="px-4 py-3">
                <ExternalLink href={item.url} className="font-medium">
                  {item.title}
                </ExternalLink>
                {item.badge && (
                  <span className="ml-2">
                    <Pill tone="orange">{item.badge}</Pill>
                  </span>
                )}
                <div className="mt-0.5 text-sm text-muted">{item.text}</div>
              </li>
            ))}
          </ul>
        </Panel>
        <Panel>
          <PanelHead title="Source code">
            <ExternalLink href="https://github.com/dungvn3000/ergoexplorer" className="text-meta font-medium">
              github.com/dungvn3000/ergoexplorer
            </ExternalLink>
          </PanelHead>
          <ul className="divide-y divide-line">
            {SOURCE.map((item) => (
              <li key={item.title} className="px-4 py-3">
                <div className="font-medium">{item.title}</div>
                <div className="mt-0.5 text-sm text-muted">{item.text}</div>
              </li>
            ))}
          </ul>
        </Panel>
      </div>
    </main>
  )
}
