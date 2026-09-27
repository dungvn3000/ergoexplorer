import { useState } from 'react'
import { useParams, useSearchParams } from 'react-router'
import { getAddress, getAddressBoxes, API_BASE } from '../api.js'
import { useApi } from '../hooks.js'
import { Loaded, QrCode } from '../components/widgets.jsx'
import {
  Panel,
  PanelHead,
  Aside,
  Crumbs,
  PageHead,
  Table,
  Pill,
  DirBadge,
  IdLine,
  RawJsonButton,
  Empty,
  Loading,
  BlockLink,
  TxLink,
  BoxLink,
  TokenLink,
  TokenChips,
  Pager,
  PagerButtons,
  Tabs,
} from '../components/ui.jsx'
import { fmtInt, erg, ago, short, tokAmt } from '../format.js'
import { addressLabel, tokenName } from '../labels.js'

const PER_PAGE = 20
const plural = (n, one, many) => `${fmtInt(n)} ${n === 1 ? one : many}`

export function Address() {
  const { address } = useParams()
  const [params] = useSearchParams()
  const page = Math.max(1, +params.get('page') || 1)
  const q = useApi(() => getAddress(address, page, PER_PAGE), [address, page])
  return (
    <Loaded q={q} kind="address" id={address}>
      {(d) => <AddressPage key={d.address} d={d} page={page} />}
    </Loaded>
  )
}

function AddressPage({ d, page }) {
  const pages = Math.max(1, Math.ceil(d.txCount / PER_PAGE))
  return (
    <main className="wrap">
      <Crumbs>Address</Crumbs>
      <PageHead
        title={
          <>
            <h1>{addressLabel(d.address, d.label) || 'Address'}</h1>
            <Pill>{d.contract ? 'P2S · script' : 'P2PK'}</Pill>
            {d.unconfirmed !== 0 && <Pill tone="warn">Unconfirmed activity</Pill>}
          </>
        }
        sub={<IdLine id={d.address} />}
        actions={<RawJsonButton href={`${API_BASE}/addresses/${d.address}`} />}
      />

      <div className="grid-21">
        <Panel>
          <div className="panel-b">
            <div className="flex flex-wrap items-end gap-x-10 gap-y-6">
              <QrCode text={d.address} />
              <Figure
                label="Confirmed balance"
                value={erg(d.balance)}
                unit="ERG"
                note={d.unconfirmed ? `${d.unconfirmed > 0 ? '+' : '−'} ${erg(Math.abs(d.unconfirmed), 4)} ERG unconfirmed` : d.boxes != null ? plural(d.boxes, 'unspent box', 'unspent boxes') : null}
              />
              <Figure
                label="Tokens"
                value={fmtInt(d.tokens.length)}
                note={<span className="block max-w-[220px] truncate">{d.tokens.length ? d.tokens.map(tokenName).join(', ') : 'none held'}</span>}
              />
              <Figure label="Transactions" value={fmtInt(d.txCount)} note={d.firstSeen ? `first ${ago(d.firstSeen)} · last ${ago(d.lastSeen)}` : null} />
            </div>
          </div>
        </Panel>
        <Panel>
          <PanelHead title="Script">
            <Aside>{d.contract ? 'ErgoTree' : 'proveDlog'}</Aside>
          </PanelHead>
          <div className="panel-b">
            <div className="hash-full max-h-24 overflow-auto text-muted">{d.ergoTree || 'Script not available.'}</div>
            <p className="mt-2.5 text-sm text-muted">
              {d.contract ? 'Pay-to-script address. Boxes can be spent only when the ErgoTree evaluates to true.' : 'Pay-to-public-key. Spending requires a Schnorr signature for the embedded key.'}
            </p>
          </div>
        </Panel>
      </div>

      {d.tokens.length > 0 && (
        <Panel className="mb-5">
          <PanelHead title="Token balances">
            <Aside>{d.tokens.length}</Aside>
          </PanelHead>
          <Table cols={['Token', '>Amount', 'Token id', '>Decimals']}>
            {d.tokens.map((t) => (
              <tr key={t.tokenId}>
                <td>
                  <TokenLink id={t.tokenId} name={t.name} />
                </td>
                <td className="r">{tokAmt(t.amount, t.decimals)}</td>
                <td className="mono">{short(t.tokenId, 12, 8)}</td>
                <td className="r">{t.decimals ?? '—'}</td>
              </tr>
            ))}
          </Table>
        </Panel>
      )}

      <Panel>
        <Tabs
          tabs={[
            {
              id: 'txs',
              label: 'Transactions',
              n: fmtInt(d.txCount),
              render: () => (
                <>
                  {d.txs.length ? (
                    <Table cols={['Transaction', 'Block', 'Age', '', '>Amount', 'Tokens', '>Fee']}>
                      {d.txs.map((t) => (
                        <tr key={t.id}>
                          <td>
                            <TxLink id={t.id} a={12} b={8} />
                          </td>
                          <td>
                            <BlockLink height={t.height} />
                          </td>
                          <td className="text-muted">{ago(t.timestamp)}</td>
                          <td>
                            <DirBadge dir={t.dir} />
                          </td>
                          <td className={`r ${t.amount > 0 ? 'text-good' : ''}`}>
                            {t.amount > 0 ? '+' : t.amount < 0 ? '−' : ''}
                            {erg(Math.abs(t.amount), 4)} ERG
                          </td>
                          <td>
                            <TokenChips assets={t.tokens} />
                          </td>
                          <td className="r text-muted">{erg(t.fee, 4)}</td>
                        </tr>
                      ))}
                    </Table>
                  ) : (
                    <Empty>No transactions.</Empty>
                  )}
                  <div className="panel-f">
                    <span>Newest first</span>
                    <Pager page={page} pages={pages} hrefFor={(p) => `/address/${d.address}?page=${p}`} />
                  </div>
                </>
              ),
            },
            { id: 'boxes', label: 'Unspent boxes', n: d.boxes != null ? fmtInt(d.boxes) : null, render: () => <UnspentBoxes address={d.address} /> },
          ]}
        />
      </Panel>
    </main>
  )
}

