<template>
  <q-breadcrumbs class="text-grey-7 q-mb-sm" active-color="grey-8">
    <q-breadcrumbs-el label="Overview" to="/" />
    <q-breadcrumbs-el label="Tokens" to="/tokens" />
    <q-breadcrumbs-el :label="token ? token.name : '…'" />
  </q-breadcrumbs>

  <t-loading v-if="isLoading" width="50px" />
  <NotFound v-else-if="!token" kind="token" :value="route.params.id" />

  <template v-else>
    <div class="row items-center q-gutter-x-sm q-mb-xs">
      <q-avatar :style="{ background: tokenColor(token.id) }" text-color="white" size="36px" font-size="14px">{{ token.name.slice(0, 2).toUpperCase() }}</q-avatar>
      <div class="text-h6 text-grey-8">{{ token.name }}</div>
      <q-badge color="grey-3" text-color="grey-8" label="EIP-4" />
      <q-badge color="grey-3" text-color="grey-8" :label="token.decimals + ' decimals'" />
    </div>
    <div class="text-grey-7 q-mb-md"><e-hash :value="token.id" full /></div>

    <q-card flat bordered class="q-mb-md">
      <q-card-section>
        <div class="row q-col-gutter-x-xl">
          <div class="col-12 col-md-6">
            <e-info-row label="Description">{{ token.desc }}</e-info-row>
            <e-info-row label="Total supply"
              ><span class="num">{{ formatTokenAmount(token.supply, token.decimals) }}</span> <span class="text-grey-6">{{ token.name }}</span></e-info-row
            >
            <e-info-row label="Holders">{{ formatInt(token.holderCount) }}</e-info-row>
          </div>
          <div class="col-12 col-md-6">
            <e-info-row label="Issued in"
              ><e-hash :value="token.issueTx" :to="'/tx/' + token.issueTx" :start="10" :end="8" /> <span class="text-grey-6">· block {{ formatInt(token.issueHeight) }}</span></e-info-row
            >
            <e-info-row label="Issuing box"><e-hash :value="token.issueBox" :start="10" :end="8" /></e-info-row>
            <e-info-row label="Standard">EIP-4 · name in R4, description in R5, decimals in R6</e-info-row>
          </div>
        </div>
      </q-card-section>
    </q-card>

    <div class="row q-col-gutter-sm">
      <div class="col-12 col-md-7">
        <q-card flat bordered>
          <q-card-section class="row items-center justify-between q-py-sm">
            <div class="text-subtitle1 text-weight-medium">Holders</div>
            <span class="text-caption text-grey-6">{{ formatInt(holdersPagination.rowsNumber) }} addresses</span>
          </q-card-section>
          <q-table
            flat
            :rows="holders"
            :columns="holderColumns"
            row-key="address"
            :loading="holdersLoading"
            v-model:pagination="holdersPagination"
            :rows-per-page-options="[25, 50, 100]"
            @request="onHoldersRequest"
            class="table-sm"
          >
            <template v-slot:body="props">
              <q-tr :props="props">
                <q-td key="rank" :props="props" class="text-grey-6">{{ props.row.rank }}</q-td>
                <q-td key="address" :props="props"><e-address :address="props.row.address" :start="10" :end="6" /></q-td>
                <q-td key="amount" :props="props" class="num">{{ formatTokenAmount(props.row.amount, token.decimals) }}</q-td>
                <q-td key="share" :props="props" class="text-grey-7 num">{{ ((props.row.amount / token.supply) * 100).toFixed(2) }}%</q-td>
              </q-tr>
            </template>
          </q-table>
        </q-card>
      </div>
      <div class="col-12 col-md-5">
        <q-card flat bordered>
          <q-card-section class="q-py-sm"><div class="text-subtitle1 text-weight-medium">Recent transfers</div></q-card-section>
          <q-table flat :rows="token.transfers" :columns="transferColumns" row-key="id" hide-pagination :rows-per-page-options="[0]" class="table-sm">
            <template v-slot:body="props">
              <q-tr :props="props">
                <q-td key="id" :props="props">
                  <e-hash :value="props.row.id" :to="'/tx/' + props.row.id" :start="8" :end="6" :copy="false" />
                  <div class="text-caption text-grey-6">{{ timeAgo(props.row.timestamp) }}</div>
                </q-td>
                <q-td key="height" :props="props"
                  ><router-link :to="'/block/' + props.row.height" class="text-primary2">{{ formatInt(props.row.height) }}</router-link></q-td
                >
                <q-td key="to" :props="props"><e-address :address="props.row.to" :start="8" :end="5" /></q-td>
                <q-td key="amount" :props="props" class="num">{{ formatTokenAmount(props.row.amount, token.decimals) }}</q-td>
              </q-tr>
            </template>
          </q-table>
        </q-card>
      </div>
    </div>
  </template>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import NotFound from './NotFound.vue'
import { getToken, getTokenHolders, tokenColor } from 'services/Explorer'
import { formatInt, formatTokenAmount, timeAgo } from 'services/ErgoUtils'

defineOptions({
  name: 'TokenDetailPage',
})

const route = useRoute()
const isLoading = ref(true)
const token = ref(null)

const holderColumns = [
  { name: 'rank', align: 'left', label: '#', field: 'rank' },
  { name: 'address', align: 'left', label: 'Address', field: 'address' },
  { name: 'amount', align: 'right', label: 'Amount', field: 'amount' },
  { name: 'share', align: 'right', label: 'Share', field: 'share' },
]
const transferColumns = [
  { name: 'id', align: 'left', label: 'Transaction', field: 'id' },
  { name: 'height', align: 'left', label: 'Block', field: 'height' },
  { name: 'to', align: 'left', label: 'To', field: 'to' },
  { name: 'amount', align: 'right', label: 'Amount', field: 'amount' },
]

const holders = ref([])
const holdersLoading = ref(false)
const holdersPagination = ref({ page: 1, rowsPerPage: 25, rowsNumber: 0 })

async function onHoldersRequest(props) {
  const { page, rowsPerPage } = props.pagination
  holdersLoading.value = true
  const res = await getTokenHolders(route.params.id, { page, rowsPerPage })
  holders.value = res ? res.items : []
  holdersPagination.value = { page, rowsPerPage, rowsNumber: res ? res.total : 0 }
  holdersLoading.value = false
}

async function load() {
  isLoading.value = true
  token.value = await getToken(route.params.id)
  isLoading.value = false
  if (token.value) await onHoldersRequest({ pagination: { ...holdersPagination.value, page: 1 } })
}

watch(() => route.params.id, load, { immediate: true })
</script>

<style scoped lang="scss">
.num {
  font-variant-numeric: tabular-nums;
}
</style>
