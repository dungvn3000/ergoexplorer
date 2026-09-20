<template>
  <q-breadcrumbs class="text-grey-7 q-mb-sm" active-color="grey-8">
    <q-breadcrumbs-el label="Overview" to="/" />
    <q-breadcrumbs-el v-if="box && box.transactionId" label="Transaction" :to="'/tx/' + box.transactionId" />
    <q-breadcrumbs-el label="Box" />
  </q-breadcrumbs>

  <t-loading v-if="isLoading" width="50px" />
  <NotFound v-else-if="!box" kind="box" :value="route.params.id" />

  <template v-else>
    <div class="row items-start justify-between q-col-gutter-sm q-mb-md">
      <div class="col-12 col-md">
        <div class="row items-center q-gutter-x-sm">
          <div class="text-h6 text-grey-8"><q-icon name="inventory_2" /> Box</div>
          <q-badge v-if="box.spent" color="grey-3" text-color="grey-8" label="Spent" />
          <q-badge v-else color="green-1" text-color="green-8" label="Unspent" />
          <q-badge v-if="box.assets.length" color="indigo-1" text-color="indigo-8" :label="box.assets.length + (box.assets.length > 1 ? ' tokens' : ' token')" />
        </div>
        <div class="text-grey-7"><e-hash :value="box.boxId" full /></div>
      </div>
      <div class="col-auto">
        <e-raw-json title="Box" :sources="[{ label: 'Explorer API', value: 'api', path: '/boxes/' + box.boxId }]" />
      </div>
    </div>

    <q-card flat bordered class="q-mb-md">
      <q-card-section>
        <div class="row q-col-gutter-x-xl">
          <div class="col-12 col-md-6">
            <e-info-row label="Value"><e-erg :value="box.value" /></e-info-row>
            <e-info-row label="Address">
              <e-address v-if="box.address" :address="box.address" :start="16" :end="12" />
              <span v-else class="text-grey-6">—</span>
            </e-info-row>
            <e-info-row label="Created by">
              <template v-if="box.transactionId">
                <e-hash :value="box.transactionId" :to="'/tx/' + box.transactionId" :start="12" :end="8" />
                <span class="text-grey-6 q-ml-sm">· output #{{ box.index }}</span>
              </template>
              <span v-else class="text-grey-6">genesis</span>
            </e-info-row>
            <e-info-row label="Creation height">
              <router-link :to="'/block/' + box.creationHeight" class="text-primary2 text-weight-medium">{{ formatInt(box.creationHeight) }}</router-link>
              <span class="text-caption text-grey-6 q-ml-sm">declared by the creating transaction</span>
            </e-info-row>
          </div>
          <div class="col-12 col-md-6">
            <e-info-row label="Status">
              <template v-if="box.spent">
                <span class="text-grey-8">Spent</span>
                <span v-if="box.spentBy" class="q-ml-sm">in <e-hash :value="box.spentBy" :to="'/tx/' + box.spentBy" :start="12" :end="8" /></span>
              </template>
              <span v-else class="text-green-8">Unspent · part of the UTXO set</span>
            </e-info-row>
            <e-info-row label="Script">
              {{ isP2PK ? 'P2PK · proveDlog' : 'P2S · ErgoTree' }}
              <span class="text-caption text-grey-6 q-ml-sm">{{ (box.ergoTree || '').length / 2 }} bytes</span>
            </e-info-row>
            <e-info-row label="Registers">{{ box.registers.length ? box.registers.map((r) => r.key).join(', ') : 'none' }}</e-info-row>
            <e-info-row label="Tokens">{{ box.assets.length || 'none' }}</e-info-row>
          </div>
        </div>
      </q-card-section>
    </q-card>

    <q-card flat bordered class="q-mb-md" v-if="box.assets.length">
      <q-card-section class="q-py-sm"><div class="text-subtitle1 text-weight-medium">Tokens</div></q-card-section>
      <q-table flat :rows="box.assets" :columns="tokenColumns" row-key="tokenId" hide-pagination :rows-per-page-options="[0]" class="table-sm">
        <template v-slot:body="props">
          <q-tr :props="props">
            <q-td key="name" :props="props">
              <router-link :to="'/token/' + props.row.tokenId" class="text-primary2 row items-center no-wrap">
                <q-avatar :style="{ background: tokenColor(props.row.tokenId) }" text-color="white" size="22px" font-size="9px" class="q-mr-sm">{{
                  (props.row.name || '?').slice(0, 2).toUpperCase()
                }}</q-avatar>
                <span class="text-weight-medium">{{ props.row.name || 'Unnamed token' }}</span>
              </router-link>
            </q-td>
            <q-td key="amount" :props="props" class="num">{{ formatTokenAmount(props.row.amount, props.row.decimals) }}</q-td>
            <q-td key="tokenId" :props="props"><e-hash :value="props.row.tokenId" :to="'/token/' + props.row.tokenId" :start="12" :end="8" /></q-td>
            <q-td key="decimals" :props="props">{{ props.row.decimals }}</q-td>
          </q-tr>
        </template>
      </q-table>
    </q-card>

    <div class="row q-col-gutter-sm">
      <div class="col-12 col-md-6">
        <q-card flat bordered class="full-height">
          <q-card-section class="q-py-sm">
            <div class="text-subtitle1 text-weight-medium">Registers</div>
            <div class="text-caption text-grey-6">R4–R9 · decoded from the Sigma serialization</div>
          </q-card-section>
          <q-separator />
          <q-card-section v-if="!box.registers.length" class="text-grey-6">This box carries no additional registers.</q-card-section>
          <div v-else>
            <div v-for="r in box.registers" :key="r.key" class="reg">
              <div class="row items-center q-gutter-x-sm">
                <span class="rk">{{ r.key }}</span>
                <q-badge color="grey-3" text-color="grey-8" :label="r.type" />
              </div>
              <div class="rv mono q-mt-xs">{{ r.value }}</div>
              <div class="text-caption text-grey-5 mono ellipsis q-mt-xs" :title="r.raw">raw {{ r.raw }}</div>
            </div>
          </div>
        </q-card>
      </div>
      <div class="col-12 col-md-6">
        <q-card flat bordered class="full-height">
          <q-card-section class="q-py-sm row items-center justify-between">
            <div>
              <div class="text-subtitle1 text-weight-medium">ErgoTree</div>
              <div class="text-caption text-grey-6">
                {{ isP2PK ? 'Pay-to-public-key: spendable with a Schnorr signature for the embedded key.' : 'Pay-to-script: spendable when the tree evaluates to true.' }}
              </div>
            </div>
            <q-btn dense flat round icon="content_copy" color="grey-7" @click="copyTree"><q-tooltip>Copy</q-tooltip></q-btn>
          </q-card-section>
          <q-separator />
          <q-card-section class="mono tree text-grey-8">{{ box.ergoTree }}</q-card-section>
        </q-card>
      </div>
    </div>
  </template>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useQuasar, copyToClipboard } from 'quasar'
