<template>
  <q-toolbar class="bg-white text-black">
    <q-btn flat dense round icon="menu" aria-label="Menu" @click="$emit('toggleLeftDrawer')">
      <q-tooltip class="text-body2"> Hide or show sidebar (Ctrl + B)</q-tooltip>
    </q-btn>

    <router-link to="/" class="row items-center no-wrap q-pl-sm text-black brand">
      <img src="/img/logo.svg" alt="Ergo Explorer" class="navbar-brand" />
      <span class="text-weight-bold q-ml-xs"> Explorer</span>
    </router-link>

    <q-space />

    <q-form class="search-box" @submit.prevent="onSearch" v-if="$q.screen.gt.xs">
      <q-input v-model="query" dense outlined placeholder="Address, transaction, block, token or height" bg-color="grey-1" :loading="searching" @keyup.enter="onSearch">
        <template v-slot:prepend>
          <q-icon name="search" />
        </template>
        <template v-slot:append v-if="query">
          <q-icon name="close" class="cursor-pointer" @click="query = ''" />
        </template>
      </q-input>
    </q-form>

    <q-space />

    <div class="q-gutter-sm row items-center no-wrap">
      <q-chip dense outline color="grey-7" class="q-px-sm">
        <span class="net-dot bg-green q-mr-xs"></span>
        Mainnet
      </q-chip>
      <q-btn round dense flat :icon="$q.fullscreen.isActive ? 'fullscreen_exit' : 'fullscreen'" @click="$q.fullscreen.toggle()" v-if="$q.screen.gt.sm">
        <q-tooltip class="text-body2">Fullscreen</q-tooltip>
      </q-btn>
    </div>
  </q-toolbar>
</template>
<script setup>
import { onMounted, onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useQuasar } from 'quasar'
import { resolveSearch } from 'services/Explorer'

const $q = useQuasar()
const router = useRouter()
const query = ref('')
const searching = ref(false)

async function onSearch() {
  if (!query.value.trim() || searching.value) return
  searching.value = true
  const path = await resolveSearch(query.value).finally(() => (searching.value = false))
  if (!path) {
    $q.notify({ type: 'warning', message: 'Nothing found. Enter a block height, a block/transaction id (64 hex), an address or a token id.' })
    return
  }
  router.push(path)
  query.value = ''
}

function onGlobalKeydown(e) {
  if ((e.ctrlKey || e.metaKey) && e.keyCode === 66 /* B */) {
    e.preventDefault()
    emit('toggleLeftDrawer')
  }
}

onMounted(() => {
  window.addEventListener('keydown', onGlobalKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onGlobalKeydown)
})

const emit = defineEmits(['toggleLeftDrawer'])

defineProps({
  leftDrawerOpen: {
    type: Boolean,
    required: true,
  },
})

defineOptions({
  name: 'TopBar',
})
</script>

<style scoped lang="scss">
.brand {
  text-decoration: none;
  font-size: 15px;
}

.search-box {
  width: 100%;
  max-width: 520px;
}

.net-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
</style>
