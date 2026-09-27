import { useCallback, useMemo } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import { CHARTS, FEATURED, GROUPS, RANGES, fmtValue, pointLabel, series } from '../charts-meta.js'
import { useApi } from '../hooks.js'
import { LineChart } from '../components/Chart.jsx'
import { Panel, Crumbs, PageHead, Sub, Table, Segmented, Empty, Loading } from '../components/ui.jsx'
import { fmtInt, fmtCompact } from '../format.js'

const changePct = (pts) => (pts && pts.length > 1 && pts[0].v ? ((pts[pts.length - 1].v - pts[0].v) / pts[0].v) * 100 : null)
const signed = (pct, dp) => (pct == null ? '—' : `${pct >= 0 ? '+' : '−'}${Math.abs(pct).toFixed(dp)}%`)

export function Charts() {
  const { chart } = useParams()
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const meta = CHARTS.find((c) => c.key === (chart || params.get('m'))) || CHARTS[0]
  const range = RANGES.find((r) => r[0] === params.get('r')) || RANGES[1]
  const view = params.get('v') === 'table' ? 'table' : 'chart'
  const q = useApi(() => series(meta, range[1]), [meta.key, range[1]])
  const points = q.data || []
  const fmt = useCallback((v) => `${fmtValue(meta, v)} ${meta.unit}`, [meta])
  const href = (o) => '/charts?' + new URLSearchParams({ m: meta.key, r: range[0], v: view, ...o })

  return (
    <main className="wrap">
      <Crumbs>Charts</Crumbs>
      <PageHead title={<h1>Charts</h1>} sub={<Sub>Network metrics computed from the index · {CHARTS.length} series, daily (UTC days) except the hourly mempool series</Sub>} />
      <div className="mb-5 grid grid-cols-4 gap-3 max-[1000px]:grid-cols-2">
        {FEATURED.map((c) => (
          <MetricCard key={c.key} meta={c} to={href({ m: c.key })} on={c.key === meta.key} />
        ))}
      </div>

      <Panel>
        <div className="flex flex-wrap items-start justify-between gap-3 border-b border-line px-4 py-3">
          <div className="min-w-0">
            <h2>
              {meta.title} <span className="text-meta font-normal text-muted">· {meta.unit}</span>
            </h2>
            <div className="text-meta text-muted">{meta.desc}</div>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <select
              aria-label="Metric"
              value={meta.key}
              onChange={(e) => navigate(href({ m: e.target.value }))}
              className="h-8 max-w-[220px] cursor-pointer rounded-md border border-line-strong bg-surface px-2 text-base text-ink focus:border-accent focus:outline-none"
            >
              {GROUPS.map(([group, metrics]) => (
                <optgroup key={group} label={group}>
                  {metrics.map((c) => (
                    <option key={c.key} value={c.key}>
                      {c.title}
                    </option>
                  ))}
                </optgroup>
              ))}
            </select>
            <Segmented items={RANGES.map(([l]) => ({ label: l, to: href({ r: l }), on: l === range[0] }))} />
            <Segmented
              items={[
                { label: 'Chart', to: href({ v: 'chart' }), on: view === 'chart' },
                { label: 'Table', to: href({ v: 'table' }), on: view === 'table' },
              ]}
            />
            <button className="btn" disabled={!points.length} onClick={() => downloadCsv(meta, range, points)}>
              Download CSV
            </button>
          </div>
        </div>
        {points.length > 0 && <Summary meta={meta} range={range} points={points} />}
        {q.error && !q.data ? (
          <Empty className="text-crit">{q.error.message}</Empty>
        ) : !q.data ? (
          <Loading />
        ) : !points.length ? (
          <div className="p-8 text-center text-muted">No data for this range yet.</div>
        ) : view === 'chart' ? (
          <div className="panel-b">
            <LineChart data={points} height={280} axis={fmtCompact} fmt={fmt} label={`${meta.title}, ${range[0]}`} />
          </div>
        ) : (
          <SeriesTable meta={meta} points={points} />
        )}
      </Panel>
    </main>
  )
}

// small card per metric: last 30 days, links to the metric
function MetricCard({ meta, to, on }) {
  const q = useApi(() => series(meta, 30), [meta.key])
  return <CardBody meta={meta} pts={q.data || (q.error ? [] : null)} to={to} on={on} />
}

