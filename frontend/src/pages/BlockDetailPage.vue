<template>
  <q-breadcrumbs class="text-grey-7 q-mb-sm" active-color="grey-8">
    <q-breadcrumbs-el label="Overview" to="/" />
    <q-breadcrumbs-el label="Blocks" to="/blocks" />
    <q-breadcrumbs-el :label="block ? formatInt(block.height) : '…'" />
  </q-breadcrumbs>

  <t-loading v-if="isLoading" width="50px" />
  <NotFound v-else-if="!block" kind="block" :value="route.params.id" />

  <template v-else>
    <div class="row items-start justify-between q-col-gutter-sm q-mb-md">
      <div class="col-12 col-md">
        <div class="row items-center q-gutter-x-sm">
          <div class="text-h6 text-grey-8"><q-icon name="view_in_ar" /> Block {{ formatInt(block.height) }}</div>
          <q-badge color="green-1" text-color="green-8" :label="block.confirmations < 10 ? block.confirmations + (block.confirmations > 1 ? ' confirmations' : ' confirmation') : 'Confirmed'" />
          <q-badge v-if="block.votes !== '0,0,0'" color="orange-1" text-color="orange-9" :label="'Votes ' + block.votes" />
        </div>
        <div class="text-grey-7"><e-hash :value="block.id" full /></div>
      </div>
      <div class="col-auto row q-gutter-x-xs">
        <e-raw-json
          :title="'Block ' + formatInt(block.height)"
          :sources="[
            { label: 'Node', value: 'node', path: '/blocks/' + block.id + '/raw' },
            { label: 'Explorer API', value: 'api', path: '/blocks/' + block.id },
          ]"
        />
        <q-btn dense outline color="grey-8" icon="chevron_left" :label="formatInt(block.height - 1)" no-caps :to="'/block/' + (block.height - 1)" />
        <q-btn dense outline color="grey-8" icon-right="chevron_right" :label="formatInt(block.height + 1)" no-caps :to="'/block/' + (block.height + 1)" :disable="block.confirmations <= 1" />
      </div>
    </div>

    <q-card flat bordered class="q-mb-md">
      <q-card-section>
        <div class="row q-col-gutter-x-xl">
          <div class="col-12 col-md-6">
            <e-info-row label="Timestamp"
              >{{ formatTimestamp(block.timestamp) }} <span class="text-grey-6">· {{ timeAgo(block.timestamp) }}</span></e-info-row
            >
            <e-info-row label="Transactions"
              >{{ block.txCount }} <span class="text-grey-6">· {{ formatBytes(block.size) }}</span></e-info-row
            >
            <e-info-row label="Miner"
              ><e-address :address="block.minerAddress" /> <span class="text-grey-6 q-ml-xs">{{ shortHash(block.minerAddress, 6, 6) }}</span></e-info-row
            >
            <e-info-row label="Miner reward"
              ><e-erg :value="block.reward" /> <span class="text-grey-6">+ {{ formatErg(block.fees, 4) }} ERG fee</span></e-info-row
            >
            <e-info-row label="Emission"
              ><e-erg :value="block.emission" /> <span class="text-grey-6" v-if="block.reemitted">· {{ formatErg(block.reemitted) }} ERG to the re-emission contract (EIP-27)</span></e-info-row
            >
            <e-info-row label="Confirmations">{{ formatInt(block.confirmations) }}</e-info-row>
          </div>
          <div class="col-12 col-md-6">
            <e-info-row label="Difficulty">{{ formatInt(Math.round(block.difficulty)) }}</e-info-row>
            <e-info-row label="nBits"
              ><span class="mono">{{ block.nBits }}</span></e-info-row
            >
            <e-info-row label="Epoch"
              >{{ formatInt(Math.floor(block.height / 1024)) }} <span class="text-grey-6">· block {{ (block.height % 1024) + 1 }} / 1024</span></e-info-row
            >
            <e-info-row label="Version"
              >{{ block.version }} <span class="text-grey-6">· votes {{ block.votes }}</span></e-info-row
            >
            <e-info-row label="Parent"><e-hash :value="block.parentId" :to="'/block/' + block.parentId" :start="12" :end="10" /></e-info-row>
            <e-info-row label="Size">{{ formatInt(block.size) }} bytes</e-info-row>
          </div>
        </div>
      </q-card-section>
    </q-card>

    <q-card flat bordered>
      <q-tabs v-model="tab" dense align="left" active-color="primary" indicator-color="primary" narrow-indicator class="text-grey-7">
        <q-tab name="txs" no-caps :label="`Transactions (${block.txCount})`" />
        <q-tab name="header" no-caps label="Header" />
        <q-tab name="pow" no-caps label="PoW (Autolykos v2)" />
        <q-tab name="ext" no-caps label="Extension" />
      </q-tabs>
      <q-separator />

      <q-tab-panels v-model="tab" animated>
        <q-tab-panel name="txs" class="q-pa-none">
          <div v-for="t in block.transactions" :key="t.id" class="tx-card">
            <div class="row items-center justify-between q-mb-xs">
              <div class="row items-center q-gutter-x-sm">
                <e-hash :value="t.id" :to="'/tx/' + t.id" :start="14" :end="10" />
                <q-badge :color="t.coinbase ? 'deep-orange-1' : 'grey-3'" :text-color="t.coinbase ? 'deep-orange-9' : 'grey-8'" :label="t.kind" />
              </div>
              <div class="text-caption text-grey-6">{{ formatErg(outputTotal(t), 4) }} ERG · fee {{ formatErg(t.fee, 4) }} · {{ formatBytes(t.size) }}</div>
            </div>
            <div class="row q-col-gutter-x-lg">
              <div class="col-12 col-md-6">
                <div class="text-caption text-grey-5 text-uppercase">Inputs ({{ t.inputs.length }})</div>
                <div v-for="o in t.inputs.slice(0, 4)" :key="o.boxId" class="row justify-between no-wrap io-line">
                  <span class="ellipsis"
                    ><e-address :address="o.address" :start="10" :end="8" /><q-badge v-if="o.assets.length" outline color="grey-6" class="q-ml-xs" :label="'+' + o.assets.length + ' token'"
                  /></span>
                  <span class="text-grey-8 q-ml-sm">{{ formatErg(o.value, 4) }} ERG</span>
                </div>
                <div v-if="t.inputs.length > 4" class="text-caption text-grey-6">… {{ t.inputs.length - 4 }} more</div>
              </div>
              <div class="col-12 col-md-6">
                <div class="text-caption text-grey-5 text-uppercase">Outputs ({{ t.outputs.length }})</div>
                <div v-for="o in t.outputs.slice(0, 4)" :key="o.boxId" class="row justify-between no-wrap io-line">
                  <span class="ellipsis"
                    ><e-address :address="o.address" :start="10" :end="8" /><q-badge v-if="o.assets.length" outline color="grey-6" class="q-ml-xs" :label="'+' + o.assets.length + ' token'"
                  /></span>
                  <span class="text-grey-8 q-ml-sm">{{ formatErg(o.value, 4) }} ERG</span>
                </div>
                <div v-if="t.outputs.length > 4" class="text-caption text-grey-6">… {{ t.outputs.length - 4 }} more</div>
              </div>
            </div>
          </div>
        </q-tab-panel>

        <q-tab-panel name="header">
          <e-info-row label="Block id"><e-hash :value="block.id" full /></e-info-row>
          <e-info-row label="Parent"><e-hash :value="block.parentId" full /></e-info-row>
          <e-info-row label="State root"><e-hash :value="block.stateRoot" full /></e-info-row>
          <e-info-row label="Transactions root"><e-hash :value="block.txRoot" full /></e-info-row>
          <e-info-row label="AD proofs root"><e-hash :value="block.adRoot" full /></e-info-row>
          <e-info-row label="Extension hash"><e-hash :value="block.extHash" full /></e-info-row>
          <e-info-row label="Timestamp"
            ><span class="mono">{{ block.timestamp }}</span> <span class="text-grey-6">ms</span></e-info-row
          >
          <e-info-row label="Version">{{ block.version }}</e-info-row>
          <e-info-row label="Votes"
            ><span class="mono">{{ block.votes }}</span></e-info-row
          >
        </q-tab-panel>

        <q-tab-panel name="pow">
          <e-info-row label="Algorithm">Autolykos v2</e-info-row>
          <e-info-row label="pk (miner)"><e-hash :value="block.pow.pk" full /></e-info-row>
          <e-info-row label="w"><e-hash :value="block.pow.w" full /></e-info-row>
          <e-info-row label="n (nonce)"
            ><span class="mono">{{ block.pow.n }}</span></e-info-row
          >
          <e-info-row label="d"
            ><span class="mono">{{ block.pow.d }}</span></e-info-row
          >
          <p class="text-caption text-grey-6 q-mt-md">
            Autolykos v2 is memory-hard and ASIC-resistant. <span class="mono">pk</span> is the miner's public key, <span class="mono">w</span> is a one-time key, <span class="mono">d</span> is always
            0 in v2.
          </p>
        </q-tab-panel>

        <q-tab-panel name="ext">
          <e-info-row label="Extension hash"><e-hash :value="block.extHash" full /></e-info-row>
          <e-info-row label="Interlinks"><span class="text-grey-6">3 interlink vectors (NiPoPoW)</span></e-info-row>
        </q-tab-panel>
      </q-tab-panels>
    </q-card>
  </template>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import NotFound from './NotFound.vue'
import { getBlock } from 'services/Explorer'
import { formatInt, formatErg, formatTimestamp, timeAgo, shortHash } from 'services/ErgoUtils'
import { formatBytes } from 'services/Utils'

defineOptions({
  name: 'BlockDetailPage',
})

const route = useRoute()
const isLoading = ref(true)
const block = ref(null)
const tab = ref('txs')

const outputTotal = (tx) => tx.outputs.reduce((sum, o) => sum + o.value, 0)

async function load() {
  isLoading.value = true
  block.value = await getBlock(route.params.id)
  isLoading.value = false
}

watch(() => route.params.id, load, { immediate: true })
</script>

<style scoped lang="scss">
.mono {
  font-family: $mono-font-family;
  font-size: 0.92em;
}

.tx-card {
  padding: 12px 16px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.08);
  &:last-child {
    border-bottom: 0;
  }
}

.io-line {
  font-size: 13px;
  padding: 2px 0;
}
</style>
