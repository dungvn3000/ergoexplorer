<template>
  <div class="row items-center justify-between q-pb-sm">
    <div class="text-h6 text-grey-8"><q-icon name="bar_chart" /> Charts</div>
    <q-btn-toggle v-model="days" no-caps unelevated rounded dense toggle-color="indigo-7" color="grey-3" text-color="grey-8" :options="RANGES" />
  </div>

  <div class="row q-col-gutter-sm q-mb-md">
    <div class="col-12 col-md-4">
      <q-select v-model="metric" :options="METRICS" option-value="key" option-label="label" emit-value map-options dense outlined bg-color="white" label="Metric" />
    </div>
  </div>

  <q-card flat bordered>
    <q-card-section>
      <div class="row items-baseline justify-between">
        <div>
          <div class="text-subtitle1 text-weight-medium">{{ current.label }}</div>
          <div class="text-caption text-grey-6">{{ current.description }}</div>
        </div>
        <div class="text-right" v-if="series && series.points.length">
          <div class="text-caption text-grey-6">Latest</div>
          <div class="text-h6 text-weight-bolder num">
            {{ fmt(series.points[series.points.length - 1].v) }} <span class="text-caption text-grey-6">{{ series.unit }}</span>
          </div>
        </div>
      </div>
    </q-card-section>
    <q-card-section class="q-pt-none" style="position: relative; min-height: 280px">
      <q-inner-loading :showing="loading" color="primary" />
      <AreaChart v-if="chartData.length > 1" :data="chartData" :height="300" :series="[{ key: 'v', label: current.label + (series.unit ? ' (' + series.unit + ')' : ''), color: current.color }]" />
      <div v-else-if="!loading" class="text-grey-6 q-pa-lg text-center">No data for this range yet — the chart fills in as the index catches up.</div>
    </q-card-section>
  </q-card>

  <q-card flat bordered class="q-mt-md" v-if="series && series.points.length">
    <q-card-section class="q-py-sm"><div class="text-subtitle1 text-weight-medium">Data</div></q-card-section>
    <q-table
      flat
      dense
      :rows="tableRows"
      :columns="[
        { name: 't', align: 'left', label: 'Date', field: 't' },
        { name: 'v', align: 'right', label: current.label + (series.unit ? ' (' + series.unit + ')' : ''), field: 'v' },
      ]"
      row-key="t"
      :rows-per-page-options="[10, 30, 100]"
      class="table-sm"
    />
  </q-card>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { format } from 'date-fns'
import AreaChart from 'components/AreaChart.vue'
import { getChart } from 'services/Explorer'

defineOptions({
  name: 'ChartsPage',
})

const RANGES = [
  { label: '30d', value: 30 },
  { label: '90d', value: 90 },
  { label: '1y', value: 365 },
  { label: 'All', value: 0 },
]

const METRICS = [
  { key: 'hashrate', label: 'Network hashrate', description: 'Average difficulty / 120 s, per day', color: '#ff7043' },
  { key: 'difficulty', label: 'Difficulty', description: 'Average block difficulty per day', color: '#6d4c9f' },
  { key: 'blocks', label: 'Blocks per day', description: 'Blocks mined per UTC day', color: '#1976d2' },
  { key: 'blockTime', label: 'Block time', description: 'Average seconds between blocks', color: '#26a69a' },
  { key: 'transactions', label: 'Transactions per day', description: 'Including the emission tx of each block', color: '#1976d2' },
  { key: 'fees', label: 'Fees per day', description: 'Total transaction fees paid to miners', color: '#f88c2b' },
  { key: 'avgFee', label: 'Average fee', description: 'Fees / non-reward transactions', color: '#f88c2b' },
  { key: 'blockSize', label: 'Average block size', description: 'Bytes per block', color: '#546bfa' },
  { key: 'emission', label: 'Miner reward per day', description: 'ERG kept by miners (emission − re-emission part)', color: '#e53935' },
  { key: 'circulatingSupply', label: 'Circulating supply', description: 'Issued − earmarked for re-emission (EIP-27), end of day', color: '#e53935' },
  { key: 'boxesCreated', label: 'Boxes created per day', description: 'Transaction outputs', color: '#1f7a8c' },
  { key: 'boxesSpent', label: 'Boxes spent per day', description: 'Transaction inputs', color: '#1f7a8c' },
  { key: 'utxoSize', label: 'UTXO set size', description: 'Unspent boxes at the end of the day', color: '#1f7a8c' },
  { key: 'activeAddresses', label: 'Active addresses', description: 'Addresses that received or spent a box that day', color: '#26a69a' },
  { key: 'fundedAddresses', label: 'Addresses with balance', description: 'Addresses holding ERG at the end of the day', color: '#26a69a' },
  { key: 'tokenTransfers', label: 'Token transfers per day', description: 'Boxes carrying tokens', color: '#8a6b1f' },
  { key: 'tokensMinted', label: 'Tokens minted per day', description: 'New EIP-4 tokens', color: '#8a6b1f' },
  { key: 'mempoolTxs', label: 'Mempool size', description: 'Unconfirmed transactions, hourly average', color: '#546bfa' },
  { key: 'mempoolBytes', label: 'Mempool bytes', description: 'Bytes waiting, hourly average', color: '#546bfa' },
]

const route = useRoute()
const router = useRouter()
const metric = ref(METRICS.some((m) => m.key === route.params.chart) ? route.params.chart : 'hashrate')
const days = ref(90)
const loading = ref(false)
const series = ref(null)

const current = computed(() => METRICS.find((m) => m.key === metric.value))

const fmt = (v) => (Math.abs(v) >= 1000 ? Math.round(v).toLocaleString('en-US') : v.toLocaleString('en-US', { maximumFractionDigits: 4 }))

const chartData = computed(() => series.value?.points || [])

// Daily series: UTC days; hourly mempool series: local time
const isDaily = computed(() => !!series.value && series.value.points.length > 0 && series.value.points[0].t % 86400000 === 0)
const tableRows = computed(() =>
  series.value?.points ? [...series.value.points].reverse().map((p) => ({ t: isDaily.value ? new Date(p.t).toISOString().slice(0, 10) : format(new Date(p.t), 'yyyy-MM-dd HH:mm'), v: fmt(p.v) })) : [],
)

async function load() {
  loading.value = true
  series.value = await getChart(metric.value, days.value)
  loading.value = false
}

watch([metric, days], load, { immediate: true })
watch(metric, (m) => router.replace('/charts/' + m))
watch(
  () => route.params.chart,
  (c) => {
    if (c && c !== metric.value && METRICS.some((m) => m.key === c)) metric.value = c
  },
)
</script>

<style scoped lang="scss">
.num {
  font-variant-numeric: tabular-nums;
}
</style>
