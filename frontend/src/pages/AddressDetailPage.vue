<template>
  <q-breadcrumbs class="text-grey-7 q-mb-sm" active-color="grey-8">
    <q-breadcrumbs-el label="Overview" to="/" />
    <q-breadcrumbs-el label="Address" />
  </q-breadcrumbs>

  <t-loading v-if="isLoading" width="50px" />
  <NotFound v-else-if="!addr" kind="address" :value="route.params.address" />

  <template v-else>
    <div class="row items-start justify-between q-col-gutter-sm q-mb-md">
      <div class="col-12 col-md">
        <div class="row items-center q-gutter-x-sm">
          <div class="text-h6 text-grey-8"><q-icon name="account_balance_wallet" /> {{ addr.label || 'Address' }}</div>
          <q-badge color="grey-3" text-color="grey-8" :label="addr.contract ? 'P2S · script' : 'P2PK'" />
          <q-badge v-if="addr.unconfirmed" color="orange-1" text-color="orange-9" label="Unconfirmed activity" />
        </div>
        <div class="text-grey-7"><e-hash :value="addr.address" full /></div>
      </div>
      <div class="col-auto row q-gutter-x-xs">
        <e-raw-json title="Address" :sources="[{ label: 'Explorer API', value: 'api', path: '/addresses/' + addr.address }]" />
      </div>
    </div>

    <div class="row q-col-gutter-sm q-mb-md">
      <div class="col-12 col-md-8">
        <q-card flat bordered class="full-height">
          <q-card-section class="row items-center q-col-gutter-lg">
            <div class="col-auto">
              <img :src="qr" alt="QR" class="qr" v-if="qr" />
            </div>
            <div class="col">
              <div class="text-caption text-grey-6">Confirmed balance</div>
              <div class="text-h5 text-weight-bolder num">{{ formatErg(addr.balance) }} <span class="text-subtitle2 text-grey-6">ERG</span></div>
              <div class="text-caption text-grey-6">
                <span v-if="addr.unconfirmed">+ {{ formatErg(addr.unconfirmed, 4) }} ERG unconfirmed · </span><span v-if="addr.boxes != null">{{ formatInt(addr.boxes) }} unspent boxes</span
                ><span v-else>see Unspent boxes</span>
              </div>
            </div>
            <div class="col-auto">
              <div class="text-caption text-grey-6">Token</div>
              <div class="text-h5 text-weight-bolder">{{ addr.tokens.length }}</div>
              <div class="text-caption text-grey-6">{{ addr.tokens.length ? addr.tokens.map((t) => t.name).join(', ') : 'none' }}</div>
            </div>
            <div class="col-auto">
              <div class="text-caption text-grey-6">Transactions</div>
              <div class="text-h5 text-weight-bolder num">{{ formatInt(addr.txCount) }}</div>
              <div class="text-caption text-grey-6">first {{ timeAgo(addr.firstSeen) }} · last {{ timeAgo(addr.lastSeen) }}</div>
            </div>
          </q-card-section>
        </q-card>
      </div>
      <div class="col-12 col-md-4">
        <q-card flat bordered class="full-height">
          <q-card-section>
            <div class="row items-center justify-between">
              <div class="text-subtitle1 text-weight-medium">Script</div>
              <span class="text-caption text-grey-6">{{ addr.contract ? 'ErgoTree v1' : 'proveDlog' }}</span>
            </div>
            <div class="text-caption text-grey-7 mono tree q-mt-xs">{{ addr.ergoTree }}</div>
            <div class="text-caption text-grey-6 q-mt-sm">
              {{
                addr.contract
                  ? 'Pay-to-script address: boxes can be spent only when the ErgoTree evaluates to true.'
                  : 'Pay-to-public-key address: spending requires a Schnorr signature for the embedded key.'
              }}
            </div>
          </q-card-section>
        </q-card>
      </div>
    </div>

    <q-card flat bordered class="q-mb-md" v-if="addr.tokens.length">
      <q-card-section class="q-py-sm"><div class="text-subtitle1 text-weight-medium">Token balances</div></q-card-section>
      <q-table flat :rows="addr.tokens" :columns="tokenColumns" row-key="tokenId" hide-pagination :rows-per-page-options="[0]" class="table-sm">
        <template v-slot:body="props">
          <q-tr :props="props">
            <q-td key="name" :props="props">
              <router-link :to="'/token/' + props.row.tokenId" class="text-primary2 row items-center no-wrap">
                <q-avatar :style="{ background: tokenColor(props.row.tokenId) }" text-color="white" size="22px" font-size="9px" class="q-mr-sm">{{
                  props.row.name.slice(0, 2).toUpperCase()
                }}</q-avatar>
                <span class="text-weight-medium">{{ props.row.name }}</span>
              </router-link>
            </q-td>
            <q-td key="amount" :props="props" class="num">{{ formatTokenAmount(props.row.amount, props.row.decimals) }}</q-td>
            <q-td key="tokenId" :props="props"><e-hash :value="props.row.tokenId" :to="'/token/' + props.row.tokenId" :start="12" :end="8" /></q-td>
            <q-td key="decimals" :props="props">{{ props.row.decimals }}</q-td>
          </q-tr>
        </template>
      </q-table>
    </q-card>

    <q-card flat bordered>
      <div class="row items-center justify-between q-pr-md">
        <q-tabs v-model="tab" dense no-caps align="left" active-color="indigo-7" indicator-color="indigo-7" class="text-grey-7">
          <q-tab name="txs" label="Transactions" />
          <q-tab name="boxes" :label="'Unspent boxes' + (addr.boxes != null ? ' (' + formatInt(addr.boxes) + ')' : '')" />
        </q-tabs>
        <span class="text-caption text-grey-6">{{ tab === 'txs' ? formatInt(addr.txCount) + ' total' : formatInt(boxTotal) + ' unspent' }} · newest first</span>
      </div>
      <q-separator />

      <q-tab-panels v-model="tab" animated>
        <q-tab-panel name="txs" class="q-pa-none">
          <q-table flat :rows="addr.txs" :columns="txColumns" row-key="id" :loading="isPaging" v-model:pagination="pagination" :rows-per-page-options="[20, 50]" @request="onRequest" class="table-sm">
            <template v-slot:body="props">
              <q-tr :props="props">
                <q-td key="id" :props="props"><e-hash :value="props.row.id" :to="'/tx/' + props.row.id" :start="12" :end="8" /></q-td>
                <q-td key="height" :props="props"
                  ><router-link :to="'/block/' + props.row.height" class="text-primary2">{{ formatInt(props.row.height) }}</router-link></q-td
                >
                <q-td key="age" :props="props" class="text-grey-7">{{ timeAgo(props.row.timestamp) }}</q-td>
                <q-td key="dir" :props="props">
                  <q-badge :color="DIR[props.row.dir].color" :text-color="DIR[props.row.dir].text" :label="DIR[props.row.dir].label" />
                </q-td>
                <q-td key="amount" :props="props" class="num" :class="props.row.amount > 0 ? 'text-green-8' : ''">
                  {{ props.row.amount > 0 ? '+' : '−' }}{{ formatErg(Math.abs(props.row.amount), 4) }} ERG
                </q-td>
                <q-td key="tokens" :props="props">
                  <e-token v-for="t in props.row.tokens" :key="t.tokenId" :token="t" />
                  <span v-if="!props.row.tokens.length" class="text-grey-5">—</span>
                </q-td>
                <q-td key="fee" :props="props" class="text-grey-7 num">{{ formatErg(props.row.fee, 4) }}</q-td>
              </q-tr>
            </template>
          </q-table>
        </q-tab-panel>

        <q-tab-panel name="boxes" class="q-pa-none" style="position: relative; min-height: 120px">
          <q-inner-loading :showing="isBoxLoading" color="primary" />
          <e-box v-for="b in boxes" :key="b.boxId" :box="b" output />
          <div v-if="!isBoxLoading && !boxes.length" class="text-grey-6 q-pa-lg text-center">No unspent boxes.</div>
          <div v-if="boxPages > 1" class="row items-center justify-between q-px-md q-py-sm text-caption text-grey-6">
            <span>Page {{ boxPage }} of {{ formatInt(boxPages) }}</span>
            <q-pagination v-model="boxPage" :max="boxPages" :max-pages="7" boundary-numbers direction-links dense color="grey-8" active-color="indigo-7" @update:model-value="loadBoxes" />
          </div>
        </q-tab-panel>
      </q-tab-panels>
    </q-card>
  </template>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import QRCode from 'qrcode'
