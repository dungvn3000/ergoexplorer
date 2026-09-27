import { Link, useParams } from 'react-router'
import { getBox, getTransaction, API_BASE } from '../api.js'
import { useApi } from '../hooks.js'
import { Loaded } from '../components/widgets.jsx'
import { ArrowRight } from 'lucide-react'
import { Panel, PanelHead, Aside, Crumbs, PageHead, KvCard, Kv, KvRow, Table, Pill, IdLine, CopyButton, RawJsonButton, Empty, AddressLink, BlockLink, TxLink, TokenLink } from '../components/ui.jsx'
import { fmtInt, erg, ago, short, fmtDate, tokAmt } from '../format.js'
import { addressLabel } from '../labels.js'

const REGISTER_KEYS = ['R4', 'R5', 'R6', 'R7', 'R8', 'R9']

export function Box() {
  const { id } = useParams()
  const q = useApi(async () => {
    const box = await getBox(id)
    if (!box) return null
    // the creating transaction gives the inclusion block and time; the page still works without it
    const tx = await getTransaction(box.transactionId).catch(() => null)
    return { box, tx }
  }, [id])
  return (
    <Loaded q={q} kind="box" id={id}>
      {({ box, tx }) => <BoxPage key={box.boxId} o={box} tx={tx} />}
    </Loaded>
  )
}

