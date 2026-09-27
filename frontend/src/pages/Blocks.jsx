import { useEffect } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import { getBlocks, getBlock, getBlockRaw, API_BASE } from '../api.js'
import { decodeExtension } from '../extension.js'
import { useApi } from '../hooks.js'
import { Loaded } from '../components/widgets.jsx'
import { useSetHeight } from '../app-context.js'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import {
  Panel,
  Crumbs,
  PageHead,
  Sub,
  KvCard,
  Kv,
  KvRow,
  Table,
  Pill,
  KindPill,
  Tag,
  IdLine,
  CopyButton,
  RawJsonButton,
  AddressLink,
  BlockLink,
  BlockIdLink,
  TxLink,
  Pager,
  Tabs,
  Empty,
  Loading,
} from '../components/ui.jsx'
import { fmtInt, erg, ago, bytes, short, fmtDate, fmtPeta, totalValue } from '../format.js'

const PER_PAGE = 25

export function Blocks() {
  const [params] = useSearchParams()
  const page = Math.max(1, +params.get('page') || 1)
  const q = useApi(() => getBlocks(page, PER_PAGE), [page], { keepPrevious: true })
  const setHeight = useSetHeight()
  useEffect(() => {
    if (q.data) setHeight(q.data.total)
  }, [q.data, setHeight])
  return (
    <Loaded q={q} kind="blocks">
      {({ items, total }) => <BlocksPage page={page} items={items} total={total} />}
    </Loaded>
  )
}

function BlocksPage({ page, items, total }) {
  return (
    <main className="wrap">
      <Crumbs>Blocks</Crumbs>
      <PageHead title={<h1>Blocks</h1>} sub={<Sub>Newest first · {fmtInt(total)} blocks in chain</Sub>} />
      <Panel>
        <Table cols={['Height', 'Age', 'Timestamp', '>Txs', 'Miner', '>Size', '>Difficulty', '>Reward + fees', 'Block id']}>
          {items.map((b) => (
            <tr key={b.id}>
              <td>
                <BlockLink height={b.height} />
              </td>
              <td className="text-muted">{ago(b.timestamp)}</td>
              <td className="mono">{fmtDate(b.timestamp)}</td>
              <td className="r">{b.txCount}</td>
              <td>
                <AddressLink address={b.minerAddress} label={b.miner} />
              </td>
              <td className="r">{bytes(b.size)}</td>
              <td className="r">{fmtPeta(b.difficulty)}</td>
              <td className="r">{erg(b.reward + b.fees, 4)} ERG</td>
              <td>
                <BlockIdLink id={b.id} />
              </td>
            </tr>
          ))}
        </Table>
        <div className="panel-f">
          <span>{items.length ? `Showing ${fmtInt(items[0].height)} → ${fmtInt(items[items.length - 1].height)}` : 'No blocks'}</span>
          <Pager page={page} pages={Math.ceil(total / PER_PAGE)} hrefFor={(p) => `/blocks?page=${p}`} />
        </div>
      </Panel>
    </main>
  )
}

export function Block() {
  const { id } = useParams()
  const q = useApi(() => getBlock(id), [id])
  return (
    <Loaded q={q} kind="block" id={id}>
      {(b) => <BlockPage key={b.id} b={b} />}
    </Loaded>
  )
}

