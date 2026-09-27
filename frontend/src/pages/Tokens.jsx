import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { getTokens, getToken, getTokenHolders, API_BASE } from '../api.js'
import { useApi } from '../hooks.js'
import { Loaded } from '../components/widgets.jsx'
import {
  Panel,
  PanelHead,
  Aside,
  Crumbs,
  PageHead,
  Sub,
  KvCard,
  Kv,
  KvRow,
  Table,
  Pill,
  IdLine,
  CopyButton,
  RawJsonButton,
  Empty,
  Loading,
  AddressLink,
  BlockLink,
  TxLink,
  BoxLink,
  TokenIcon,
  TokenLink,
  PagerButtons,
} from '../components/ui.jsx'
import { fmtInt, ago, short, tokAmt } from '../format.js'

export function Tokens() {
  const q = useApi(getTokens, [])
  return (
    <Loaded q={q} kind="tokens">
      {(tokens) => <TokensPage tokens={tokens} />}
    </Loaded>
  )
}

function TokensPage({ tokens }) {
  return (
    <main className="wrap">
      <Crumbs>Tokens</Crumbs>
      <PageHead
        title={<h1>Tokens</h1>}
        sub={
          <Sub>
            EIP-4 assets on Ergo · {tokens.length} featured token{tokens.length === 1 ? '' : 's'}
          </Sub>
        }
      />
      <Panel>
        <Table cols={['Token', 'Token id', '>Decimals', '>Total supply', '>Issued at']}>
          {tokens.map((t) => (
            <tr key={t.id}>
              <td>
                <TokenLink id={t.id} name={t.name} />
              </td>
              <td className="mono">{short(t.id, 14, 10)}</td>
              <td className="r">{t.decimals}</td>
              <td className="r">{tokAmt(t.supply, t.decimals)}</td>
              <td className="r">{t.issueHeight ? <BlockLink height={t.issueHeight} /> : '—'}</td>
            </tr>
          ))}
        </Table>
      </Panel>
    </main>
  )
}

export function Token() {
  const { id } = useParams()
  const q = useApi(() => getToken(id), [id])
  return (
    <Loaded q={q} kind="token" id={id}>
      {(t) => <TokenPage key={t.id} t={t} />}
    </Loaded>
  )
}

function TokenPage({ t }) {
  const name = t.name || short(t.id, 6, 4)
  return (
    <main className="wrap">
      <Crumbs>
        <Link to="/tokens">Tokens</Link>
        {name}
      </Crumbs>
      <PageHead
        title={
          <>
            <TokenIcon id={t.id} name={t.name || t.id} className="size-8.5 text-base" />
            <h1>{t.name || 'Token'}</h1>
            <Pill>EIP-4 token</Pill>
            <Pill>{t.decimals} decimals</Pill>
          </>
        }
        sub={<IdLine id={t.id} />}
        actions={<RawJsonButton href={`${API_BASE}/tokens/${t.id}`} />}
      />

      <KvCard>
        <Kv>
          <KvRow k="Description">{t.desc || '—'}</KvRow>
          <KvRow k="Total supply">
            <span className="tabular-nums">{tokAmt(t.supply, t.decimals)}</span> <span className="text-muted">{t.name}</span>
          </KvRow>
          <KvRow k="Holders">
            <span className="tabular-nums">{t.holderCount != null ? fmtInt(t.holderCount) : '—'}</span>
          </KvRow>
        </Kv>
        <Kv>
          <KvRow k="Issued in">
            {t.issueTx ? (
              <>
                <TxLink id={t.issueTx} />
                {t.issueHeight && <span className="text-muted"> · block {fmtInt(t.issueHeight)}</span>}
              </>
            ) : t.issueHeight ? (
              <BlockLink height={t.issueHeight} />
            ) : (
              '—'
            )}
          </KvRow>
          <KvRow k="Issuing box">
            {t.issueBox ? (
              <>
                <BoxLink id={t.issueBox} />
                <CopyButton value={t.issueBox} />
              </>
            ) : (
              '—'
            )}
          </KvRow>
          <KvRow k="Standard">{'EIP-4 · name in R4, description R5, decimals R6'}</KvRow>
        </Kv>
      </KvCard>

      <div className="grid-2">
        <Holders token={t} />
        <Panel>
          <PanelHead title="Recent transfers" />
          {t.transfers && t.transfers.length ? (
            <Table cols={['Transaction', 'Block', 'To', '>Amount']}>
              {/* one transaction can pay the same address in several outputs, so the row key includes the position */}
              {t.transfers.map((x, i) => (
                <tr key={`${x.id}:${i}`}>
                  <td>
                    <TxLink id={x.id} a={8} b={6} />
                    <div className="text-xs text-muted">{ago(x.timestamp)}</div>
                  </td>
                  <td>
                    <BlockLink height={x.height} />
                  </td>
                  <td>
                    <AddressLink address={x.to} a={8} b={5} />
                  </td>
                  <td className="r">{tokAmt(x.amount, t.decimals)}</td>
                </tr>
              ))}
            </Table>
          ) : (
            <Empty>No recent transfers.</Empty>
          )}
        </Panel>
      </div>
    </main>
  )
}

const HOLDERS_PER_PAGE = 10

// pages through /tokens/{id}/holders in place
function Holders({ token }) {
  const [page, setPage] = useState(1)
  const q = useApi(() => getTokenHolders(token.id, page, HOLDERS_PER_PAGE), [token.id, page])
  const d = q.data
  return (
    <Panel>
      <PanelHead title="Holders">
        <Aside>{token.holderCount != null && `${fmtInt(token.holderCount)} addresses`}</Aside>
      </PanelHead>
      {q.error && !d ? (
        <Empty className="text-crit">{q.error.message}</Empty>
      ) : !d ? (
        <Loading />
      ) : !d.items.length ? (
        <Empty>No holders indexed yet.</Empty>
      ) : (
        <Table cols={['#', 'Address', '>Amount', '>Share']}>
          {d.items.map((h, i) => (
            <tr key={h.address}>
              <td className="tabular-nums text-muted">{h.rank ?? (page - 1) * HOLDERS_PER_PAGE + i + 1}</td>
              <td>
                <AddressLink address={h.address} a={10} b={6} />
              </td>
              <td className="r">{tokAmt(h.amount, token.decimals)}</td>
              <td className="r text-muted">{token.supply ? `${((h.amount / token.supply) * 100).toFixed(2)}%` : '—'}</td>
            </tr>
          ))}
        </Table>
      )}
      {d && d.total > HOLDERS_PER_PAGE && (
        <div className="panel-f">
          <span>{fmtInt(d.total)} holders</span>
          <PagerButtons page={page} pages={Math.ceil(d.total / HOLDERS_PER_PAGE)} onPage={(p) => p >= 1 && setPage(p)} />
        </div>
      )}
    </Panel>
  )
}