import NotFound from './NotFound.vue'
import { getAddress, getAddressBoxes, tokenColor } from 'services/Explorer'
import { formatInt, formatErg, formatTokenAmount, timeAgo } from 'services/ErgoUtils'

defineOptions({
  name: 'AddressDetailPage',
})

const route = useRoute()
const isLoading = ref(true)
const addr = ref(null)
const qr = ref('')
const isPaging = ref(false)
const pagination = ref({ page: 1, rowsPerPage: 20, rowsNumber: 0 })

// "Unspent boxes" tab: loaded when first opened, 20 boxes per page
const tab = ref('txs')
const boxes = ref([])
const boxTotal = ref(0)
const boxPage = ref(1)
const BOX_ROWS = 20
const boxPages = computed(() => Math.max(1, Math.ceil(boxTotal.value / BOX_ROWS)))
const isBoxLoading = ref(false)

const DIR = {
  in: { label: 'IN', color: 'green-1', text: 'green-8' },
  out: { label: 'OUT', color: 'grey-3', text: 'grey-8' },
  self: { label: 'SELF', color: 'orange-1', text: 'orange-9' },
}

const tokenColumns = [
  { name: 'name', align: 'left', label: 'Token', field: 'name' },
  { name: 'amount', align: 'right', label: 'Amount', field: 'amount' },
  { name: 'tokenId', align: 'left', label: 'Token id', field: 'tokenId' },
  { name: 'decimals', align: 'right', label: 'Decimals', field: 'decimals' },
]
const txColumns = [
  { name: 'id', align: 'left', label: 'Transaction', field: 'id' },
  { name: 'height', align: 'left', label: 'Block', field: 'height' },
  { name: 'age', align: 'left', label: 'Age', field: 'timestamp' },
  { name: 'dir', align: 'left', label: 'Direction', field: 'dir' },
  { name: 'amount', align: 'right', label: 'Amount', field: 'amount' },
  { name: 'tokens', align: 'left', label: 'Token', field: 'tokens' },
  { name: 'fee', align: 'right', label: 'Fee', field: 'fee' },
]

