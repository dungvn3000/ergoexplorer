<template>
  <div class="explorer-home">
    <div class="row items-center justify-between q-pb-sm">
      <div class="text-h6 text-grey-8"><q-icon name="hub" /> Ergo network overview</div>
    </div>

    <div class="row q-col-gutter-sm q-mb-md" v-if="stats">
      <div class="col-6 col-md-4 col-lg-2" v-for="m in metrics" :key="m.label">
        <EStatTile v-bind="m" />
      </div>
    </div>

    <div class="row q-col-gutter-sm q-mb-md">
      <div class="col-12 col-md-8">
        <q-card flat bordered class="full-height">
          <q-card-section>
            <div class="text-subtitle1 text-weight-medium">Network hashrate</div>
            <div class="text-caption text-grey-6">Last 30 days · TH/s</div>
          </q-card-section>
          <q-card-section class="q-pt-none">
            <AreaChart :data="hashrateSeries" :series="[{ key: 'value', label: 'Hashrate (TH/s)', color: '#ff7043' }]" daily />
          </q-card-section>
        </q-card>
      </div>

      <div class="col-12 col-md-4">
        <q-card flat bordered class="full-height">
          <q-card-section>
            <div class="text-subtitle1 text-weight-medium">Blocks by pool</div>
            <div class="text-caption text-grey-6">Last 24h · {{ totalPoolBlocks }} block</div>
          </q-card-section>
          <q-card-section class="q-pt-none" v-if="stats">
            <div v-for="p in stats.poolShare24h" :key="p.name" class="row items-center q-py-xs">
              <span class="text-caption" style="min-width: 90px">{{ p.name }}</span>
              <q-linear-progress :value="p.blocks / maxPoolBlocks" color="deep-orange-6" size="8px" rounded class="q-mx-sm" style="flex: 1" />
              <span class="text-caption text-weight-bold" style="min-width: 36px; text-align: right">{{ p.blocks }}</span>
            </div>
          </q-card-section>
        </q-card>
      </div>
    </div>

    <div class="row q-col-gutter-sm">
      <div class="col-12 col-md-6">
        <q-card flat bordered>
          <q-card-section class="row items-center justify-between">
            <div class="text-subtitle1 text-weight-medium"><span class="live-dot bg-green q-mr-xs"></span>Latest blocks</div>
            <router-link to="/blocks" class="text-caption">View all</router-link>
          </q-card-section>
          <q-table flat :rows="latestBlocks" :columns="blockColumns" row-key="id" :loading="loading" hide-pagination :rows-per-page-options="[0]" class="table-sm">
            <template v-slot:body="props">
              <q-tr :props="props">
                <q-td key="height" :props="props">
                  <router-link :to="'/block/' + props.row.height" class="text-primary2 text-weight-medium">{{ formatInt(props.row.height) }}</router-link>
                  <div class="text-caption text-grey-6">{{ timeAgo(props.row.timestamp) }}</div>
                </q-td>
                <q-td key="miner" :props="props">
                  <e-address :address="props.row.minerAddress" />
                  <div class="text-caption text-grey-6">{{ props.row.txCount }} tx · {{ formatBytes(props.row.size) }}</div>
                </q-td>
                <q-td key="reward" :props="props">
                  <e-erg :value="props.row.reward + props.row.fees" :decimals="4" />
                </q-td>
              </q-tr>
            </template>
          </q-table>
        </q-card>
      </div>

      <div class="col-12 col-md-6">
        <q-card flat bordered>
          <q-card-section class="row items-center justify-between">
            <div class="text-subtitle1 text-weight-medium"><span class="live-dot bg-green q-mr-xs"></span>Latest transactions</div>
            <router-link to="/mempool" class="text-caption">View mempool</router-link>
          </q-card-section>
          <q-table flat :rows="latestTxs" :columns="txColumns" row-key="id" :loading="loading" hide-pagination :rows-per-page-options="[0]" class="table-sm">
            <template v-slot:body="props">
              <q-tr :props="props">
                <q-td key="id" :props="props">
                  <e-hash :value="props.row.id" :to="'/tx/' + props.row.id" :start="10" :end="8" :copy="false" />
                  <div class="text-caption text-grey-6">{{ props.row.kind }} · block {{ formatInt(props.row.height) }}</div>
                </q-td>
                <q-td key="io" :props="props" class="text-grey-7">{{ props.row.inputs.length }} → {{ props.row.outputs.length }}</q-td>
                <q-td key="value" :props="props">
                  <e-erg :value="outputTotal(props.row)" :decimals="4" />
                  <div class="text-caption text-grey-6">fee {{ formatErg(props.row.fee, 4) }}</div>
                </q-td>
              </q-tr>
            </template>
          </q-table>
        </q-card>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import EStatTile from 'components/EStatTile.vue'
