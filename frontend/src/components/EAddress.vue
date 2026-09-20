<template>
  <router-link :to="'/address/' + address" class="text-primary2 row inline items-center no-wrap" :title="address">
    <q-icon v-if="label && label.contract" name="description" size="14px" class="q-mr-xs text-deep-orange-6" />
    <span v-if="label && !raw" class="text-weight-medium">{{ label.name }}</span>
    <span v-else class="mono">{{ shortHash(address, start, end) }}</span>
  </router-link>
</template>
<script setup>
import { computed } from 'vue'
import { getAddressLabel } from 'services/Explorer'
import { shortHash } from 'services/ErgoUtils'

const props = defineProps({
  address: { type: String, required: true },
  start: { type: Number, default: 9 },
  end: { type: Number, default: 6 },
  raw: Boolean, // always show the hash even when a label exists
})

const label = computed(() => getAddressLabel(props.address))

defineOptions({
  name: 'EAddress',
})
</script>
<style scoped lang="scss">
.mono {
  font-family: $mono-font-family;
  font-size: 0.92em;
}
</style>
