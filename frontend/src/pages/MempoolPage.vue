<template>
  <div class="row items-center q-gutter-x-sm q-pb-xs">
    <div class="text-h6 text-grey-8"><q-icon name="pending_actions" /> Mempool</div>
    <q-badge color="orange-1" text-color="orange-9" :label="rows.length + ' unconfirmed'" v-if="rows.length" />
  </div>
  <div class="text-caption text-grey-6 q-mb-md">Transactions seen by the node but not yet included in a block · next block expected in ~{{ nextBlockSec }}s</div>

  <div class="row q-col-gutter-sm q-mb-md" v-if="rows.length">
    <div class="col-6 col-md-3" v-for="m in metrics" :key="m.label">
      <EStatTile v-bind="m" />
    </div>
  </div>

  <q-table flat bordered row-key="id" :rows="rows" :columns="columns" :loading="isLoading" :rows-per-page-options="[50]" hide-pagination>
    <template v-slot:loading>
      <q-inner-loading showing color="primary" />
    </template>
    <template v-slot:body="props">
      <q-tr :props="props">
        <q-td key="id" :props="props"><e-hash :value="props.row.id" :to="'/tx/' + props.row.id" :start="14" :end="10" /></q-td>
        <q-td key="seen" :props="props" class="text-grey-7">{{ timeAgo(props.row.timestamp) }}</q-td>
        <q-td key="kind" :props="props"><q-badge color="grey-3" text-color="grey-8" :label="props.row.kind" /></q-td>
        <q-td key="io" :props="props">{{ props.row.inputs.length }} → {{ props.row.outputs.length }}</q-td>
        <q-td key="value" :props="props"><e-erg :value="outputTotal(props.row)" :decimals="4" /></q-td>
        <q-td key="fee" :props="props">{{ formatErg(props.row.fee, 4) }}</q-td>
        <q-td key="size" :props="props" class="text-grey-7">{{ formatBytes(props.row.size) }}</q-td>
      </q-tr>
    </template>
  </q-table>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import EStatTile from 'components/EStatTile.vue'
import { getMempool } from 'services/Explorer'
import { formatErg, timeAgo } from 'services/ErgoUtils'
import { formatBytes } from 'services/Utils'

defineOptions({
  name: 'MempoolPage',
})

const isLoading = ref(true)
const rows = ref([])
const nextBlockSec = ref(79)

const outputTotal = (tx) => tx.outputs.reduce((sum, o) => sum + o.value, 0)

const metrics = computed(() => {
  const size = rows.value.reduce((s, t) => s + t.size, 0)
  const fees = rows.value.reduce((s, t) => s + t.fee, 0)
  const oldest = rows.value[rows.value.length - 1]
  return [
    { label: 'Pending', value: rows.value.length, unit: 'tx', sub: oldest ? 'oldest ' + timeAgo(oldest.timestamp) : '', icon: 'hourglass_top', color: '#f88c2b', bg: '#fff3e0' },
    { label: 'Size', value: formatBytes(size), sub: 'of 512 KB block limit', icon: 'storage', color: '#1976d2', bg: '#e3f2fd' },
    { label: 'Fees waiting', value: formatErg(fees, 4), unit: 'ERG', sub: 'median 0.0011 ERG', icon: 'payments', color: '#26a69a', bg: '#e0f5f2' },
    { label: 'Fee rate', value: '1.1', unit: 'µERG/B', sub: 'min relay 1.0 µERG/B', icon: 'trending_flat', color: '#6d4c9f', bg: '#ede7f6' },
  ]
})

const columns = [
  { name: 'id', align: 'left', label: 'Transaction', field: 'id' },
  { name: 'seen', align: 'left', label: 'Seen', field: 'timestamp' },
  { name: 'kind', align: 'left', label: 'Type', field: 'kind' },
  { name: 'io', align: 'right', label: 'In → out', field: 'io' },
  { name: 'value', align: 'right', label: 'Value', field: 'value' },
  { name: 'fee', align: 'right', label: 'Fee (ERG)', field: 'fee' },
  { name: 'size', align: 'right', label: 'Size', field: 'size' },
]

onMounted(async () => {
  const mempool = await getMempool()
  rows.value = mempool ? mempool.items : []
  isLoading.value = false
})
</script>
