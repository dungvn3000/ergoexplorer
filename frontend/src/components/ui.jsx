// Small building blocks shared by the pages. Class sets live in styles.css (.panel, .tbl, .kv, .pill …).
import { Children, Fragment, useState } from 'react'
import { Link } from 'react-router'
import { useNotify } from '../app-context.js'
import { short, fmtInt, tokAmt, tokenColor } from '../format.js'
import { addressLabel, tokenName } from '../labels.js'
import { ChevronLeft, ChevronRight, Copy, ExternalLink as ExternalLinkIcon } from 'lucide-react'

/* ── layout ────────────────────────────── */
export const Panel = ({ className = '', children }) => <div className={`panel ${className}`}>{children}</div>

export const PanelHead = ({ title, children }) => (
  <div className="panel-h">
    <h2>{title}</h2>
    {children}
  </div>
)

export const Aside = ({ children }) => <span className="aside">{children}</span>

export const MoreLink = ({ to, children }) => (
  <Link className="more" to={to}>
    {children}
  </Link>
)

// <Crumbs><Link to="/blocks">Blocks</Link>Block 12</Crumbs> — Home is always first
export function Crumbs({ children }) {
  return (
    <div className="crumbs">
      <Link to="/">Home</Link>
      {Children.toArray(children).map((item, i) => (
        <Fragment key={i}>
          <span className="sep">/</span>
          {item}
        </Fragment>
      ))}
    </div>
  )
}

export const PageHead = ({ title, sub, actions }) => (
  <div className="phead">
    <div className="phead-id">
      <div className="phead-title">{title}</div>
      {sub}
    </div>
    {actions && <div className="phead-acts">{actions}</div>}
  </div>
)

export const Sub = ({ children }) => <div className="text-muted">{children}</div>

// key/value list: <Kv><KvRow k="Size">1 KB</KvRow>…</Kv>; a KvCard holds two of them side by side
export const KvCard = ({ children }) => (
  <Panel className="mb-5">
    <div className="panel-b">
      <div className="kv2">{children}</div>
    </div>
  </Panel>
)
export const Kv = ({ children }) => <dl className="kv">{children}</dl>
export const KvRow = ({ k, children }) => (
  <>
    <dt>{k}</dt>
    <dd>{children}</dd>
  </>
)

// cols: header labels; a leading '>' right-aligns the column
export const Table = ({ cols, children }) => (
  <div className="tbl-wrap">
    <table className="tbl">
      <thead>
        <tr>
          {cols.map((c, i) =>
            c.startsWith('>') ? (
              <th key={i} className="r">
                {c.slice(1)}
              </th>
            ) : (
              <th key={i}>{c}</th>
            ),
          )}
        </tr>
      </thead>
      <tbody>{children}</tbody>
    </table>
  </div>
)

export const Stats = ({ className = 'grid-cols-4 max-[1000px]:grid-cols-2', children }) => <div className={`stats ${className}`}>{children}</div>

export const Stat = ({ k, v, unit, d, dClass = '' }) => (
  <div className="stat">
    <div className="stat-k">{k}</div>
    <div className="stat-v">
      {v}
      {unit && v !== '—' && <small>{unit}</small>}
    </div>
    <div className={`stat-d ${dClass}`}>{d}</div>
  </div>
)

export const Empty = ({ children, className = '' }) => <div className={`empty ${className}`}>{children}</div>

export const Spinner = ({ className = '' }) => <span className={`spinner ${className}`} />
export const Loading = ({ className = '' }) => (
  <div className={`loading ${className}`}>
    <Spinner />
    Loading…
  </div>
)

/* ── pills ─────────────────────────────── */
// one hue per transaction type (the backend emits Block reward / Transfer / Token transfer / Token issue / Token burn / Re-emission)
const KIND_TONE = {
  'Block reward': 'good',
  Transfer: 'accent',
  'Token transfer': 'violet',
  'Token issue': 'warn',
  'Token burn': 'crit',
  'Re-emission': 'good',
  'DEX swap': 'teal',
  'Bank mint': 'orange',
  'Bridge lock': 'rose',
  Consolidation: 'neutral',
}
const DIR_TONE = { in: 'good', out: 'neutral', self: 'warn' }

export const Pill = ({ tone = 'neutral', children }) => <span className={`pill tone-${tone}`}>{children}</span>
export const KindPill = ({ kind }) => <Pill tone={KIND_TONE[kind] || 'neutral'}>{kind}</Pill>
export const DirBadge = ({ dir }) => <span className={`dir tone-${DIR_TONE[dir] || 'neutral'}`}>{String(dir).toUpperCase()}</span>
export const Tag = ({ children }) => <span className="tag">{children}</span>

/* ── ids, copy ─────────────────────────── */
export function CopyButton({ value }) {
  const notify = useNotify()
  const copy = () => {
    if (navigator.clipboard) navigator.clipboard.writeText(value).catch(() => {})
    notify('Copied ' + short(value, 6, 4))
  }
  return (
    <button className="copy" title="Copy" aria-label="Copy" onClick={copy}>
      <Copy size={13} />
    </button>
  )
}

// link that leaves the explorer: new tab, no referrer, small ↗ marker
export const ExternalLink = ({ href, className = '', children }) => (
  <a href={href} target="_blank" rel="noopener noreferrer" className={className}>
    {children}
    <ExternalLinkIcon size={11} className="ml-1 inline align-baseline text-faint" />
  </a>
)

// preformatted snippet (config, command) with a copy button
export const Snippet = ({ children }) => (
  <div className="relative mt-1 mb-3">
    <pre className="overflow-x-auto whitespace-pre-wrap break-all rounded-md border border-line bg-surface-2 py-2 pl-3 pr-9 font-mono text-meta">{children}</pre>
    <span className="absolute top-1.5 right-1.5">
      <CopyButton value={children} />
    </span>
  </div>
)