function BoxPage({ o, tx }) {
  const p2pk = o.ergoTree.startsWith('0008cd')
  const pending = !!(tx && tx.pending)
  const included = tx && !tx.pending ? tx : null
  const createdAt = included ? `block ${fmtInt(included.height)} · ${ago(included.timestamp)}` : pending ? 'pending in mempool' : `creation height ${fmtInt(o.creationHeight)}`
  const status = o.spent ? <Pill>Spent</Pill> : pending ? <Pill tone="warn">Pending · in mempool</Pill> : o.spent === false ? <Pill tone="good">Unspent</Pill> : null

  return (
    <main className="wrap">
      <Crumbs>
        <Link to={`/tx/${o.transactionId}`}>Transaction {short(o.transactionId, 6, 4)}</Link>
        Box
      </Crumbs>
      <PageHead
        title={
          <>
            <h1>Box</h1>
            {status}
            <Pill>{p2pk ? 'P2PK' : 'P2S · script'}</Pill>
          </>
        }
        sub={<IdLine id={o.boxId} />}
        actions={<RawJsonButton href={`${API_BASE}/boxes/${o.boxId}`} />}
      />

      {/* lifecycle: created → this box → spent */}
      <Panel className="mb-5">
        <div className="grid grid-cols-[1fr_auto_1fr_auto_1fr] items-center gap-3 p-4 max-[800px]:grid-cols-1">
          <Step caption="Created in" main={<TxLink id={o.transactionId} />} sub={createdAt} />
          <StepArrow />
          <Step
            caption="This box"
            className="border-accent-line bg-accent-soft"
            main={<span className="font-semibold tabular-nums">{erg(o.value)} ERG</span>}
            sub={`output #${o.index}${o.assets.length ? ` · ${o.assets.length} token${o.assets.length > 1 ? 's' : ''}` : ''}`}
          />
          <StepArrow />
          <Step
            caption="Spent in"
            main={o.spent ? <TxLink id={o.spentBy} /> : <span className="text-muted">Not spent</span>}
            sub={o.spent ? 'consumed as an input' : o.spent === false ? 'still in the UTXO set' : 'spending status unknown'}
          />
        </div>
      </Panel>

      <KvCard>
        <Kv>
          <KvRow k="Value">
            <span className="tabular-nums">{erg(o.value)}</span> <span className="text-muted">ERG · {fmtInt(o.value)} nanoERG</span>
          </KvRow>
          <KvRow k="Address">
            {addressLabel(o.address) && (
              <div>
                <AddressLink address={o.address} />
              </div>
            )}
            <Link className="hash-full" to={`/address/${o.address}`}>
              {o.address}
            </Link>
          </KvRow>
          <KvRow k="Created in">
            <TxLink id={o.transactionId} />
            <CopyButton value={o.transactionId} /> <span className="text-muted">· output #{o.index}</span>
          </KvRow>
          <KvRow k="Creation height">
            <span className="tabular-nums">{fmtInt(o.creationHeight)}</span> <span className="text-muted">· set by the transaction builder</span>
          </KvRow>
        </Kv>
        <Kv>
          <KvRow k="Status">
            {o.spent ? (
              <>
                Spent in <TxLink id={o.spentBy} />
              </>
            ) : pending ? (
              'Unconfirmed'
            ) : o.spent === false ? (
              'Unspent'
            ) : (
              '—'
            )}
          </KvRow>
          <KvRow k="Included in block">
            {included ? (
              <>
                <BlockLink height={included.height} /> <span className="text-muted">· {fmtDate(included.timestamp)}</span>
              </>
            ) : (
              <span className="text-muted">—</span>
            )}
          </KvRow>
          <KvRow k="Tokens">
            <span className="tabular-nums">{o.assets.length}</span>
          </KvRow>
          <KvRow k="Registers">{`${o.registers.length} of 6 set (R4–R9)`}</KvRow>
        </Kv>
      </KvCard>

      <div className="grid-2">
        <Panel>
          <PanelHead title="Tokens">
            <Aside>{o.assets.length}</Aside>
          </PanelHead>
          {o.assets.length ? (
            <Table cols={['Token', '>Amount', 'Token id']}>
              {o.assets.map((a) => (
                <tr key={a.tokenId}>
                  <td>
                    <TokenLink id={a.tokenId} name={a.name} />
                  </td>
                  <td className="r">{tokAmt(a.amount, a.decimals)}</td>
                  <td className="mono">{short(a.tokenId, 10, 8)}</td>
                </tr>
              ))}
            </Table>
          ) : (
            <Empty>This box carries no tokens.</Empty>
          )}
        </Panel>
        <Panel>
          <PanelHead title="Registers">
            <Aside>R4–R9</Aside>
          </PanelHead>
          <Table cols={['Reg', 'Type', 'Value', 'Raw']}>
            {REGISTER_KEYS.map((k) => {
              const r = o.registers.find((x) => x.key === k)
              return r ? (
                <tr key={k}>
                  <td className="font-mono font-semibold text-accent-ink">{k}</td>
                  <td className="font-mono text-sm">{r.type}</td>
                  <td className="max-w-[260px] truncate font-mono text-sm" title={r.value}>
                    {r.value}
                  </td>
                  <td className="mono" title={r.raw}>
                    {short(r.raw, 8, 6)}
                  </td>
                </tr>
              ) : (
                <tr key={k}>
                  <td className="font-mono text-faint">{k}</td>
                  <td className="text-faint" colSpan={3}>
                    empty
                  </td>
                </tr>
              )
            })}
          </Table>
          <div className="panel-f">
            <span>R0–R3 hold value, script, tokens and creation info; R4–R9 are free for contract data.</span>
          </div>
        </Panel>
      </div>

      <Panel>
        <PanelHead title="ErgoTree">
          <Aside>{p2pk ? 'Pay-to-public-key' : 'Pay-to-script'}</Aside>
        </PanelHead>
        <div className="panel-b">
          {p2pk && (
            <div className="mb-3 font-mono text-meta">
              proveDlog(<span className="break-all text-accent-ink">{o.ergoTree.slice(6)}</span>)
            </div>
          )}
          <div className="hash-full text-muted">{o.ergoTree}</div>
          <div className="mt-2 flex items-center gap-1.5 text-sm text-muted">
            {fmtInt(o.ergoTree.length / 2)} bytes
            <CopyButton value={o.ergoTree} />
          </div>
        </div>
      </Panel>
    </main>
  )
}

const Step = ({ caption, main, sub, className = 'border-line bg-surface-2' }) => (
  <div className={`min-w-0 rounded-lg border px-3.5 py-3 ${className}`}>
    <div className="text-2xs font-semibold uppercase tracking-[.05em] text-muted">{caption}</div>
    <div className="mt-1 truncate">{main}</div>
    <div className="truncate text-sm text-muted">{sub}</div>
  </div>
)
const StepArrow = () => (
  <div className="grid place-items-center text-faint max-[800px]:rotate-90">
    <ArrowRight size={20} className="block" />
  </div>
)
