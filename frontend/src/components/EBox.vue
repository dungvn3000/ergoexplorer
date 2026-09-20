<template>
  <div class="e-box">
    <div class="row items-baseline justify-between no-wrap">
      <div class="ellipsis"><e-address :address="box.address" :start="12" :end="10" /></div>
      <div class="q-ml-md no-wrap"><e-erg :value="box.value" /></div>
    </div>
    <div v-if="box.assets.length" class="q-mt-xs">
      <e-token v-for="a in box.assets" :key="a.tokenId" :token="a" />
    </div>
    <div class="row items-center q-gutter-x-sm text-caption text-grey-6 q-mt-xs">
      <span class="mono text-grey-5">#{{ box.index }}</span>
      <e-hash :value="box.boxId" :to="'/box/' + box.boxId" :start="10" :end="8" muted />
      <span>created at {{ formatInt(box.creationHeight) }}</span>
      <template v-if="output">
        <span v-if="box.spent">· spent in <e-hash :value="box.spentBy" :to="'/tx/' + box.spentBy" :start="8" :end="6" :copy="false" /></span>
        <q-badge v-else color="green-1" text-color="green-8" label="Unspent" />
      </template>
    </div>
    <q-expansion-item dense dense-toggle expand-icon-toggle switch-toggle-side label="ErgoTree & register" header-class="text-caption text-grey-7 q-px-none" class="q-mt-xs">
      <div class="regs text-caption">
        <div class="row no-wrap">
          <span class="rk">Tree</span><span class="rv mono">{{ box.ergoTree }}</span>
        </div>
        <div v-for="r in box.registers" :key="r.key" class="row no-wrap">
          <span class="rk">{{ r.key }}</span>
          <span class="rv"
            ><span class="mono">{{ r.value }}</span> <span class="text-grey-5">· {{ r.type }} · {{ r.raw }}</span></span
          >
        </div>
        <div v-if="!box.registers.length" class="row no-wrap"><span class="rk">R4–R9</span><span class="rv text-grey-5">empty</span></div>
      </div>
    </q-expansion-item>
  </div>
</template>
<script setup>
import { formatInt } from 'services/ErgoUtils'

defineProps({
  box: { type: Object, required: true },
  output: Boolean, // output box => show spent / unspent state
})

defineOptions({
  name: 'EBox',
})
</script>
<style scoped lang="scss">
.e-box {
  padding: 10px 14px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.08);
  &:last-child {
    border-bottom: 0;
  }
}
.mono {
  font-family: $mono-font-family;
  font-size: 0.92em;
}
.regs {
  padding: 4px 0 6px;
  .rk {
    flex: none;
    width: 52px;
    font-weight: 600;
    color: #ff7043;
  }
  .rv {
    word-break: break-all;
    color: #666;
  }
}
</style>