function BlockPage({ b }) {
  const txs = b.transactions || []
  const hash = (v) => <span className="hash-full">{v}</span>
  return (
    <main className="wrap">
      <Crumbs>
        <Link to="/blocks">Blocks</Link>
        <span className="tabular-nums">{fmtInt(b.height)}</span>
      </Crumbs>
      <PageHead
        title={
          <>
            <h1>
              Block <span className="tabular-nums">{fmtInt(b.height)}</span>
            </h1>
            <Pill tone="good">{b.confirmations < 10 ? `${b.confirmations} confirmation${b.confirmations === 1 ? '' : 's'}` : 'Confirmed'}</Pill>
            {b.votes !== '0,0,0' && <Pill tone="warn">Votes {b.votes}</Pill>}
          </>
        }
        sub={<IdLine id={b.id} />}
        actions={
          <>
            <Link className="btn" to={`/block/${b.height - 1}`} aria-disabled={b.height <= 1 || undefined}>
              <ChevronLeft size={14} strokeWidth={2.2} />
              {fmtInt(b.height - 1)}
            </Link>
            <Link className="btn" to={`/block/${b.height + 1}`} aria-disabled={b.confirmations <= 1 || undefined}>
              {fmtInt(b.height + 1)}
              <ChevronRight size={14} strokeWidth={2.2} />
            </Link>
            <RawJsonButton href={`${API_BASE}/blocks/${b.id}/raw`} />
          </>
        }
      />

      <KvCard>
        <Kv>
          <KvRow k="Timestamp">
            <span className="font-mono">{fmtDate(b.timestamp)}</span> <span className="text-muted">· {ago(b.timestamp)}</span>
          </KvRow>
          <KvRow k="Transactions">
            <span className="tabular-nums">{b.txCount}</span> <span className="text-muted">· {bytes(b.size)}</span>
          </KvRow>
          <KvRow k="Miner">
            <AddressLink address={b.minerAddress} label={b.miner} /> <span className="font-mono text-sm text-muted">{short(b.minerAddress, 6, 6)}</span>
          </KvRow>
          <KvRow k="Miner reward">
            <span className="tabular-nums">{erg(b.reward)}</span> <span className="text-muted">ERG + {erg(b.fees, 4)} ERG fees</span>
          </KvRow>
          <KvRow k="Emission">
            <span className="tabular-nums">{erg(b.emission)}</span> <span className="text-muted">ERG{b.reemitted > 0 && ` · ${erg(b.reemitted)} ERG to re-emission (EIP-27)`}</span>
          </KvRow>
          <KvRow k="Confirmations">
            <span className="tabular-nums">{fmtInt(b.confirmations)}</span>
          </KvRow>
        </Kv>
        <Kv>
          <KvRow k="Difficulty">
            <span className="tabular-nums">{fmtInt(b.difficulty)}</span>
          </KvRow>
          <KvRow k="nBits">
            <span className="font-mono">{b.nBits}</span>
          </KvRow>
          <KvRow k="Epoch">
            <span className="tabular-nums">{fmtInt(Math.floor(b.height / 1024))}</span> <span className="text-muted">· block {(b.height % 1024) + 1} of 1024</span>
          </KvRow>
          <KvRow k="Version">
            {b.version} <span className="text-muted">· votes {b.votes}</span>
          </KvRow>
          <KvRow k="Parent">
            <BlockIdLink id={b.parentId} />
            <CopyButton value={b.parentId} />
          </KvRow>
          <KvRow k="Size">
            <span className="tabular-nums">{fmtInt(b.size)} bytes</span>
          </KvRow>
        </Kv>
      </KvCard>

      <Panel>
        <Tabs
          tabs={[
            { id: 'txs', label: 'Transactions', n: txs.length, render: () => (txs.length ? txs.map((t) => <TxCard key={t.id} t={t} />) : <Empty>No transactions.</Empty>) },
            {
              id: 'header',
              label: 'Header',
              render: () => (
                <div className="panel-b">
                  <Kv>
                    <KvRow k="Block id">{hash(b.id)}</KvRow>
                    <KvRow k="Parent id">{hash(b.parentId)}</KvRow>
                    <KvRow k="State root">{hash(b.stateRoot)}</KvRow>
                    <KvRow k="Transactions root">{hash(b.txRoot)}</KvRow>
                    <KvRow k="AD proofs root">{hash(b.adRoot)}</KvRow>
                    <KvRow k="Extension hash">{hash(b.extHash)}</KvRow>
                    <KvRow k="Timestamp">
                      <span className="font-mono">{b.timestamp}</span> <span className="text-muted">ms since epoch</span>
                    </KvRow>
                    <KvRow k="Version">{b.version}</KvRow>
                    <KvRow k="Votes">
                      <span className="font-mono">{b.votes}</span>
                    </KvRow>
                  </Kv>
                </div>
              ),
            },
            {
              id: 'pow',
              label: 'PoW solution',
              render: () => (
                <div className="panel-b">
                  <Kv>
                    <KvRow k="Algorithm">{b.version >= 2 ? 'Autolykos v2' : 'Autolykos v1'}</KvRow>
                    <KvRow k="pk (miner)">{hash(b.pow.pk)}</KvRow>
                    <KvRow k="w">{hash(b.pow.w)}</KvRow>
                    <KvRow k="n (nonce)">
                      <span className="font-mono">{b.pow.n}</span>
                    </KvRow>
                    <KvRow k="d">{hash(b.pow.d)}</KvRow>
                  </Kv>
                  <p className="mt-2.5 text-sm text-muted">pk is the miner’s public key and n the nonce; in Autolykos v2 w and d are fixed.</p>
                </div>
              ),
            },
            // mounted only when the tab is open, so the raw block is fetched on demand
            { id: 'ext', label: 'Extension', render: () => <BlockExtension id={b.id} extHash={b.extHash} /> },
          ]}
        />
      </Panel>
    </main>
  )
}