import NotFound from './NotFound.vue'
import { getBox, tokenColor } from 'services/Explorer'
import { formatInt, formatTokenAmount } from 'services/ErgoUtils'

defineOptions({
  name: 'BoxDetailPage',
})

const $q = useQuasar()
const route = useRoute()
const isLoading = ref(true)
const box = ref(null)

// P2PK ErgoTree: 0008cd + 33-byte compressed public key
const isP2PK = computed(() => !!box.value && /^0008cd[0-9a-f]{66}$/i.test(box.value.ergoTree || ''))

const tokenColumns = [
  { name: 'name', align: 'left', label: 'Token', field: 'name' },
  { name: 'amount', align: 'right', label: 'Amount', field: 'amount' },
  { name: 'tokenId', align: 'left', label: 'Token id', field: 'tokenId' },
  { name: 'decimals', align: 'right', label: 'Decimals', field: 'decimals' },
]

async function copyTree() {
  await copyToClipboard(box.value.ergoTree)
  $q.notify({ type: 'positive', message: 'ErgoTree copied', timeout: 1200 })
}

async function load() {
  isLoading.value = true
  box.value = await getBox(route.params.id)
  isLoading.value = false
}

watch(() => route.params.id, load, { immediate: true })
</script>

<style scoped lang="scss">
.full-height {
  height: 100%;
}

.num {
  font-variant-numeric: tabular-nums;
}

.mono {
  font-family: $mono-font-family;
  font-size: 0.92em;
}

.tree {
  word-break: break-all;
  line-height: 1.6;
}

.reg {
  padding: 10px 16px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.08);
  &:last-child {
    border-bottom: 0;
  }
  .rk {
    font-weight: 600;
    color: #ff7043;
  }
  .rv {
    word-break: break-all;
    color: #333;
  }
}
</style>
