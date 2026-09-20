<template>
  <div class="text-h6 text-grey-8 q-pb-sm"><q-icon name="view_in_ar" /> Blocks</div>
  <div class="text-caption text-grey-6 q-mb-md">
    Newest first · {{ formatInt(pagination.rowsNumber) }} blocks in chain · {{ formatInt(Math.floor(pagination.rowsNumber / 1024)) }} difficulty epochs
  </div>

  <q-table flat bordered row-key="id" :rows="rows" :columns="columns" :loading="isLoading" v-model:pagination="pagination" :rows-per-page-options="[25, 50, 100]" @request="onRequest">
    <template v-slot:loading>
      <q-inner-loading showing color="primary" />
    </template>
    <template v-slot:body="props">
      <q-tr :props="props">
        <q-td key="height" :props="props">
          <router-link :to="'/block/' + props.row.height" class="text-primary2 text-weight-medium">{{ formatInt(props.row.height) }}</router-link>
        </q-td>
        <q-td key="age" :props="props" class="text-grey-7">{{ timeAgo(props.row.timestamp) }}</q-td>
        <q-td key="timestamp" :props="props" class="text-grey-7">{{ formatTimestamp(props.row.timestamp) }}</q-td>
        <q-td key="txCount" :props="props">{{ props.row.txCount }}</q-td>
        <q-td key="miner" :props="props"><e-address :address="props.row.minerAddress" /></q-td>
        <q-td key="size" :props="props">{{ formatBytes(props.row.size) }}</q-td>
        <q-td key="difficulty" :props="props">{{ formatDifficulty(props.row.difficulty) }}</q-td>
        <q-td key="reward" :props="props"><e-erg :value="props.row.reward + props.row.fees" :decimals="4" /></q-td>
        <q-td key="id" :props="props"><e-hash :value="props.row.id" :to="'/block/' + props.row.id" :start="10" :end="8" /></q-td>
      </q-tr>
    </template>
  </q-table>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getBlocks } from 'services/Explorer'
import { formatInt, formatDifficulty, formatTimestamp, timeAgo } from 'services/ErgoUtils'
import { formatBytes } from 'services/Utils'

defineOptions({
  name: 'BlockListPage',
})

const isLoading = ref(false)
const rows = ref([])
const pagination = ref({ page: 1, rowsPerPage: 25, rowsNumber: 0 })

const columns = [
  { name: 'height', align: 'left', label: 'Height', field: 'height' },
  { name: 'age', align: 'left', label: 'Age', field: 'timestamp' },
  { name: 'timestamp', align: 'left', label: 'Timestamp', field: 'timestamp' },
  { name: 'txCount', align: 'right', label: 'Txs', field: 'txCount' },
  { name: 'miner', align: 'left', label: 'Miner', field: 'miner' },
  { name: 'size', align: 'right', label: 'Size', field: 'size' },
  { name: 'difficulty', align: 'right', label: 'Difficulty', field: 'difficulty' },
  { name: 'reward', align: 'right', label: 'Reward + fees', field: 'reward' },
  { name: 'id', align: 'left', label: 'Block id', field: 'id' },
]

async function onRequest(props) {
  const { page, rowsPerPage } = props.pagination
  isLoading.value = true
  const res = await getBlocks({ page, rowsPerPage })
  rows.value = res.items
  pagination.value = { page, rowsPerPage, rowsNumber: res.total }
  isLoading.value = false
}

onMounted(() => onRequest({ pagination: pagination.value }))
</script>