// the block extension from the node's raw block: NiPoPoW interlinks, voted system parameters, other fields
function BlockExtension({ id, extHash }) {
  const q = useApi(() => getBlockRaw(id), [id])
  if (q.error && !q.data) return <Empty className="text-crit">{q.error.message}</Empty>
  if (q.data === undefined) return <Loading />
  const ext = q.data && q.data.extension
  if (!ext) return <Empty>The node did not return an extension for this block.</Empty>
  const { parameters, interlinks, other } = decodeExtension(ext.fields)
  const hex = (v) => <span className="hash-full">{v}</span>
  return (
    <>
      <div className="panel-b">
        <Kv>
          <KvRow k="Extension hash">{hex(extHash)}</KvRow>
          <KvRow k="Fields">
            <span className="tabular-nums">{ext.fields.length}</span>{' '}
            <span className="text-muted">
              · {interlinks.length} interlink{interlinks.length === 1 ? '' : 's'}
              {parameters.length > 0 && ` · ${parameters.length} system parameters`}
              {other.length > 0 && ` · ${other.length} other`}
            </span>
          </KvRow>
        </Kv>
      </div>

      {parameters.length > 0 && (
        <Section title="System parameters" note="Voted by miners; written in the first block of each 1024-block epoch.">
          <Table cols={['Parameter', '>Value', 'Unit', 'Key']}>
            {parameters.map((p) => (
              <tr key={p.key}>
                <td>{p.name}</td>
                <td className="r">{p.value == null ? <span className="font-mono text-sm text-muted">{p.raw}</span> : fmtInt(p.value)}</td>
                <td className="text-muted">{p.unit}</td>
                <td className="mono">{p.key}</td>
              </tr>
            ))}
          </Table>
        </Section>
      )}

      {interlinks.length > 0 && (
        <Section title="NiPoPoW interlinks" note="For each superblock level, the latest block of that level — what lets light clients verify the chain with a few headers.">
          <Table cols={['Levels', 'Block', 'Key']}>
            {interlinks.map((l) => (
              <tr key={l.key}>
                <td className="tabular-nums">{l.count > 1 ? `${l.fromLevel} – ${l.toLevel}` : l.fromLevel}</td>
                <td>
                  <BlockIdLink id={l.blockId} a={12} b={10} />
                </td>
                <td className="mono">{l.key}</td>
              </tr>
            ))}
          </Table>
        </Section>
      )}

      {other.length > 0 && (
        <Section title="Other fields">
          <Table cols={['Field', 'Key', 'Value']}>
            {other.map((f) => (
              <tr key={f.key}>
                <td>{f.name}</td>
                <td className="mono">{f.key}</td>
                <td className="font-mono text-sm break-all whitespace-normal">{f.raw}</td>
              </tr>
            ))}
          </Table>
        </Section>
      )}
    </>
  )
}

const Section = ({ title, note, children }) => (
  <div className="border-t border-line">
    <div className="px-4 pt-3 pb-2">
      <h3 className="font-semibold text-ink normal-case tracking-normal text-base">{title}</h3>
      {note && <div className="text-sm text-muted">{note}</div>}
    </div>
    {children}
  </div>
)

// compact input/output summary of a transaction inside a block
function TxCard({ t }) {
  return (
    <div className="grid grid-cols-2 items-start gap-x-6 gap-y-1.5 border-b border-line px-4 py-3 last:border-b-0 max-[700px]:grid-cols-1">
      <div className="col-span-full flex flex-wrap items-center justify-between gap-3">
        <span className="inline-flex items-center gap-1.5">
          <TxLink id={t.id} a={14} b={10} />
          <CopyButton value={t.id} />
          <KindPill kind={t.kind} />
        </span>
        <span className="text-meta tabular-nums text-muted">
          {erg(totalValue(t.outputs), 4)} ERG · fee {erg(t.fee, 4)} · {bytes(t.size)}
        </span>
      </div>
      <BoxSummary caption="Inputs" boxes={t.inputs} />
      <BoxSummary caption="Outputs" boxes={t.outputs} />
    </div>
  )
}

function BoxSummary({ caption, boxes }) {
  return (
    <div className="flex min-w-0 flex-col gap-0.75 text-meta">
      <div className="text-2xs font-semibold uppercase tracking-[.05em] text-faint">
        {caption} ({boxes.length})
      </div>
      {boxes.slice(0, 4).map((o) => (
        <div key={o.boxId} className="flex min-w-0 justify-between gap-2.5">
          <span className="truncate font-mono text-sm">
            <AddressLink address={o.address} a={10} b={8} />
            {o.assets.length > 0 && (
              <>
                {' '}
                <Tag>
                  +{o.assets.length} token{o.assets.length > 1 ? 's' : ''}
                </Tag>
              </>
            )}
          </span>
          <span className="whitespace-nowrap tabular-nums">{erg(o.value, 4)} ERG</span>
        </div>
      ))}
      {boxes.length > 4 && <div className="text-muted">… {boxes.length - 4} more</div>}
    </div>
  )
}