import AreaChart from 'components/AreaChart.vue'
import { getNetworkStats, getLatestBlocks, getLatestTransactions } from 'services/Explorer'
import { formatInt, formatErg, formatDifficulty, formatSeconds, timeAgo } from 'services/ErgoUtils'
import { formatBytes } from 'services/Utils'

defineOptions({
  name: 'ExplorerHomePage',
})

const loading = ref(true)
const stats = ref(null)
const latestBlocks = ref([])
const latestTxs = ref([])

const metrics = computed(() => {
  const s = stats.value
  if (!s) return []
  return [
    { label: 'Height', value: formatInt(s.height), sub: `${timeAgo(s.tipTimestamp)} · epoch ${formatInt(s.epoch)}`, icon: 'view_in_ar', color: '#1976d2', bg: '#e3f2fd' },
    {
      label: 'Hashrate',
      value: s.hashrate >= 1 ? s.hashrate.toFixed(2) : (s.hashrate * 1000).toFixed(0),
      unit: s.hashrate >= 1 ? 'TH/s' : 'GH/s',
      sub: `${s.hashrateChange7d >= 0 ? '▲' : '▼'} ${Math.abs(s.hashrateChange7d)}% vs 7d`,
      icon: 'speed',
      color: '#f88c2b',
      bg: '#fff3e0',
    },
    { label: 'Difficulty', value: formatDifficulty(s.difficulty), sub: `adjusts in ${s.blocksToNextEpoch} block`, icon: 'tune', color: '#6d4c9f', bg: '#ede7f6' },
    { label: 'Block time', value: formatSeconds(s.avgBlockTimeSec), sub: 'avg of last 300 · target 2m', icon: 'timer', color: '#26a69a', bg: '#e0f5f2' },
    {
      label: 'Supply',
      value: (s.circulating / 1e6).toFixed(2),
      unit: 'M ERG',
      sub: `${((s.circulating / s.maxSupply) * 100).toFixed(1)}% of ${(s.maxSupply / 1e6).toFixed(2)}M · ${(s.reemissionLocked / 1e6).toFixed(2)}M locked (EIP-27)`,
      icon: 'toll',
      color: '#e53935',
      bg: '#fdecea',
    },
    { label: 'Mempool', value: s.mempoolCount, unit: 'tx', sub: `${formatBytes(s.mempoolBytes)} pending`, icon: 'pending_actions', color: '#546bfa', bg: '#e8eaff' },
  ]
})

const hashrateSeries = computed(() => {
  const s = stats.value
  if (!s) return []
  return s.hashrate30d.map((d) => ({ t: d.day, value: d.value }))
})

const totalPoolBlocks = computed(() => (stats.value ? stats.value.poolShare24h.reduce((sum, p) => sum + p.blocks, 0) : 0))
const maxPoolBlocks = computed(() => (stats.value ? Math.max(...stats.value.poolShare24h.map((p) => p.blocks)) : 1))

const outputTotal = (tx) => tx.outputs.reduce((sum, o) => sum + o.value, 0)

const blockColumns = [
  { name: 'height', align: 'left', label: 'Block', field: 'height' },
  { name: 'miner', align: 'left', label: 'Miner', field: 'miner' },
  { name: 'reward', align: 'left', label: 'Reward + fees', field: 'reward' },
]
const txColumns = [
  { name: 'id', align: 'left', label: 'Transaction', field: 'id' },
  { name: 'io', align: 'left', label: 'In → out', field: 'io' },
  { name: 'value', align: 'left', label: 'Value', field: 'value' },
]

onMounted(async () => {
  ;[stats.value, latestBlocks.value, latestTxs.value] = await Promise.all([getNetworkStats(), getLatestBlocks(10), getLatestTransactions(10)])
  loading.value = false
})
</script>

<style scoped lang="scss">
.full-height {
  height: 100%;
}

.live-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  vertical-align: middle;
  animation: pulse 2s ease-in-out infinite;
}

@keyframes pulse {
  50% {
    opacity: 0.35;
  }
}
</style>