function CardBody({ meta, pts, to, on }) {
  const change = changePct(pts)
  return (
    <Link
      to={to}
      aria-current={on ? 'true' : 'false'}
      className="panel block p-3.5 text-ink hover:border-line-strong hover:no-underline aria-[current=true]:border-accent aria-[current=true]:ring-1 aria-[current=true]:ring-accent"
    >
      <div className="flex items-baseline justify-between gap-2">
        <span className="truncate text-xs font-semibold uppercase tracking-[.06em] text-muted">{meta.title}</span>
        <span className="whitespace-nowrap text-sm tabular-nums text-muted">{change == null ? '' : `${change >= 0 ? '▲' : '▼'} ${Math.abs(change).toFixed(1)}%`}</span>
      </div>
      <div className="mt-0.5 whitespace-nowrap text-lg font-semibold tabular-nums">
        {pts && pts.length ? fmtValue(meta, pts[pts.length - 1].v) : '—'} <small className="text-sm font-medium text-muted">{meta.unit}</small>
      </div>
      {pts ? <LineChart data={pts} mini label={`${meta.title}, last 30 days`} className="mt-2" /> : <div className="mt-2 h-11" />}
    </Link>
  )
}

function Summary({ meta, range, points }) {
  const vals = points.map((p) => p.v)
  const cells = [
    ['Latest', fmtValue(meta, vals[vals.length - 1]), true],
    [`Change · ${range[0]}`, signed(changePct(points), 1), false],
    ['Low', fmtValue(meta, Math.min(...vals)), true],
    ['High', fmtValue(meta, Math.max(...vals)), true],
    ['Average', fmtValue(meta, vals.reduce((s, x) => s + x, 0) / vals.length), true],
  ]
  return (
    <div className="grid grid-cols-5 border-b border-line max-[700px]:grid-cols-2">
      {cells.map(([k, v, withUnit]) => (
        <div key={k} className="min-w-0 px-4 py-3">
          <div className="text-xs font-semibold uppercase tracking-[.06em] text-muted">{k}</div>
          <div className="truncate text-lg font-semibold tabular-nums">
            {v}
            {withUnit && (
              <>
                {' '}
                <small className="text-sm font-medium text-muted">{meta.unit}</small>
              </>
            )}
          </div>
        </div>
      ))}
    </div>
  )
}

// newest first; long ranges are sampled (daily series: one row per week, hourly: one per day) — the CSV keeps every point
function SeriesTable({ meta, points }) {
  const sampled = points.length > 120
  const step = meta.hourly ? 24 : 7
  const sampleUnit = meta.hourly ? 'day' : 'week'
  const rows = useMemo(() => {
    const list = (sampled ? points.filter((_, i) => (points.length - 1 - i) % step === 0) : points).slice().reverse()
    return list.map((d, i) => ({
      time: pointLabel(meta, d.t),
      value: fmtValue(meta, d.v),
      change: list[i + 1] && list[i + 1].v ? signed(((d.v - list[i + 1].v) / list[i + 1].v) * 100, 2) : '—',
    }))
  }, [meta, points, sampled, step])
  return (
    <>
      <div className="max-h-[560px] overflow-y-auto">
        <Table
          cols={[
            meta.hourly ? 'Hour (UTC)' : 'Date',
            `>${meta.title} (${meta.unit})`, // hourly series have holes where nothing was recorded, so they compare with the previous reading, not "the prior hour"
            `>Change vs ${sampled ? `prior ${sampleUnit}` : meta.hourly ? 'previous reading' : 'prior day'}`,
          ]}
        >
          {rows.map((r) => (
            <tr key={r.time}>
              <td className="font-mono text-sm">{r.time}</td>
              <td className="r">{r.value}</td>
              <td className="r text-muted">{r.change}</td>
            </tr>
          ))}
        </Table>
      </div>
      {sampled && (
        <div className="panel-f">
          <span>
            One row per {sampleUnit} of {fmtInt(points.length)} {meta.hourly ? 'hourly' : 'daily'} values · the CSV has every point
          </span>
        </div>
      )}
    </>
  )
}

function downloadCsv(meta, range, points) {
  const csv = `${meta.hourly ? 'hour_utc' : 'date'},${meta.key} (${meta.unit})\n` + points.map((p) => `${pointLabel(meta, p.t)},${p.v}`).join('\n')
  const a = document.createElement('a')
  a.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv' }))
  a.download = `ergo-${meta.key}-${range[0].toLowerCase()}.csv`
  a.click()
  setTimeout(() => URL.revokeObjectURL(a.href), 1000)
}
