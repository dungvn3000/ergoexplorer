<template>
  <span class="no-wrap">
    <span class="text-weight-medium num">{{ formatErg(value, decimals) }}</span>
    <span class="text-grey-6 q-ml-xs">ERG</span>
  </span>
</template>
<script setup>
import { formatErg } from 'services/ErgoUtils'

defineProps({
  value: { type: Number, required: true }, // nanoERG
  decimals: { type: Number, default: 9 },
})

defineOptions({
  name: 'EErg',
})
</script>
<style scoped lang="scss">
.num {
  font-variant-numeric: tabular-nums;
}
</style>
