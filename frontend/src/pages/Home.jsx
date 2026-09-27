import { useEffect, useMemo } from 'react'
import { Link } from 'react-router'
import { getNetworkState, getLatestBlocks, getLatestTransactions } from '../api.js'
import { useApi, useInterval } from '../hooks.js'
import { useSetHeight } from '../app-context.js'
import { SearchForm } from '../components/Layout.jsx'
import { LineChart } from '../components/Chart.jsx'
import { Panel, PanelHead, MoreLink, Stats, Stat, Loading } from '../components/ui.jsx'
import { fmtInt, erg, ago, bytes, short, fmtPeta, fmtSeconds, sum, totalValue } from '../format.js'

const REFRESH_MS = 20000
const fmtHashrate = (v) => v.toFixed(2) + ' TH/s'

// Each part renders as soon as its request answers; everything refreshes on a timer (the old data stays meanwhile).
export function Home() {
  const net = useApi(getNetworkState, [])
  const blocks = useApi(() => getLatestBlocks(10), [])
  const txs = useApi(() => getLatestTransactions(10), [])
  useInterval(() => {
    net.reload()
    blocks.reload()
    txs.reload()
  }, REFRESH_MS)

  return (
    <>
      <Hero />
      <main className="wrap">
        {net.error && !net.data && <NetworkError />}
        <NetworkSection n={net.data || null} />
        <div className="grid-2">
          <Panel>
            <PanelHead
              title={
                <>
                  <LiveDot />
                  Latest blocks
                </>
              }
            >
              <MoreLink to="/blocks">View all blocks →</MoreLink>
            </PanelHead>
            <ListBody q={blocks}>
              {(list) => (
                <div className="flex flex-col">
                  {list.map((b) => (
                    <BlockRow key={b.id} b={b} />
                  ))}
                </div>
              )}
            </ListBody>
          </Panel>
          <Panel>
            <PanelHead
              title={
                <>
                  <LiveDot />
                  Latest transactions
                </>
              }
            >
              <MoreLink to="/mempool">View mempool →</MoreLink>
            </PanelHead>
            <ListBody q={txs}>
              {(list) => (
                <div className="flex flex-col">
                  {list.map((t) => (
                    <TxRow key={t.id} t={t} />
                  ))}
                </div>
              )}
            </ListBody>
          </Panel>
        </div>
      </main>
    </>
  )
}

// a panel body for a useApi() list: spinner until the first answer, a short message if it failed
function ListBody({ q, children }) {
  if (q.data) return children(q.data)
  if (q.error) return <div className="empty text-crit">Could not load this list.</div>
  return <Loading />
}

// stats row + hashrate + pools; n is null while /networkState is pending
function NetworkSection({ n }) {
  const setHeight = useSetHeight()
  useEffect(() => {
    if (n) setHeight(n.height)
  }, [n, setHeight])
  return (
    <>
      <NetworkStats n={n} />
      <div className="grid-21">
        <HashratePanel n={n} />
        <PoolsPanel n={n} />
      </div>
    </>
  )
}

const NetworkError = () => <div className="mb-5 rounded-lg border border-crit/30 bg-crit-soft px-4 py-3 text-crit">Network stats are unavailable right now.</div>

const HINT = 'rounded-full border border-nav-line bg-nav-2 px-2.25 py-0.75 text-nav-muted hover:text-nav-ink hover:no-underline'

// kept outside the refreshing parts, so a refresh never touches what is being typed in the search box
function Hero() {
  return (
    <section className="bg-nav text-nav-ink">
      <div className="mx-auto flex max-w-[1240px] flex-col items-start gap-3.5 px-5 pt-9 pb-7">
        <div className="text-sm font-semibold uppercase tracking-[.08em] text-nav-accent">Ergo mainnet</div>
        <h1 className="text-3xl">Search the Ergo blockchain</h1>
        <SearchForm variant="hero" />
        <div className="flex flex-wrap items-center gap-1.5 text-meta text-nav-muted">
          Try:{' '}
          <Link className={HINT} to="/blocks">
            latest blocks
          </Link>{' '}
          <Link className={HINT} to="/mempool">
            mempool
          </Link>{' '}
          <Link className={HINT} to="/richlist">
            rich list
          </Link>{' '}
          <Link className={HINT} to="/charts">
            charts
          </Link>
        </div>
      </div>
    </section>
  )
}

