import { Link, useParams } from 'react-router'
import { getTransaction, API_BASE } from '../api.js'
import { useApi } from '../hooks.js'
import { Loaded } from '../components/widgets.jsx'
import { ArrowRight } from 'lucide-react'
import { Panel, Crumbs, PageHead, KvCard, Kv, KvRow, Pill, KindPill, IdLine, CopyButton, RawJsonButton, AddressLink, BlockLink, TxLink, BoxLink, TokenChip } from '../components/ui.jsx'
import { fmtInt, erg, ago, short, fmtDate, totalValue } from '../format.js'

export function Tx() {
  const { id } = useParams()
  const q = useApi(() => getTransaction(id), [id])
  return (
    <Loaded q={q} kind="transaction" id={id}>
      {(t) => <TxPage key={t.id} t={t} />}
    </Loaded>
  )
}

function TxPage({ t }) {
  const inTotal = totalValue(t.inputs),
    outTotal = totalValue(t.outputs)
  return (
    <main className="wrap">
      <Crumbs>
        {t.pending ? <Link to="/mempool">Mempool</Link> : <Link to={`/block/${t.height}`}>Block {fmtInt(t.height)}</Link>}
        Transaction
      </Crumbs>
      <PageHead
        title={
          <>
            <h1>Transaction</h1>
            {t.pending ? (
              <Pill tone="warn">Pending · in mempool</Pill>
            ) : (
              <Pill tone="good">
                {fmtInt(t.confirmations)} confirmation{t.confirmations === 1 ? '' : 's'}
              </Pill>
            )}
            <KindPill kind={t.kind} />
          </>
        }
        sub={<IdLine id={t.id} />}
        actions={<RawJsonButton href={`${API_BASE}/transactions/${t.id}`} />}
      />

      <KvCard>
        <Kv>
          <KvRow k="Block">
            {t.pending ? (
              <span className="text-muted">— not yet included</span>
            ) : (
              <>
                <BlockLink height={t.height} />
                {t.blockId && (
                  <>
                    {' '}
                    <span className="font-mono text-sm text-muted">{short(t.blockId, 8, 6)}</span>
                  </>
                )}
              </>
            )}
          </KvRow>
          <KvRow k="Timestamp">
            {t.pending ? (
              <span className="text-muted">first seen {ago(t.timestamp)}</span>
            ) : (
              <>
                <span className="font-mono">{fmtDate(t.timestamp)}</span> <span className="text-muted">· {ago(t.timestamp)}</span>
              </>
            )}
          </KvRow>
          <KvRow k="Index in block">
            <span className="tabular-nums">{t.index ?? '—'}</span>
          </KvRow>
          <KvRow k="Size">
            <span className="tabular-nums">{fmtInt(t.size)} bytes</span>
          </KvRow>
        </Kv>
        <Kv>
          <KvRow k="Total input">
            <Erg nano={inTotal} />
          </KvRow>
          <KvRow k="Total output">
            <Erg nano={outTotal} />
          </KvRow>
          <KvRow k="Fee">
            {t.fee ? (
              <>
                <Erg nano={t.fee} /> <span className="text-muted">· {(t.fee / t.size / 1000).toFixed(3)} µERG/byte</span>
              </>
            ) : (
              <span className="text-muted">none</span>
            )}
          </KvRow>
          <KvRow k="Data inputs">
            <span className="tabular-nums">{t.dataInputs.length}</span>
          </KvRow>
        </Kv>
      </KvCard>

      <div className="grid grid-cols-[1fr_32px_1fr] items-start gap-3 max-[900px]:grid-cols-1">
        <Panel>
          <IoHead label="Inputs" n={t.inputs.length} right={`${erg(inTotal, 4)} ERG`} />
          {t.inputs.map((o) => (
            <BoxRow key={o.boxId} o={o} role="input" />
          ))}
          {t.dataInputs.length > 0 && (
            <>
              <IoHead label="Data inputs" n={t.dataInputs.length} right="read-only" className="border-t" />
              {t.dataInputs.map((o) => (
                <BoxRow key={o.boxId} o={o} role="input" />
              ))}
            </>
          )}
        </Panel>
        <div className="grid place-items-center pt-11 text-faint max-[900px]:rotate-90 max-[900px]:justify-self-center max-[900px]:pt-0">
          <ArrowRight size={20} className="block" />
        </div>
        <Panel>
          <IoHead label="Outputs" n={t.outputs.length} right={`${erg(outTotal, 4)} ERG`} />
          {t.outputs.map((o) => (
            <BoxRow key={o.boxId} o={o} role="output" />
          ))}
        </Panel>
      </div>
    </main>
  )
}

const Erg = ({ nano }) => (
  <>
    <span className="tabular-nums">{erg(nano)}</span> <span className="text-muted">ERG</span>
  </>
)

const IoHead = ({ label, n, right, className = '' }) => (
  <div className={`flex items-center justify-between border-b border-line px-3.5 py-2.5 font-semibold ${className}`}>
    <span>
      {label} <span className="text-muted">({n})</span>
    </span>
    <span className="text-meta font-medium tabular-nums text-muted">{right}</span>
  </div>
)

// one input / output box
function BoxRow({ o, role }) {
  return (
    <div className="flex min-w-0 flex-col gap-1.5 border-b border-line px-3.5 py-3 last:border-b-0">
      <div className="flex min-w-0 items-baseline justify-between gap-2.5">
        <span className="min-w-0 truncate font-mono text-meta">
          <AddressLink address={o.address} a={12} b={10} />
        </span>
        <span className="whitespace-nowrap font-semibold tabular-nums">
          {erg(o.value)} <span className="font-medium text-muted">ERG</span>
        </span>
      </div>
      {o.assets.length > 0 && (
        <div className="flex flex-wrap gap-1.25">
          {o.assets.map((a) => (
            <TokenChip key={a.tokenId} asset={a} />
          ))}
        </div>
      )}
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1.5 text-sm text-muted">
        <span className="font-mono text-faint">#{o.index}</span>
        <span className="inline-flex items-center">
          <BoxLink id={o.boxId} />
          <CopyButton value={o.boxId} />
        </span>
        {role === 'output' && o.spent && (
          <span>
            · spent in <TxLink id={o.spentBy} a={8} b={6} />
          </span>
        )}
        {role === 'output' && o.spent === false && <Pill tone="good">Unspent</Pill>}
        {role === 'input' && (
          <span>
            · created in <TxLink id={o.transactionId} a={8} b={6} /> at {fmtInt(o.creationHeight)}
          </span>
        )}
      </div>
      <details className="group text-sm">
        <summary className="inline-flex cursor-pointer list-none select-none items-center gap-1.25 text-muted [&::-webkit-details-marker]:hidden">
          <span className="ml-0.5 border-y-4 border-l-[5px] border-y-transparent border-l-faint transition-transform group-open:rotate-90" />
          ErgoTree &amp; registers
        </summary>
        <div className="mt-2 grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 font-mono text-xs">
          <span className="font-semibold text-accent-ink">Tree</span>
          <span className="break-all text-muted">{o.ergoTree}</span>
          {o.registers.map((r) => (
            <span key={r.key} className="contents">
              <span className="font-semibold text-accent-ink">{r.key}</span>
              <span className="break-all text-muted">
                {r.value}{' '}
                <span className="text-faint">
                  · {r.type} · {r.raw}
                </span>
              </span>
            </span>
          ))}
          {o.registers.length === 0 && (
            <>
              <span className="font-semibold text-accent-ink">R4–R9</span>
              <span className="text-faint">empty</span>
            </>
          )}
        </div>
      </details>
    </div>
  )
}
