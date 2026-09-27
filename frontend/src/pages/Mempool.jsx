import { getMempool } from '../api.js'
import { useApi, useInterval } from '../hooks.js'
import { Loaded } from '../components/widgets.jsx'
import { Panel, Crumbs, PageHead, Sub, Table, Pill, KindPill, Stats, Stat, TxLink } from '../components/ui.jsx'
import { fmtInt, erg, bytes, totalValue } from '../format.js'

const REFRESH_MS = 10000

export function Mempool() {
  const q = useApi(getMempool, [])
  useInterval(q.reload, REFRESH_MS)
  return (
    <Loaded q={q} kind="mempool">
      {(m) => <MempoolPage m={m} />}
    </Loaded>
  )
}

function MempoolPage({ m }) {
  return (
    <main className="wrap">
      <Crumbs>Mempool</Crumbs>
      <PageHead
        title={
          <>
            <h1>Mempool</h1>
            <Pill tone="warn">{fmtInt(m.total)} unconfirmed</Pill>
          </>
        }
        sub={<Sub>Transactions seen by the node but not yet included in a block · refreshes every {REFRESH_MS / 1000}s</Sub>}
      />
      <Stats>
        <Stat k="Pending" v={fmtInt(m.total)} unit="tx" d={m.total >= 200 ? 'showing the first 200' : 'in the node mempool'} />
        <Stat k="Size" v={bytes(m.size)} d="of transactions waiting" />
        <Stat k="Fees waiting" v={erg(m.fees, 4)} unit="ERG" d="paid to the next miners" />
        <Stat k="Average fee" v={erg(m.total ? m.fees / m.total : 0, 4)} unit="ERG" d={m.size ? `${(m.fees / m.size / 1000).toFixed(2)} µERG/byte` : '—'} />
      </Stats>
      <Panel>
        {m.items.length ? (
          <Table cols={['Transaction', 'Type', '>In → out', '>Value', '>Fee', '>Size']}>
            {m.items.map((t) => (
              <tr key={t.id}>
                <td>
                  <TxLink id={t.id} a={14} b={10} />
                </td>
                <td>
                  <KindPill kind={t.kind} />
                </td>
                <td className="r">
                  {t.inputs.length} → {t.outputs.length}
                </td>
                <td className="r">{erg(totalValue(t.outputs), 4)} ERG</td>
                <td className="r">{erg(t.fee, 4)}</td>
                <td className="r text-muted">{bytes(t.size)}</td>
              </tr>
            ))}
          </Table>
        ) : (
          <div className="p-8 text-center text-muted">The mempool is empty.</div>
        )}
      </Panel>
    </main>
  )
}
