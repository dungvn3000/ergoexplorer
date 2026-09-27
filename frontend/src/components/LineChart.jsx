// Area line chart on Chart.js (same setup as the v1 frontend's AreaChart.vue), coloured from the theme's CSS variables.
import { useMemo } from 'react'
import { Line } from 'react-chartjs-2'
import { Chart, LineElement, PointElement, LinearScale, TimeScale, Filler, Tooltip } from 'chart.js'
import 'chartjs-adapter-date-fns'
import { useTheme } from '../app-context.js'

// register only what is used (tree-shaking): line/area, numeric + time axes, fill, tooltip
Chart.register(LineElement, PointElement, LinearScale, TimeScale, Filler, Tooltip)

// the theme argument only keys the memo: colours are re-read from CSS after a theme switch
const readColors = (_theme) => {
  const css = getComputedStyle(document.documentElement)
  const v = (name) => css.getPropertyValue(name).trim()
  return { accent: v('--color-accent'), line: v('--color-line'), lineStrong: v('--color-line-strong'), muted: v('--color-muted'), ink: v('--color-ink'), surface: v('--color-surface') }
}

/*
 * data: [{ t: epoch ms, v: number }]
 * mini: sparkline without axes or tooltip (metric cards)
 * fmt: tooltip value text; axis: y tick text
 *
 * react-chartjs-2 calls chart.update() whenever the data or options object is new, so both are memoized:
 * pass a stable `data` array and stable `fmt` / `axis` functions (module-level or memoized in the caller).
 */
export function LineChart({ data, mini = false, height, fmt = String, axis = String, label = '', className = '' }) {
  const { theme } = useTheme()
  const c = useMemo(() => readColors(theme), [theme])

  // daily series (points at 00:00 UTC) are shifted into local time so day labels show the UTC day in every time zone
  const daily = data.length > 0 && data.every((d) => d.t % 86400000 === 0)

  const chartData = useMemo(() => {
    const xOf = (t) => (daily ? t + new Date(t).getTimezoneOffset() * 60000 : t)
    // missing buckets (the series has holes where nothing was recorded) break the line instead of being bridged
    const steps = data
      .slice(1)
      .map((d, i) => d.t - data[i].t)
      .sort((a, b) => a - b)
    const step = steps.length ? steps[steps.length >> 1] : 0
    const points = []
    data.forEach((d, i) => {
      if (i > 0 && step && d.t - data[i - 1].t > step * 1.5) points.push({ x: xOf(data[i - 1].t + step), y: null })
      points.push({ x: xOf(d.t), y: d.v })
    })
    // a reading with a hole on both sides has no line to show it, so it gets a dot
    const isolated = new Set(points.flatMap((p, i) => (p.y !== null && (points[i - 1]?.y ?? null) === null && (points[i + 1]?.y ?? null) === null ? [i] : [])))
    const radius = mini || data.length > 40 ? 0 : 3
    return {
      datasets: [
        {
          label,
          data: points,
          borderColor: c.accent,
          backgroundColor: c.accent + '2e',
          fill: 'origin',
          borderWidth: mini ? 1.5 : 2,
          tension: 0.3,
          pointRadius: (ctx) => (isolated.has(ctx.dataIndex) ? (mini ? 1.5 : 2.5) : radius),
          pointHoverRadius: mini ? 0 : 5,
          pointBackgroundColor: c.surface,
          pointBorderColor: c.accent,
          pointBorderWidth: 2,
        },
      ],
    }
  }, [data, daily, mini, label, c])

  const options = useMemo(() => {
    const tick = { color: c.muted, font: { size: 11 } }
    return mini
      ? {
          responsive: true,
          maintainAspectRatio: false,
          animation: false,
          events: [],
          plugins: { tooltip: { enabled: false } },
          scales: { x: { type: 'time', display: false }, y: { display: false } },
        }
      : {
          responsive: true,
          maintainAspectRatio: false,
          animation: false,
          interaction: { mode: 'index', intersect: false },
          plugins: {
            tooltip: {
              backgroundColor: c.surface,
              titleColor: c.muted,
              bodyColor: c.ink,
              borderColor: c.line,
              borderWidth: 1,
              padding: 8,
              displayColors: false,
              callbacks: { label: (ctx) => fmt(ctx.parsed.y) },
            },
          },
          scales: {
            x: {
              type: 'time',
              time: {
                minUnit: daily ? 'day' : 'hour',
                tooltipFormat: daily ? 'dd MMM yyyy' : 'dd MMM HH:mm',
                displayFormats: { hour: 'HH:mm', day: 'dd MMM', week: 'dd MMM', month: "MMM ''yy", year: 'yyyy' },
              },
              grid: { display: false },
              border: { color: c.lineStrong },
              ticks: { ...tick, maxRotation: 0, autoSkipPadding: 24 },
            },
            y: {
              beginAtZero: true,
              grid: { color: c.line },
              border: { display: false },
              ticks: { ...tick, maxTicksLimit: 6, callback: (v) => axis(v) },
            },
          },
        }
  }, [mini, daily, c, fmt, axis])

  return (
    <div className={`relative ${className}`} style={{ height: height || (mini ? 44 : 180) }}>
      <Line data={chartData} options={options} aria-label={label} role="img" />
    </div>
  )
}
