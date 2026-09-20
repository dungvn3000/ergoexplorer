<template>
  <div class="row items-center q-gutter-x-sm q-pb-xs">
    <div class="text-h6 text-grey-8"><q-icon name="leaderboard" /> Rich list</div>
    <q-badge v-if="meta && !meta.synced" color="orange-1" text-color="orange-9" :label="'Index at block ' + formatInt(meta.indexedHeight) + ' — figures incomplete until synced'" />
  </div>
  <div class="text-caption text-grey-6 q-mb-md">
    Addresses by ERG held in unspent boxes · {{ meta ? formatInt(meta.total) : '…' }} funded addresses · share of {{ (MAX_SUPPLY / 1e6).toFixed(2) }}M max supply
  </div>

  <q-table flat bordered row-key="address" :rows="rows" :columns="columns" :loading="isLoading" v-model:pagination="pagination" :rows-per-page-options="[25, 50, 100]" @request="onRequest">
    <template v-slot:loading>
      <q-inner-loading showing color="primary" />
    </template>
    <template v-slot:body="props">
      <q-tr :props="props">
        <q-td key="rank" :props="props" class="text-grey-6">{{ props.row.rank }}</q-td>
        <q-td key="address" :props="props"><e-address :address="props.row.address" :start="14" :end="10" /></q-td>
        <q-td key="amount" :props="props"><e-erg :value="props.row.amount" :decimals="2" /></q-td>
        <q-td key="share" :props="props" class="text-grey-7 num">{{ ((props.row.amount / 1e9 / MAX_SUPPLY) * 100).toFixed(3) }}%</q-td>
        <q-td key="boxCount" :props="props" class="num">{{ formatInt(props.row.boxCount) }}</q-td>
      </q-tr>
    </template>
  </q-table>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getRichList } from 'services/Explorer'
import { formatInt } from 'services/ErgoUtils'

defineOptions({
  name: 'RichListPage',
})

const MAX_SUPPLY = 97739924

const isLoading = ref(false)
const rows = ref([])
const meta = ref(null)
const pagination = ref({ page: 1, rowsPerPage: 50, rowsNumber: 0 })

const columns = [
  { name: 'rank', align: 'left', label: '#', field: 'rank' },
  { name: 'address', align: 'left', label: 'Address', field: 'address' },
  { name: 'amount', align: 'right', label: 'Balance', field: 'amount' },
  { name: 'share', align: 'right', label: 'Share', field: 'share' },
  { name: 'boxCount', align: 'right', label: 'Unspent boxes', field: 'boxCount' },
]

async function onRequest(props) {
  const { page, rowsPerPage } = props.pagination
  isLoading.value = true
  const res = await getRichList({ page, rowsPerPage })
  rows.value = res ? res.items : []
  meta.value = res
  pagination.value = { page, rowsPerPage, rowsNumber: res ? res.total : 0 }
  isLoading.value = false
}

onMounted(() => onRequest({ pagination: pagination.value }))
</script>

<style scoped lang="scss">
.num {
  font-variant-numeric: tabular-nums;
}
</style>