function NetworkStats({ n }) {
  const dash = '—'
  return (
    <Stats className="grid-cols-6 max-[1000px]:grid-cols-3 max-[560px]:grid-cols-2">
      <Stat k="Height" v={n ? fmtInt(n.height) : dash} d={n && `${ago(n.tipTimestamp)} · epoch ${fmtInt(n.epoch)}`} />
      <Stat
        k="Hashrate"
        v={n ? n.hashrate.toFixed(2) : dash}
        unit="TH/s"
        d={n && `${n.hashrateChange7d >= 0 ? '▲' : '▼'} ${Math.abs(n.hashrateChange7d).toFixed(1)}% vs 7d`}
        dClass={n && n.hashrateChange7d < 0 ? 'text-crit' : 'text-good'}
      />
      <Stat k="Difficulty" v={n ? fmtPeta(n.difficulty) : dash} d={n && `next adjust in ${fmtInt(n.blocksToNextEpoch)} blocks`} />
      <Stat k="Block time" v={n ? fmtSeconds(n.avgBlockTimeSec) : dash} d="recent average · target 2m" />
      <Stat k="Supply" v={n ? (n.circulating / 1e6).toFixed(2) : dash} unit="M ERG" d={n && `${((n.circulating / n.maxSupply) * 100).toFixed(1)}% of ${(n.maxSupply / 1e6).toFixed(2)}M max`} />
      <Stat k="Mempool" v={n ? fmtInt(n.mempoolCount) : dash} unit="tx" d={n && `${bytes(n.mempoolBytes)} pending`} />
    </Stats>
  )
}

function HashratePanel({ n }) {
  const series = useMemo(() => (n ? n.hashrate30d.map((d) => ({ t: d.day, v: d.value })) : null), [n])
  return (
    <Panel>
      <PanelHead
        title={
          <>
            Network hashrate <span className="text-meta font-normal text-muted">· 30d · TH/s</span>
          </>
        }
      >
        <MoreLink to="/charts">All charts →</MoreLink>
      </PanelHead>
      <div className="panel-b">{n ? <LineChart data={series} fmt={fmtHashrate} label="Network hashrate, last 30 days" /> : <Loading />}</div>
    </Panel>
  )
}

function PoolsPanel({ n }) {
  const pools = n ? n.poolShare24h : []
  const max = Math.max(1, ...pools.map((p) => p.blocks))
  return (
    <Panel>
      <PanelHead title="Blocks by pool">
        <span className="aside">Last 24h · {fmtInt(sum(pools, (p) => p.blocks))} blocks</span>
      </PanelHead>
      <div className="panel-b">
        {!n && <Loading />}
        <div className="flex flex-col gap-2.25 text-meta">
          {pools.map((p) => (
            <div key={p.address} className="grid grid-cols-[110px_1fr_56px] items-center gap-2.5">
              <Link className="truncate text-ink" to={`/address/${p.address}`} title={p.address}>
                {p.name}
              </Link>
              <div className="h-3.5 overflow-hidden rounded-[3px] bg-surface-3">
                <div className="h-full rounded-[3px_4px_4px_3px] bg-accent opacity-85" style={{ width: `${((p.blocks / max) * 100).toFixed(1)}%` }} />
              </div>
              <div className="text-right tabular-nums text-muted">{p.blocks}</div>
            </div>
          ))}
        </div>
      </div>
    </Panel>
  )
}

const LiveDot = () => <span className="inline-block size-1.5 animate-pulse rounded-full bg-good motion-reduce:animate-none" />

const ROW = 'grid min-w-0 grid-cols-[auto_1fr_auto] items-center gap-3.5 border-b border-line px-4 py-2.5 text-ink last:border-b-0 hover:bg-surface-2 hover:no-underline'

function Row({ to, icon, iconClass, title, titleClass = '', sub, amount, meta }) {
  return (
    <Link className={ROW} to={to}>
      <div className={`grid size-9 place-items-center rounded-lg font-mono text-2xs font-semibold ${iconClass}`}>{icon}</div>
      <div className="flex min-w-0 flex-col gap-px">
        <div className={`truncate font-medium ${titleClass}`}>{title}</div>
        <div className="truncate text-sm text-muted">{sub}</div>
      </div>
      <div className="flex flex-col gap-px text-right tabular-nums">
        <div className="whitespace-nowrap font-medium">{amount}</div>
        <div className="whitespace-nowrap text-sm text-muted">{meta}</div>
      </div>
    </Link>
  )
}

const BlockRow = ({ b }) => (
  <Row
    to={`/block/${b.height}`}
    icon={String(b.height).slice(-3)}
    iconClass="bg-surface-3 text-muted"
    title={
      <>
        <span className="tabular-nums text-accent-ink">{fmtInt(b.height)}</span> <span className="text-muted">·</span> {b.miner || short(b.minerAddress, 6, 4)}
      </>
    }
    sub={
      <>
        {b.txCount} tx · {bytes(b.size)} · <span className="font-mono">{short(b.id, 8, 6)}</span>
      </>
    }
    amount={`${erg(b.reward + b.fees, 4)} ERG`}
    meta={ago(b.timestamp)}
  />
)

const TxRow = ({ t }) => (
  <Row
    to={`/tx/${t.id}`}
    icon="TX"
    iconClass="bg-accent-soft text-accent-ink"
    title={short(t.id, 12, 8)}
    titleClass="font-mono"
    sub={`${t.kind} · ${t.inputs.length} in → ${t.outputs.length} out · block ${fmtInt(t.height)}`}
    amount={`${erg(totalValue(t.outputs), 4)} ERG`}
    meta={`fee ${erg(t.fee, 4)} · ${ago(t.timestamp)}`}
  />
)