export const IdLine = ({ id }) => (
  <div className="idline">
    <span>{id}</span>
    <CopyButton value={id} />
  </div>
)

export const RawJsonButton = ({ href }) => (
  <a className="btn" href={href} target="_blank" rel="noopener noreferrer">
    Raw JSON
  </a>
)

/* ── links ─────────────────────────────── */
// shows the address label when known, else a shortened address
export function AddressLink({ address, label, a = 9, b = 6 }) {
  const name = addressLabel(address, label)
  return (
    <Link className="lbl" to={`/address/${address}`} title={address}>
      <span className={name ? 'lbl-name' : 'font-mono'}>{name || short(address, a, b)}</span>
    </Link>
  )
}
export const BlockLink = ({ height }) => (
  <Link className="tabular-nums" to={`/block/${height}`}>
    {fmtInt(height)}
  </Link>
)
const HashLink = ({ to, id, a = 10, b = 8 }) => (
  <Link className="font-mono" to={to} title={id}>
    {short(id, a, b)}
  </Link>
)
export const BlockIdLink = ({ id, a, b }) => <HashLink to={`/block/${id}`} id={id} a={a} b={b} />
export const TxLink = ({ id, a, b }) => <HashLink to={`/tx/${id}`} id={id} a={a} b={b} />
export const BoxLink = ({ id, a, b }) => <HashLink to={`/box/${id}`} id={id} a={a} b={b} />

/* ── tokens ────────────────────────────── */
export const TokenIcon = ({ id, name, className = '' }) => (
  <i className={`tok-icon ${className}`} style={{ background: tokenColor(id) }}>
    {String(name || '?')
      .slice(0, 2)
      .toUpperCase()}
  </i>
)
// asset inside a box or balance: { tokenId, name, decimals, amount }
export const TokenChip = ({ asset }) => (
  <Link className="tok-chip" to={`/token/${asset.tokenId}`} title={tokenName(asset)}>
    <TokenIcon id={asset.tokenId} name={tokenName(asset)} />
    <span>{tokAmt(asset.amount, asset.decimals)}</span> {tokenName(asset)}
  </Link>
)
export const TokenChips = ({ assets, max = Infinity }) =>
  assets.length ? (
    <div className="flex flex-wrap gap-1">
      {assets.slice(0, max).map((a) => (
        <TokenChip key={a.tokenId} asset={a} />
      ))}
      {assets.length > max && <span className="text-sm text-muted">+{assets.length - max}</span>}
    </div>
  ) : (
    <span className="text-faint">—</span>
  )
export const TokenLink = ({ id, name }) => (
  <Link className="lbl" to={`/token/${id}`}>
    <TokenIcon id={id} name={name || id} />
    <span className="lbl-name">{name || short(id, 6, 4)}</span>
  </Link>
)

/* ── paging, segmented control, tabs ───── */
// link pager: the page number is part of the route (`hrefFor(page)`)
export const Pager = ({ page, pages, hrefFor }) => (
  <PagerFrame
    page={page}
    pages={pages}
    prev={
      <Link className="btn-pager" to={hrefFor(page - 1)} aria-label="Previous page" aria-disabled={page <= 1 || undefined}>
        <ChevronLeft size={14} strokeWidth={2.2} />
      </Link>
    }
    next={
      <Link className="btn-pager" to={hrefFor(page + 1)} aria-label="Next page" aria-disabled={page >= pages || undefined}>
        <ChevronRight size={14} strokeWidth={2.2} />
      </Link>
    }
  />
)
// button pager: pages in place (`onPage(page)`)
export const PagerButtons = ({ page, pages, onPage }) => (
  <PagerFrame
    page={page}
    pages={pages}
    prev={
      <button className="btn-pager" onClick={() => onPage(page - 1)} aria-label="Previous page" aria-disabled={page <= 1 || undefined}>
        <ChevronLeft size={14} strokeWidth={2.2} />
      </button>
    }
    next={
      <button className="btn-pager" onClick={() => onPage(page + 1)} aria-label="Next page" aria-disabled={page >= pages || undefined}>
        <ChevronRight size={14} strokeWidth={2.2} />
      </button>
    }
  />
)
const PagerFrame = ({ page, pages, prev, next }) => (
  <div className="flex items-center gap-1">
    {prev}
    <span className="px-2 tabular-nums">
      Page {fmtInt(page)} of {fmtInt(Math.max(1, pages))}
    </span>
    {next}
  </div>
)

// items: [{ label, to, on }]
export const Segmented = ({ items }) => (
  <div className="seg">
    {items.map((i) => (
      <Link key={i.label} to={i.to} aria-current={i.on ? 'true' : 'false'}>
        {i.label}
      </Link>
    ))}
  </div>
)

// tabs: [{ id, label, n?, render: () => node }]
export function Tabs({ tabs, onChange }) {
  const [cur, setCur] = useState(tabs[0].id)
  const active = tabs.find((t) => t.id === cur) || tabs[0]
  const select = (id) => {
    setCur(id)
    if (onChange) onChange(id)
  }
  return (
    <>
      <div className="tabs" role="tablist">
        {tabs.map((t) => (
          <button key={t.id} className="tab" role="tab" aria-selected={t.id === active.id} onClick={() => select(t.id)}>
            {t.label}
            {t.n != null && <span className="n">{t.n}</span>}
          </button>
        ))}
      </div>
      <div role="tabpanel">{active.render()}</div>
    </>
  )
}
