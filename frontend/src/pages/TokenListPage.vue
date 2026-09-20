<template>
  <div class="text-h6 text-grey-8 q-pb-sm"><q-icon name="token" /> Tokens</div>
  <div class="text-caption text-grey-6 q-mb-md">EIP-4 assets on Ergo · {{ formatInt(148512) }} tokens minted · showing the most held</div>

  <q-table flat bordered row-key="id" :rows="rows" :columns="columns" :loading="isLoading" :rows-per-page-options="[25]" hide-pagination>
    <template v-slot:loading>
      <q-inner-loading showing color="primary" />
    </template>
    <template v-slot:body="props">
      <q-tr :props="props">
        <q-td key="name" :props="props">
          <router-link :to="'/token/' + props.row.id" class="text-primary2 row items-center no-wrap">
            <q-avatar :style="{ background: tokenColor(props.row.id) }" text-color="white" size="26px" font-size="11px" class="q-mr-sm">{{ props.row.name.slice(0, 2).toUpperCase() }}</q-avatar>
            <span class="text-weight-medium">{{ props.row.name }}</span>
          </router-link>
        </q-td>
        <q-td key="id" :props="props"><e-hash :value="props.row.id" :to="'/token/' + props.row.id" :start="14" :end="10" /></q-td>
        <q-td key="decimals" :props="props">{{ props.row.decimals }}</q-td>
        <q-td key="supply" :props="props">{{ formatTokenAmount(props.row.supply, props.row.decimals) }}</q-td>
        <q-td key="holderCount" :props="props">{{ formatInt(props.row.holderCount) }}</q-td>
        <q-td key="transfers24h" :props="props">{{ formatInt(props.row.transfers24h) }}</q-td>
      </q-tr>
    </template>
  </q-table>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getTokens, tokenColor } from 'services/Explorer'
import { formatInt, formatTokenAmount } from 'services/ErgoUtils'

defineOptions({
  name: 'TokenListPage',
})

const isLoading = ref(true)
const rows = ref([])

const columns = [
  { name: 'name', align: 'left', label: 'Token', field: 'name' },
  { name: 'id', align: 'left', label: 'Token id', field: 'id' },
  { name: 'decimals', align: 'right', label: 'Decimals', field: 'decimals' },
  { name: 'supply', align: 'right', label: 'Total supply', field: 'supply' },
  { name: 'holderCount', align: 'right', label: 'Holders', field: 'holderCount' },
  { name: 'transfers24h', align: 'right', label: 'Transfers 24h', field: 'transfers24h' },
]

onMounted(async () => {
  rows.value = (await getTokens()) || []
  isLoading.value = false
})
</script>