const Figure = ({ label, value, unit, note }) => (
  <div>
    <div className="text-meta text-muted">{label}</div>
    <div className="text-4xl leading-[1.15] font-semibold tracking-[-.02em] tabular-nums">
      {value}
      {unit && <small className="ml-1 text-md font-medium text-muted">{unit}</small>}
    </div>
    <div className="text-meta text-muted">{note || ' '}</div>
  </div>
)

// mounted when its tab opens, so the request only happens on demand
function UnspentBoxes({ address }) {
  const [page, setPage] = useState(1)
  const q = useApi(() => getAddressBoxes(address, page, PER_PAGE), [address, page])
  if (q.error && !q.data) return <Empty className="text-crit">{q.error.message}</Empty>
  if (!q.data) return <Loading />
  const { items, total } = q.data
  return (
    <>
      {items.length ? (
        <Table cols={['Box', '>Value', 'Tokens', '>Created at', 'Transaction']}>
          {items.map((b) => (
            <tr key={b.boxId}>
              <td>
                <BoxLink id={b.boxId} />
              </td>
              <td className="r">{erg(b.value, 4)} ERG</td>
              <td>
                <TokenChips assets={b.assets} max={3} />
              </td>
              <td className="r">{fmtInt(b.creationHeight)}</td>
              <td>
                <TxLink id={b.transactionId} a={8} b={6} />
              </td>
            </tr>
          ))}
        </Table>
      ) : (
        <Empty>No unspent boxes.</Empty>
      )}
      <div className="panel-f">
        <span>{plural(total, 'unspent box', 'unspent boxes')}</span>
        <PagerButtons page={page} pages={Math.max(1, Math.ceil(total / PER_PAGE))} onPage={(p) => p >= 1 && setPage(p)} />
      </div>
    </>
  )
}
