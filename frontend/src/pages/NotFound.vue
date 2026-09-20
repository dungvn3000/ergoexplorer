<template>
  <q-card flat bordered class="q-pa-xl text-center">
    <q-icon name="search_off" size="48px" color="grey-5" />
    <div class="text-h6 text-grey-8 q-mt-sm">{{ kind.charAt(0).toUpperCase() + kind.slice(1) }} not found</div>
    <div class="text-grey-6">Nothing on mainnet matches this {{ kind }} identifier. Check for typos or try searching again.</div>
    <div class="text-caption text-grey-5 mono q-mt-sm">{{ value }}</div>
    <q-btn class="q-mt-lg" color="indigo-7" unelevated icon="home" to="/" label="Back to overview" no-caps />
  </q-card>
</template>

<script setup>
defineProps({
  kind: { type: String, default: 'page' },
  value: [String, Number],
})

defineOptions({
  name: 'ExplorerNotFound',
})
</script>

<style scoped lang="scss">
.mono {
  font-family: $mono-font-family;
  word-break: break-all;
}
</style>
