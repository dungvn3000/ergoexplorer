import { useSearchParams } from 'react-router'
import { getRichList, getNetworkState, getBalanceDistribution } from '../api.js'
import { useApi } from '../hooks.js'
import { Loaded } from '../components/widgets.jsx'
import { Panel, PanelHead, Aside, Crumbs, PageHead, Sub, Table, Pill, Tag, Stats, Stat, AddressLink, Pager, Loading, Empty } from '../components/ui.jsx'
import { fmtInt, erg, NANO } from '../format.js'
import { addressLabel } from '../labels.js'

const PER_PAGE = 50

// the table renders as soon as its page arrives; the supply shares fill in when the top 100 and the supply answer
export function RichList() {
  const [params] = useSearchParams()
  const page = Math.max(1, +params.get('page') || 1)
  const q = useApi(() => getRichList(page, PER_PAGE), [page], { keepPrevious: true })
  // top 100 (the API maximum per page) for the concentration stats, and circulating supply for shares
  const top = useApi(() => getRichList(1, 100), [])
  const net = useApi(getNetworkState, [])
  const dist = useApi(getBalanceDistribution, [])
  const stats = top.data && net.data ? { top: top.data.items, circ: net.data.circulating * NANO } : null
  return (
    <Loaded q={q} kind="rich list">
      {(list) => <RichListPage d={list} page={page} stats={stats} dist={dist} />}
    </Loaded>
  )
}

// stats: { top: top-100 items, circ: circulating nanoERG } or null while pending; dist: the balance-buckets query
function RichListPage({ d, page, stats, dist }) {
  const circ = stats ? stats.circ : null
  const topItems = stats ? stats.top : null
  const share = (n) => (circ && topItems ? ((topItems.slice(0, n).reduce((s, x) => s + x.amount, 0) / circ) * 100).toFixed(1) : '—')
  const maxAmount = topItems && topItems.length ? topItems[0].amount : d.items[0] ? d.items[0].amount : 1
  return (
    <main className="wrap">
      <Crumbs>Rich list</Crumbs>
      <PageHead
        title={
          <>
            <h1>Rich list</h1>
            {!d.synced && <Pill tone="warn">Index catching up</Pill>}
          </>
        }
        sub={<Sub>Addresses ranked by confirmed ERG balance · computed at height {fmtInt(d.indexedHeight)}</Sub>}
      />
      <Stats>
        <Stat k="Funded addresses" v={fmtInt(d.total)} d="holding at least 1 nanoERG" />
        <Stat k="Top 10 hold" v={share(10)} unit="%" d="of circulating supply" />
        <Stat k="Top 100 hold" v={share(100)} unit="%" d="of circulating supply" />
        <Stat k="Circulating" v={circ ? (circ / NANO / 1e6).toFixed(2) : '—'} unit="M ERG" d="ERG in circulation" />
      </Stats>
      <Distribution q={dist} />
      <Panel>
        <PanelHead title="Top addresses" />
        <Table cols={['#', 'Address', '>Balance', 'Share of supply', '>Boxes']}>
          {d.items.map((x) => (
            <Row key={x.address} x={x} circ={circ} maxAmount={maxAmount} />
          ))}
        </Table>
        <div className="panel-f">
          <span>{d.items.length ? `Ranks ${fmtInt(d.items[0].rank)}–${fmtInt(d.items[d.items.length - 1].rank)} of ${fmtInt(d.total)}` : 'No addresses'}</span>
          <Pager page={page} pages={Math.max(1, Math.ceil(d.total / PER_PAGE))} hrefFor={(p) => `/richlist?page=${p}`} />
        </div>
      </Panel>
    </main>
  )
}

function Row({ x, circ, maxAmount }) {
  const pct = circ ? (x.amount / circ) * 100 : null
  const contract = !x.address.startsWith('9') && !addressLabel(x.address)
  return (
    <tr>
      <td className="tabular-nums text-muted">{x.rank}</td>
      <td>
        <span className="inline-flex items-center gap-2">
          <AddressLink address={x.address} a={10} b={6} />
          {contract && <Tag>P2S</Tag>}
        </span>
      </td>
      <td className="r font-medium">
        {erg(x.amount, 2)} <span className="font-normal text-muted">ERG</span>
      </td>
      <td>
        <div className="flex items-center gap-2.5">
          <div className="h-1.5 w-24 overflow-hidden rounded-full bg-surface-3">
            <div className="h-full rounded-full bg-accent" style={{ width: `${Math.max(1, (x.amount / maxAmount) * 100).toFixed(1)}%` }} />
          </div>
          <span className="tabular-nums text-muted">{pct == null ? '—' : `${pct < 0.01 ? pct.toFixed(4) : pct.toFixed(2)}%`}</span>
        </div>
      </td>
      <td className="r">{x.boxCount != null ? fmtInt(x.boxCount) : '—'}</td>
    </tr>
  )
}

// funded wallet (P2PK, 9...) addresses grouped by balance, contracts left out; shares are of the ERG the buckets hold, so the column adds up to 100%
function Distribution({ q }) {
  const d = q.data
  const pct = (part, whole) => (whole ? (part / whole) * 100 : 0)
  const fmtPct = (p) => (p > 0 && p < 0.01 ? '<0.01' : p.toFixed(2)) + '%'
  return (
    <Panel className="mb-5">
      <PanelHead title="Balance distribution">{d && <Aside>{fmtInt(d.addresses)} funded wallet addresses (9…), contracts excluded</Aside>}</PanelHead>
      {/* a full scan of the balances on the server, so it can answer well after the rich list itself */}
      {d ? (
        <Table cols={['Balance (ERG)', '>Addresses', '>% of addresses', '>ERG held', 'Share of ERG']}>
          {d.buckets.map((b) => {
            const share = pct(b.nanoErg, d.nanoErg)
            return (
              <tr key={b.label}>
                <td className="font-medium">{b.label}</td>
                <td className="r">{fmtInt(b.addresses)}</td>
                <td className="r text-muted">{fmtPct(pct(b.addresses, d.addresses))}</td>
                <td className="r">
                  {erg(b.nanoErg, b.nanoErg < 1000 * NANO ? 2 : 0)} <span className="text-muted">ERG</span>
                </td>
                <td>
                  <div className="flex items-center gap-2.5">
                    <div className="h-1.5 w-24 overflow-hidden rounded-full bg-surface-3">
                      <div className="h-full rounded-full bg-accent" style={{ width: `${share.toFixed(1)}%` }} />
                    </div>
                    <span className="tabular-nums text-muted">{fmtPct(share)}</span>
                  </div>
                </td>
              </tr>
            )
          })}
        </Table>
      ) : q.error ? (
        <Empty>Could not load the balance distribution.</Empty>
      ) : (
        <Loading />
      )}
    </Panel>
  )
}
