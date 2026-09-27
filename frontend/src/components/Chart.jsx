// Lazy entry point for charts: Chart.js and its date adapter are loaded in a separate chunk the first time a chart renders.
import { lazy, Suspense } from 'react'

const LazyLineChart = lazy(() => import('./LineChart.jsx').then((m) => ({ default: m.LineChart })))

// same props as LineChart.jsx; until the chunk arrives, an empty box of the chart's height keeps the layout still
export function LineChart(props) {
  const height = props.height || (props.mini ? 44 : 180)
  return (
    <Suspense fallback={<div className={props.className} style={{ height }} aria-hidden="true" />}>
      <LazyLineChart {...props} />
    </Suspense>
  )
}
