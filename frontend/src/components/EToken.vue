<template>
  <q-chip dense clickable outline color="grey-7" :to="'/token/' + token.tokenId" class="e-token">
    <q-avatar :style="{ background: color }" text-color="white" size="18px" font-size="9px">{{ initials }}</q-avatar>
    <span class="text-black num">{{ formatTokenAmount(token.amount, token.decimals) }}</span>
    <span class="text-grey-8 q-ml-xs">{{ token.name }}</span>
  </q-chip>
</template>
<script setup>
import { computed } from 'vue'
import { tokenColor } from 'services/Explorer'
import { formatTokenAmount } from 'services/ErgoUtils'

const props = defineProps({
  // { tokenId, name, amount, decimals }
  token: { type: Object, required: true },
})

const color = computed(() => tokenColor(props.token.tokenId))
const initials = computed(() =>
  String(props.token.name || '?')
    .slice(0, 2)
    .toUpperCase(),
)

defineOptions({
  name: 'EToken',
})
</script>
<style scoped lang="scss">
.e-token {
  font-size: 12px;
  margin: 2px 4px 2px 0;
}
.num {
  font-variant-numeric: tabular-nums;
}
</style>