async function loadBoxes() {
  isBoxLoading.value = true
  const res = await getAddressBoxes(route.params.address, { page: boxPage.value, rowsPerPage: BOX_ROWS })
  boxes.value = res ? res.items : []
  boxTotal.value = res ? res.total : 0
  isBoxLoading.value = false
}

watch(tab, (t) => {
  if (t === 'boxes' && !boxes.value.length) loadBoxes()
})

async function load() {
  isLoading.value = true
  pagination.value = { page: 1, rowsPerPage: pagination.value.rowsPerPage, rowsNumber: 0 }
  tab.value = 'txs'
  boxes.value = []
  boxTotal.value = 0
  boxPage.value = 1
  addr.value = await getAddress(route.params.address, pagination.value)
  if (addr.value) pagination.value.rowsNumber = addr.value.txCount
  qr.value = addr.value ? await QRCode.toDataURL(addr.value.address, { margin: 1, width: 112 }) : ''
  isLoading.value = false
}

// Paging the history reloads only the transactions and keeps balance / QR
async function onRequest(props) {
  const { page, rowsPerPage } = props.pagination
  isPaging.value = true
  const next = await getAddress(route.params.address, { page, rowsPerPage })
  if (next) {
    addr.value.txs = next.txs
    pagination.value = { page, rowsPerPage, rowsNumber: next.txCount }
  }
  isPaging.value = false
}

watch(() => route.params.address, load, { immediate: true })
</script>

<style scoped lang="scss">
.full-height {
  height: 100%;
}

.qr {
  width: 96px;
  height: 96px;
  border: 1px solid rgba(0, 0, 0, 0.12);
  border-radius: 6px;
  display: block;
}

.num {
  font-variant-numeric: tabular-nums;
}

.mono {
  font-family: $mono-font-family;
}

.tree {
  max-height: 84px;
  overflow: auto;
  word-break: break-all;
}
</style>
