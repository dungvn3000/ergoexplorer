<template>
  <div :style="{ height: height + 'px', position: 'relative' }">
    <Line :data="chartData" :options="options" />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Line } from 'vue-chartjs'
import { Chart, LineElement, PointElement, LinearScale, TimeScale, Filler, Tooltip, Legend } from 'chart.js'
import 'chartjs-adapter-date-fns'

// Register only what is used (tree-shaking): line/area, linear + time scales, area fill, tooltip, legend
Chart.register(LineElement, PointElement, LinearScale, TimeScale, Filler, Tooltip, Legend)

const props = defineProps({
  // [{ t: epoch ms, <key>: number, ... }]
  data: {
    type: Array,
    default: () => [],
  },
  // [{ key, label, color }]
  series: {
    type: Array,
    default: () => [],
  },
  height: {
    type: Number,
    default: 260,
  },
  // Force day granularity (labels/tooltips without a time) when the points are not on 00:00 UTC
  daily: Boolean,
})

defineOptions({
  name: 'AreaChart',
})

// Daily series (points at 00:00 UTC): shift to local time so the day label is the UTC day in every timezone.
// Hourly series (mempool) stay as they are and show local time.
const midnightUtc = computed(() => props.data.length > 0 && props.data.every((d) => d.t % 86400000 === 0))
const daily = computed(() => props.daily || midnightUtc.value)
const xOf = (t) => (midnightUtc.value ? t + new Date(t).getTimezoneOffset() * 60000 : t)

const chartData = computed(() => ({
  datasets: props.series.map((s) => ({
    label: s.label,
    data: props.data.map((d) => ({ x: xOf(d.t), y: d[s.key] })),
    borderColor: s.color,
    backgroundColor: s.color + '2e',
    fill: 'origin',
    borderWidth: 2,
    tension: 0.3,
    pointRadius: props.data.length > 40 ? 0 : 3,
    pointHoverRadius: 5,
    pointBackgroundColor: '#fff',
    pointBorderColor: s.color,
    pointBorderWidth: 2,
  })),
}))

const fmtNum = (v) => (v == null ? '—' : Math.abs(v) >= 1000 ? Math.round(v).toLocaleString('en-US') : v.toLocaleString('en-US', { maximumFractionDigits: 4 }))

const fmtShort = (v) => {
  const a = Math.abs(v)
  if (a >= 1e12) return trim(v / 1e12) + 'T'
  if (a >= 1e9) return trim(v / 1e9) + 'G'
  if (a >= 1e6) return trim(v / 1e6) + 'M'
  if (a >= 1e3) return trim(v / 1e3) + 'k'
  return trim(v)
}
const trim = (v) => (Math.abs(v) >= 100 ? Math.round(v) : Math.abs(v) >= 10 ? +v.toFixed(1) : +v.toFixed(2)).toString()

const TICK = { color: '#8a8f9c', font: { size: 12 } }

const options = computed(() => ({
  responsive: true,
  maintainAspectRatio: false,
  animation: false,
  interaction: { mode: 'index', intersect: false },
  plugins: {
    legend: { display: props.series.length > 1, position: 'top', align: 'start', labels: { boxWidth: 10, boxHeight: 10, usePointStyle: true } },
    tooltip: {
      backgroundColor: '#fff',
      titleColor: '#8a8f9c',
      bodyColor: '#333',
      borderColor: '#e0e3ea',
      borderWidth: 1,
      padding: 8,
      usePointStyle: true,
      boxWidth: 8,
      boxHeight: 8,
      callbacks: {
        label: (c) => ' ' + c.dataset.label + ': ' + fmtNum(c.parsed.y),
      },
    },
  },
  scales: {
    x: {
      type: 'time',
      time: {
        minUnit: daily.value ? 'day' : 'hour',
        tooltipFormat: daily.value ? 'dd MMM yyyy' : 'dd MMM HH:mm',
        displayFormats: { hour: 'HH:mm', day: 'dd MMM', week: 'dd MMM', month: "MMM ''yy", year: 'yyyy' },
      },
      grid: { display: false },
      border: { color: '#d6d9e0' },
      ticks: { ...TICK, maxRotation: 0, autoSkipPadding: 24 },
    },
    y: {
      beginAtZero: true,
      grid: { color: '#eceef3' },
      border: { display: false },
      ticks: { ...TICK, maxTicksLimit: 6, callback: (v) => fmtShort(v) },
    },
  },
}))
</script>
