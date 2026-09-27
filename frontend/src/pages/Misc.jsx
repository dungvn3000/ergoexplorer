import { useLocation, useParams } from 'react-router'
import { getInfo, API_BASE } from '../api.js'
import { useApi } from '../hooks.js'
import { Missing } from '../components/widgets.jsx'
import { Panel, PanelHead, Aside, Crumbs, PageHead, Sub, Kv, KvRow, Pill, Loading, Snippet } from '../components/ui.jsx'
import { fmtInt, bytes } from '../format.js'

const ENDPOINTS = [
  ['/info', 'Node, index and database status'],
  ['/networkState', 'Height, hashrate, difficulty, supply, mempool, pool share'],
  ['/blocks?page=1&rowsPerPage=25', 'Blocks, newest first'],
  ['/blocks/latest?limit=10', 'Latest blocks'],
  ['/blocks/{idOrHeight}', 'Block with its transactions'],
  ['/blocks/{id}/raw', 'Block exactly as the node returns it'],
  ['/transactions/latest?limit=10', 'Latest confirmed transactions'],
  ['/transactions/{id}', 'Transaction (confirmed or in the mempool)'],
  ['/boxes/{boxId}', 'Box'],
  ['/addresses/{address}?page=1&rowsPerPage=20', 'Balance, tokens and transaction history'],
  ['/addresses/{address}/boxes', 'Unspent boxes of an address'],
  ['/tokens', 'Featured tokens'],
  ['/tokens/{tokenId}', 'Token metadata, top holders, recent transfers'],
  ['/tokens/{tokenId}/holders', 'All holders of a token'],
  ['/mempool/transactions', 'Unconfirmed transactions'],
  ['/richlist?page=1&rowsPerPage=50', 'Addresses ranked by ERG balance'],
  ['/richlist/distribution', 'Funded addresses and ERG held per balance range'],
  ['/charts', 'Chart names'],
  ['/charts/{name}?days=30', 'Daily chart series; days=0 for all history'],
  ['/search?q=…', 'What a string is: block, transaction, token, box or address'],
]

// MCP server of the production explorer (backend/, /api/mcp): the same data for AI agents over the Model Context Protocol
const MCP_URL = 'https://explorer.erg.vn/api/mcp'
const MCP_CONFIGS = [
  ['Claude Desktop / Cursor config', `{ "mcpServers": { "ergo-explorer": { "url": "${MCP_URL}" } } }`],
  ['Claude Code', `claude mcp add --transport http ergo-explorer ${MCP_URL}`],
  [
    'OpenCode — opencode.json (project root or ~/.config/opencode/)',
    `{\n  "$schema": "https://opencode.ai/config.json",\n  "mcp": {\n    "ergo-explorer": { "type": "remote", "url": "${MCP_URL}", "enabled": true }\n  }\n}`,
  ],
]
const MCP_TOOLS = [
  ['getNetworkState', 'height, hashrate, difficulty, supply, mempool, pool shares'],
  ['getBlock', 'block by height or id with its transactions'],
  ['listBlocks', 'newest blocks, paged'],
  ['getTransaction', 'transaction by id, confirmed or in the mempool'],
  ['getAddress', 'balance, tokens, history page of an address'],
  ['getAddressBoxes', 'unspent boxes of an address'],
  ['getBox', 'box by id with decoded registers'],
  ['getToken', 'EIP-4 token metadata, holders, transfers'],
  ['getTokenHolders', 'token rich list, paged'],
  ['listTokens', 'featured tokens'],
  ['getRichList', 'ERG rich list, paged'],
  ['getBalanceDistribution', 'Addresses and ERG held per balance range'],
  ['listCharts', 'available chart series'],
  ['getChart', 'a chart series as {t, v} points'],
  ['getMempool', 'unconfirmed transactions'],
  ['search', 'what a string is: height, block, tx, box, token or address'],
  ['getStatus', 'indexed height and database size'],
]

