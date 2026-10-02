import { useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
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
  KindPill,
} from '../components/ui.jsx'
import { fmtInt, ago, short, tokAmt, tokShare } from '../format.js'

// featured tokens; with ?q= (search box, several tokens by that name) the tokens whose name contains it
export function Tokens() {
  const [params] = useSearchParams()
  const search = (params.get('q') || '').trim()
  const q = useApi(() => getTokens(search), [search])
  return (
    <Loaded q={q} kind="tokens">
      {(tokens) => <TokensPage tokens={tokens} search={search} />}
    </Loaded>
  )
}

function TokensPage({ tokens, search }) {
  return (
    <main className="wrap">
      <Crumbs>{search ? <Link to="/tokens">Tokens</Link> : 'Tokens'}</Crumbs>
      <PageHead
        title={<h1>{search ? `Tokens named “${search}”` : 'Tokens'}</h1>}
        sub={
          <Sub>
            {search
              ? 'Names are not unique: anyone can mint a token with any name. The original is usually the oldest and has the most holders.'
              : `EIP-4 assets on Ergo · ${tokens.length} featured token${tokens.length === 1 ? '' : 's'} · search any token by name above`}
          </Sub>
        }
      />
      <Panel>
        {tokens.length ? (
          <Table cols={['Token', 'Token id', '>Decimals', '>Total supply', ...(search ? ['>Holders'] : []), '>Issued at']}>
            {tokens.map((t) => (
              <tr key={t.id}>
                <td>
                  <TokenLink id={t.id} name={t.name} />
                </td>
                <td className="mono">{short(t.id, 14, 10)}</td>
                <td className="r">{t.decimals}</td>
                <td className="r">{tokAmt(t.supply, t.decimals)}</td>
                {search && <td className="r">{t.holderCount != null ? fmtInt(t.holderCount) : '—'}</td>}
                <td className="r">{t.issueHeight ? <BlockLink height={t.issueHeight} /> : '—'}</td>
              </tr>
            ))}
          </Table>
        ) : (
          <Empty>No token name contains “{search}”. Names need at least 2 characters.</Empty>
        )}
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
            <Table cols={['Transaction', 'From', 'To', '>Amount']}>
              {t.transfers.map((x) => (
                <tr key={x.id}>
                  <td>
                    <div className="flex items-center gap-2">
                      <TxLink id={x.id} a={8} b={6} />
                      {x.kind && <KindPill kind={x.kind} />}
                    </div>
                    <div className="text-xs text-muted">
                      <BlockLink height={x.height} /> · {ago(x.timestamp)}
                    </div>
                  </td>
                  <td>
                    <Party address={x.from} more={x.fromMore} empty={x.mint ? 'Mint' : '—'} />
                  </td>
                  <td>
                    <Party address={x.to} more={x.toMore} empty={x.burned > 0 ? <span className="text-crit">Burned</span> : '—'} />
                  </td>
                  <td className="r">
                    {/* a burn with no receiver shows what was destroyed; a partial burn adds it under the amount moved */}
                    {x.to ? tokAmt(x.amount, t.decimals) : tokAmt(x.burned, t.decimals)}
                    {x.to && x.burned > 0 && <div className="text-xs text-crit">{tokAmt(x.burned, t.decimals)} burned</div>}
                  </td>
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

// one side of a transfer: the biggest sender / receiver, "+N" for the others of the transaction
function Party({ address, more, empty }) {
  if (!address) return <span className="text-muted">{empty}</span>
  return (
    <>
      <AddressLink address={address} a={6} b={4} />
      {more > 0 && <span className="text-xs text-muted"> +{more}</span>}
    </>
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
              <td className="r text-muted">{tokShare(h.amount, token.supply) ?? '—'}</td>
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
