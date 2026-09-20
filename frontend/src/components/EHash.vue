<template>
  <span class="e-hash row inline items-center no-wrap">
    <router-link v-if="to" :to="to" class="text-primary2 mono" :title="value">{{ display }}</router-link>
    <span v-else class="mono" :class="{ 'text-grey-7': muted }" :title="value">{{ display }}</span>
    <q-btn v-if="copy" flat dense round size="xs" icon="content_copy" color="grey-6" class="q-ml-xs" @click.stop.prevent="doCopy">
      <q-tooltip class="text-body2">Copy</q-tooltip>
    </q-btn>
  </span>
</template>
<script setup>
import { computed } from 'vue'
import { copyToClipboard, useQuasar } from 'quasar'
import { shortHash } from 'services/ErgoUtils'

const $q = useQuasar()

const props = defineProps({
  value: { type: String, required: true },
  to: String, // router path; leave empty to render as plain text
  start: { type: Number, default: 8 },
  end: { type: Number, default: 6 },
  full: Boolean, // render the full hash, no truncation
  copy: { type: Boolean, default: true },
  muted: Boolean,
})

const display = computed(() => (props.full ? props.value : shortHash(props.value, props.start, props.end)))

async function doCopy() {
  await copyToClipboard(props.value)
  $q.notify({ type: 'positive', message: 'Copied ' + shortHash(props.value, 6, 4), timeout: 1200 })
}

defineOptions({
  name: 'EHash',
})
</script>
<style scoped lang="scss">
.mono {
  font-family: $mono-font-family;
  font-size: 0.92em;
  word-break: break-all;
}
</style>