function McpPanel() {
  return (
    <Panel>
      <PanelHead title="MCP server for AI agents">
        <Pill>Streamable HTTP · stateless</Pill>
      </PanelHead>
      <div className="grid grid-cols-2 gap-x-8 p-4 max-[900px]:grid-cols-1">
        <div className="min-w-0">
          <p className="mb-3 text-muted">
            The same data is available to AI agents (Claude, Cursor, any MCP client) over the Model Context Protocol. Point the client at the endpoint below; no key, no session, nothing is stored
            about the caller.
          </p>
          <div className="text-sm text-muted">Endpoint</div>
          <Snippet>{`POST ${MCP_URL}`}</Snippet>
          {MCP_CONFIGS.map(([label, text]) => (
            <div key={label}>
              <div className="text-sm text-muted">{label}</div>
              <Snippet>{text}</Snippet>
            </div>
          ))}
        </div>
        <div className="min-w-0">
          <div className="mb-1 text-sm text-muted">Tools ({MCP_TOOLS.length})</div>
          <ul className="divide-y divide-line rounded-md border border-line">
            {MCP_TOOLS.map(([name, desc]) => (
              <li key={name} className="flex flex-col px-3 py-2">
                <span className="font-mono text-meta text-accent-ink">{name}</span>
                <span className="text-sm text-muted">{desc}</span>
              </li>
            ))}
          </ul>
        </div>
      </div>
    </Panel>
  )
}

// node / index status from /info; i is null while pending
function StatusPanel({ i, failed = false }) {
  return (
    <Panel>
      <PanelHead title="Status">{i && (i.indexSynced ? <Pill tone="good">Index synced</Pill> : <Pill tone="warn">Index catching up</Pill>)}</PanelHead>
      <div className="panel-b">
        {failed ? (
          <div className="text-crit">The backend did not answer.</div>
        ) : !i ? (
          <Loading />
        ) : (
          <Kv>
            <KvRow k="Network">{i.network}</KvRow>
            <KvRow k="Node">{`${i.nodeName} · ${i.nodeVersion}`}</KvRow>
            <KvRow k="Node height">{`${fmtInt(i.height)}${i.headersHeight > i.height ? ` · headers ${fmtInt(i.headersHeight)}` : ''}`}</KvRow>
            <KvRow k="Indexed height">
              {fmtInt(i.indexedHeight)}
              {i.height > i.indexedHeight && <span className="text-muted"> · {fmtInt(i.height - i.indexedHeight)} behind</span>}
            </KvRow>
            <KvRow k="Peers">{fmtInt(i.peers)}</KvRow>
            <KvRow k="Upstream nodes">
              <div className="flex flex-col gap-1">
                {i.nodes.map((n) => (
                  <span key={n.url} className="inline-flex items-center gap-2">
                    <span className={`size-2 rounded-full ${n.healthy ? 'bg-good' : 'bg-crit'}`} />
                    {n.url}
                  </span>
                ))}
              </div>
            </KvRow>
            <KvRow k="Database">{`${bytes(i.db.totalBytes)} · ${fmtInt(i.db.rows)} rows`}</KvRow>
            <KvRow k="Backend">{`${i.version} · built ${i.buildDate}`}</KvRow>
          </Kv>
        )}
      </div>
    </Panel>
  )
}

export function Api() {
  const info = useApi(getInfo, [])
  return (
    <main className="wrap">
      <Crumbs>API &amp; MCP</Crumbs>
      <PageHead
        title={
          <>
            <h1>API &amp; MCP</h1>
            <Pill>Read-only, no key required</Pill>
          </>
        }
        sub={
          <Sub>
            REST API at <code className="font-mono">{API_BASE}</code>. Responses are JSON {'{ errorCode, error, data }'}; amounts in nanoERG, timestamps in ms.
          </Sub>
        }
      />
      <div className="grid-2">
        <Panel>
          <PanelHead title="Endpoints">
            <Aside>GET</Aside>
          </PanelHead>
          <div className="divide-y divide-line">
            {ENDPOINTS.map(([path, desc]) => (
              <div key={path} className="flex flex-col gap-0.5 px-4 py-2.5">
                {path.includes('{') ? (
                  <span className="break-all font-mono text-meta text-accent-ink">{path}</span>
                ) : (
                  <a className="break-all font-mono text-meta" href={API_BASE + path} target="_blank" rel="noopener noreferrer">
                    {path}
                  </a>
                )}
                <span className="text-sm text-muted">{desc}</span>
              </div>
            ))}
          </div>
        </Panel>
        <StatusPanel i={info.data || null} failed={!!info.error && !info.data} />
      </div>
      <McpPanel />
    </main>
  )
}

export function SearchMiss() {
  const { q } = useParams()
  return <Missing title="No match found" detail={q} text="Nothing on mainnet matches this search. Paste a full block height or id, transaction id, box id, token id or address." />
}

export function NotFound() {
  const { pathname } = useLocation()
  return <Missing title="Page not found" text="This page does not exist." detail={pathname} />
}
