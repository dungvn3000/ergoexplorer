<template>
  <q-breadcrumbs class="text-grey-7 q-mb-sm" active-color="grey-8">
    <q-breadcrumbs-el label="Overview" to="/" />
    <q-breadcrumbs-el v-if="tx && tx.pending" label="Mempool" to="/mempool" />
    <q-breadcrumbs-el v-else-if="tx" :label="'Block ' + formatInt(tx.height)" :to="'/block/' + tx.height" />
    <q-breadcrumbs-el label="Transaction" />
  </q-breadcrumbs>

  <t-loading v-if="isLoading" width="50px" />
  <NotFound v-else-if="!tx" kind="transaction" :value="route.params.id" />

  <template v-else>
    <div class="row items-start justify-between q-col-gutter-sm q-mb-md">
      <div class="col-12 col-md">
        <div class="row items-center q-gutter-x-sm">
          <div class="text-h6 text-grey-8"><q-icon name="swap_horiz" /> Transaction</div>
          <q-badge v-if="tx.pending" color="orange-1" text-color="orange-9" label="Pending · in mempool" />
          <q-badge v-else color="green-1" text-color="green-8" :label="formatInt(tx.confirmations) + (tx.confirmations > 1 ? ' confirmations' : ' confirmation')" />
          <q-badge :color="tx.coinbase ? 'deep-orange-1' : 'grey-3'" :text-color="tx.coinbase ? 'deep-orange-9' : 'grey-8'" :label="tx.kind" />
        </div>
        <div class="text-grey-7"><e-hash :value="tx.id" full /></div>
      </div>
      <div class="col-auto">
        <e-raw-json title="Transaction" :sources="[{ label: 'Explorer API', value: 'api', path: '/transactions/' + tx.id }]" />
      </div>
    </div>

    <q-card flat bordered class="q-mb-md">
      <q-card-section>
        <div class="row q-col-gutter-x-xl">
          <div class="col-12 col-md-6">
            <e-info-row label="Block">
              <span v-if="tx.pending" class="text-grey-6">— not yet included in a block</span>
              <template v-else>
                <router-link :to="'/block/' + tx.height" class="text-primary2 text-weight-medium">{{ formatInt(tx.height) }}</router-link>
                <span class="text-grey-6 q-ml-sm">{{ shortHash(tx.blockId, 8, 6) }}</span>
              </template>
            </e-info-row>
            <e-info-row label="Timestamp"
              >{{ formatTimestamp(tx.timestamp) }} <span class="text-grey-6">· {{ timeAgo(tx.timestamp) }}</span></e-info-row
            >
            <e-info-row label="Index in block">{{ tx.pending ? '—' : tx.index }}</e-info-row>
            <e-info-row label="Size">{{ formatInt(tx.size) }} bytes</e-info-row>
          </div>
          <div class="col-12 col-md-6">
            <e-info-row label="Total input"><e-erg :value="inputTotal" /></e-info-row>
            <e-info-row label="Total output"><e-erg :value="outputTotal" /></e-info-row>
            <e-info-row label="Fee"
              ><e-erg :value="tx.fee" /> <span class="text-grey-6">· {{ (tx.fee / tx.size / 1000).toFixed(3) }} µERG/byte</span></e-info-row
            >
            <e-info-row label="Data input">{{ tx.dataInputs.length }}</e-info-row>
          </div>
        </div>
      </q-card-section>
    </q-card>

    <div class="row q-col-gutter-sm io-grid">
      <div class="col-12 col-md">
        <q-card flat bordered>
          <q-card-section class="row items-center justify-between q-py-sm">
            <div class="text-subtitle2">
              Inputs <span class="text-grey-6">({{ tx.inputs.length }})</span>
            </div>
            <div class="text-caption text-grey-6">{{ formatErg(inputTotal, 4) }} ERG</div>
          </q-card-section>
          <q-separator />
          <e-box v-for="b in tx.inputs" :key="b.boxId" :box="b" />
          <template v-if="tx.dataInputs.length">
            <q-separator />
            <q-card-section class="row items-center justify-between q-py-sm">
              <div class="text-subtitle2">
                Data input <span class="text-grey-6">({{ tx.dataInputs.length }})</span>
              </div>
              <div class="text-caption text-grey-6">read-only, not spent</div>
            </q-card-section>
            <q-separator />
            <e-box v-for="b in tx.dataInputs" :key="b.boxId" :box="b" />
          </template>
        </q-card>
      </div>
      <div class="col-12 col-md-auto flex flex-center arrow">
        <q-icon name="arrow_forward" size="28px" color="grey-5" />
      </div>
      <div class="col-12 col-md">
        <q-card flat bordered>
          <q-card-section class="row items-center justify-between q-py-sm">
            <div class="text-subtitle2">
              Outputs <span class="text-grey-6">({{ tx.outputs.length }})</span>
            </div>
            <div class="text-caption text-grey-6">{{ formatErg(outputTotal, 4) }} ERG</div>
          </q-card-section>
          <q-separator />
          <e-box v-for="b in tx.outputs" :key="b.boxId" :box="b" output />
        </q-card>
      </div>
    </div>
  </template>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import NotFound from './NotFound.vue'
import { getTransaction } from 'services/Explorer'
import { formatInt, formatErg, formatTimestamp, timeAgo, shortHash } from 'services/ErgoUtils'

defineOptions({
  name: 'TxDetailPage',
})

const route = useRoute()
const isLoading = ref(true)
const tx = ref(null)

const inputTotal = computed(() => (tx.value ? tx.value.inputs.reduce((s, o) => s + o.value, 0) : 0))
const outputTotal = computed(() => (tx.value ? tx.value.outputs.reduce((s, o) => s + o.value, 0) : 0))

async function load() {
  isLoading.value = true
  tx.value = await getTransaction(route.params.id)
  isLoading.value = false
}

watch(() => route.params.id, load, { immediate: true })
</script>

<style scoped lang="scss">
.io-grid .arrow {
  @media (max-width: 1023px) {
    transform: rotate(90deg);
    padding: 4px 0;
  }
}
</style>
