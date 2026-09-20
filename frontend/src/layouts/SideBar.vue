<template>
  <q-list class="sidebar-list">
    <q-item-label header> Ergo Explorer v{{ version }}</q-item-label>

    <q-expansion-item icon="space_dashboard" label="Overview" hide-expand-icon group="sidebar" to="/" exact />

    <q-expansion-item icon="view_in_ar" label="Blocks" hide-expand-icon group="sidebar" to="/blocks" :class="{ 'q-router-link--exact-active': isUrlContain('/block') }" />

    <q-expansion-item icon="pending_actions" label="Mempool" hide-expand-icon group="sidebar" to="/mempool" />

    <q-expansion-item icon="token" label="Tokens" hide-expand-icon group="sidebar" to="/tokens" :class="{ 'q-router-link--exact-active': isUrlContain('/token/') }" />

    <q-expansion-item icon="leaderboard" label="Rich list" hide-expand-icon group="sidebar" to="/richlist" />

    <q-expansion-item icon="bar_chart" label="Charts" group="sidebar" :default-opened="isUrlContain('/charts')">
      <q-expansion-item hide-expand-icon to="/charts/hashrate" label="Hashrate" :header-inset-level="0.7" />
      <q-expansion-item hide-expand-icon to="/charts/transactions" label="Transactions" :header-inset-level="0.7" />
      <q-expansion-item hide-expand-icon to="/charts/fees" label="Fees" :header-inset-level="0.7" />
      <q-expansion-item hide-expand-icon to="/charts/activeAddresses" label="Active addresses" :header-inset-level="0.7" />
      <q-expansion-item hide-expand-icon to="/charts/circulatingSupply" label="Supply" :header-inset-level="0.7" />
      <q-expansion-item hide-expand-icon to="/charts/mempoolTxs" label="Mempool" :header-inset-level="0.7" />
    </q-expansion-item>

    <q-expansion-item icon="api" label="API & MCP" hide-expand-icon group="sidebar" to="/api" />

    <q-expansion-item icon="info" label="About" hide-expand-icon group="sidebar" to="/about" />

    <q-expansion-item icon="shield" label="Privacy" hide-expand-icon group="sidebar" to="/privacy" />

    <q-space />

    <div class="node-widget q-mb-md q-mt-md q-ml-md q-mr-md">
      <div class="row items-center justify-between q-mb-xs">
        <span class="text-caption text-grey-5">Chain index</span>
        <span class="text-caption text-weight-bold" :class="synced ? 'text-positive' : 'text-warning'">{{ info ? (synced ? 'Synced' : indexPercent + '%') : '…' }}</span>
      </div>
      <q-linear-progress :value="info ? info.indexedHeight / Math.max(1, info.height) : 0" size="6px" rounded track-color="rgba(255,255,255,0.12)" :color="synced ? 'positive' : 'warning'" />
      <div class="text-caption text-grey-5 q-mt-xs" v-if="info">
        {{ synced ? 'Height ' + formatInt(info.height) : formatInt(info.indexedHeight) + ' / ' + formatInt(info.height) }} · Node v{{ info.nodeVersion.split('-')[0] }}
      </div>
    </div>
  </q-list>
</template>
<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getInfo } from 'services/Explorer'
import { formatInt } from 'services/ErgoUtils'

const version = import.meta.env.VITE_APP_VERSION
const route = useRoute()
const info = ref(null)
const synced = computed(() => info.value && info.value.indexSynced)
const indexPercent = computed(() => (info.value ? ((info.value.indexedHeight / Math.max(1, info.value.height)) * 100).toFixed(1) : 0))

onMounted(async () => {
  try {
    info.value = await getInfo()
  } catch {
    info.value = null
  }
})

function isUrlContain(name) {
  return route.fullPath.includes(name)
}

defineOptions({
  name: 'SideBar',
})
</script>

<style scoped lang="scss">
.sidebar-list {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.node-widget {
  background: rgba(255, 255, 255, 0.05);
  border-radius: 8px;
  padding: 8px;
}
</style>
