<template>
  <q-btn dense outline color="grey-8" icon="data_object" label="Raw JSON" no-caps @click="open" />

  <q-dialog v-model="show" @show="load">
    <q-card class="raw-json">
      <q-card-section class="row items-center q-py-sm">
        <div class="text-subtitle1 text-weight-medium">{{ title }}</div>
        <q-space />
        <q-btn-toggle v-model="source" dense no-caps unelevated toggle-color="indigo-7" color="grey-3" text-color="grey-8" :options="sourceOptions" v-if="sourceOptions.length > 1" />
        <q-btn flat dense round icon="content_copy" class="q-ml-sm" @click="copy" :disable="!text">
          <q-tooltip class="text-body2">Copy</q-tooltip>
        </q-btn>
        <q-btn flat dense round icon="open_in_new" type="a" :href="currentUrl" target="_blank" v-if="currentUrl">
          <q-tooltip class="text-body2">Open in new tab</q-tooltip>
        </q-btn>
        <q-btn flat dense round icon="close" v-close-popup />
      </q-card-section>
      <q-separator />
      <q-card-section class="q-pa-none body">
        <q-inner-loading :showing="loading" color="primary" />
        <div v-if="error" class="q-pa-md text-negative">{{ error }}</div>
        <pre v-else-if="text" class="q-ma-none q-pa-md">{{ text }}</pre>
      </q-card-section>
    </q-card>
  </q-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { copyToClipboard, useQuasar } from 'quasar'
import { apiUrl, getRaw } from 'services/Explorer'

const $q = useQuasar()

const props = defineProps({
  title: { type: String, default: 'Raw JSON' },
  // Sources to show: [{ label, value, data? (object already in hand) | path? (API path fetched on open) }]
  sources: { type: Array, required: true },
})

const show = ref(false)
const loading = ref(false)
const error = ref('')
const source = ref(props.sources[0].value)
const cache = ref({})

const sourceOptions = computed(() => props.sources.map((s) => ({ label: s.label, value: s.value })))
const current = computed(() => props.sources.find((s) => s.value === source.value))
const currentUrl = computed(() => (current.value && current.value.path ? apiUrl(current.value.path) : ''))
const text = computed(() => {
  const data = cache.value[source.value]
  return data ? JSON.stringify(data, null, 2) : ''
})

function open() {
  show.value = true
}

async function load() {
  const s = current.value
  if (!s || cache.value[s.value]) return
  error.value = ''
  if (s.data) {
    cache.value = { ...cache.value, [s.value]: s.data }
    return
  }
  loading.value = true
  try {
    const data = await getRaw(s.path)
    if (data === null) error.value = 'Not found'
    else cache.value = { ...cache.value, [s.value]: data }
  } catch (e) {
    error.value = 'Could not load: ' + (e.message || e)
  } finally {
    loading.value = false
  }
}

watch(source, load)

async function copy() {
  await copyToClipboard(text.value)
  $q.notify({ type: 'positive', message: 'JSON copied', timeout: 1200 })
}

defineOptions({
  name: 'ERawJson',
})
</script>

<style scoped lang="scss">
.raw-json {
  width: 900px;
  max-width: 95vw;
}
.body {
  position: relative;
  max-height: 75vh;
  overflow: auto;
  background: #fafafa;
  min-height: 120px;
}
pre {
  font-family: $mono-font-family;
  font-size: 12px;
  line-height: 1.45;
  white-space: pre;
}
</style>
