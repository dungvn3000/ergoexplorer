<template>
  <div class="text-h6 text-grey-8 q-pb-sm"><q-icon name="info" /> About</div>
  <div class="text-caption text-grey-6 q-mb-md">
    Ergo Explorer indexes the Ergo mainnet from full nodes into its own database and serves blocks, transactions, addresses, tokens, rich lists and charts from it.
  </div>

  <q-banner dense rounded class="bg-orange-1 q-mb-md">
    <template v-slot:avatar><q-icon name="info" color="orange-8" /></template>
    <span class="text-weight-medium">Fan-made explorer.</span> This site is an independent, community-run project and is not affiliated with or endorsed by the Ergo Foundation or the Ergo Platform
    team. The official explorer is
    <a href="https://explorer.ergoplatform.com/" target="_blank" rel="noopener" class="text-weight-medium text-orange-10">explorer.ergoplatform.com <q-icon name="open_in_new" size="12px" /></a>. Data
    here is read from public Ergo nodes and may lag or differ; always verify important balances and transactions there or on your own node.
  </q-banner>

  <div class="row q-col-gutter-sm q-mb-md">
    <div class="col-12 col-sm-6 col-md-3" v-for="s in status" :key="s.label">
      <q-card flat bordered class="full-height">
        <q-card-section>
          <div class="text-caption text-grey-6">{{ s.label }}</div>
          <div class="text-subtitle1 text-weight-bold num">{{ s.value }}</div>
          <div class="text-caption text-grey-6">{{ s.sub }}</div>
        </q-card-section>
      </q-card>
    </div>
  </div>

  <div class="row q-col-gutter-sm">
    <div class="col-12 col-md-6" v-for="group in STACK" :key="group.title">
      <q-card flat bordered class="full-height">
        <q-card-section class="q-py-sm">
          <div class="text-subtitle1 text-weight-medium"><q-icon :name="group.icon" class="q-mr-xs" />{{ group.title }}</div>
          <div class="text-caption text-grey-6">{{ group.subtitle }}</div>
        </q-card-section>
        <q-separator />
        <q-list separator dense>
          <q-item v-for="item in group.items" :key="item.name">
            <q-item-section>
              <q-item-label>
                <a v-if="item.url" :href="item.url" target="_blank" rel="noopener" class="text-weight-medium text-primary2"
                  >{{ item.name }} <q-icon name="open_in_new" size="12px" class="text-grey-5"
                /></a>
                <span v-else class="text-weight-medium">{{ item.name }}</span>
                <span class="text-grey-6 q-ml-xs" v-if="item.version">{{ item.version }}</span>
              </q-item-label>
              <q-item-label caption>{{ item.role }}</q-item-label>
            </q-item-section>
          </q-item>
        </q-list>
      </q-card>
    </div>
  </div>

  <q-card flat bordered class="q-mt-md">
    <q-card-section class="q-py-sm"
      ><div class="text-subtitle1 text-weight-medium"><q-icon name="account_tree" class="q-mr-xs" />How it works</div></q-card-section
    >
    <q-separator />
    <q-card-section>
      <div class="row q-col-gutter-md">
        <div class="col-12 col-md-4" v-for="step in FLOW" :key="step.title">
          <div class="text-weight-medium">{{ step.title }}</div>
          <div class="text-caption text-grey-7">{{ step.text }}</div>
        </div>
      </div>
    </q-card-section>
  </q-card>

  <q-card flat bordered class="q-mt-md">
    <q-card-section class="q-py-sm">
      <div class="row items-center justify-between">
        <div class="text-subtitle1 text-weight-medium"><q-icon name="code" class="q-mr-xs" />Source code</div>
        <a href="https://github.com/dungvn3000/ergoexplorer" target="_blank" rel="noopener" class="text-caption text-weight-medium text-primary2"
          >github.com/dungvn3000/ergoexplorer <q-icon name="open_in_new" size="12px" class="text-grey-5"
        /></a>
      </div>
    </q-card-section>
    <q-separator />
    <q-list separator>
      <q-item v-for="item in SOURCE" :key="item.title">
        <q-item-section>
          <q-item-label class="text-weight-medium">{{ item.title }}</q-item-label>
          <q-item-label caption>{{ item.text }}</q-item-label>
        </q-item-section>
      </q-item>
    </q-list>
  </q-card>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getInfo } from 'services/Explorer'
import { formatInt } from 'services/ErgoUtils'
import { formatBytes } from 'services/Utils'

defineOptions({
  name: 'AboutPage',
})

const info = ref(null)
const version = import.meta.env.VITE_APP_VERSION

const status = computed(() => [
  { label: 'Explorer', value: 'v' + (info.value ? info.value.version : version), sub: info.value ? 'built ' + info.value.buildDate : '' },
  { label: 'Ergo node', value: info.value ? 'v' + info.value.nodeVersion.split('-')[0] : '…', sub: info.value ? info.value.nodeName + ' · ' + info.value.peers + ' peers' : '' },
  {
    label: 'Chain index',
    value: info.value ? formatInt(info.value.indexedHeight) + ' / ' + formatInt(info.value.height) : '…',
    sub: info.value ? (info.value.indexSynced ? 'fully synced' : ((info.value.indexedHeight / info.value.height) * 100).toFixed(1) + '% indexed') : '',
  },
  {
    label: 'Database',
    value: info.value && info.value.db ? formatBytes(info.value.db.totalBytes) : '…',
    sub: info.value && info.value.db ? formatBytes(info.value.db.dataBytes) + ' data · ' + formatBytes(info.value.db.indexBytes) + ' indexes' : '',
  },
])

const STACK = [
  {
    title: 'Frontend',
    subtitle: 'Single-page app, hash router',
    icon: 'web',
    items: [
      { name: 'Vue', version: '3.5', url: 'https://vuejs.org', role: 'UI framework (Composition API, <script setup>)' },
      { name: 'Quasar', version: '2.33', url: 'https://quasar.dev', role: 'Component library and CLI (Vite build)' },
      { name: 'Vite', version: '8', url: 'https://vite.dev', role: 'Dev server and bundler' },
      { name: 'Vue Router', version: '5', url: 'https://router.vuejs.org', role: 'Routing' },
      { name: 'Pinia', version: '3', url: 'https://pinia.vuejs.org', role: 'State management' },
      { name: 'Axios', version: '1', url: 'https://axios-http.com', role: 'HTTP client for the explorer API' },
      { name: 'Chart.js', version: '4', url: 'https://www.chartjs.org', role: 'Area charts (vue-chartjs, date-fns adapter)' },
      { name: 'date-fns', version: '4', url: 'https://date-fns.org', role: 'Dates and relative times' },
      { name: 'qrcode', version: '1.5', url: 'https://github.com/soldair/node-qrcode', role: 'Address QR codes' },
    ],
  },
  {
    title: 'Backend',
    subtitle: 'REST API and chain indexer, one Java process',
    icon: 'dns',
    items: [
      { name: 'Java', version: '21', url: 'https://openjdk.org/projects/jdk/21/', role: 'Virtual threads for node fan-out' },
      { name: 'Jooby', version: '4.5', url: 'https://jooby.io', role: 'Web framework on Netty, MVC controllers' },
      { name: 'Guice', version: '7', url: 'https://github.com/google/guice', role: 'Dependency injection' },
      { name: 'Ebean', version: '18.5', url: 'https://ebean.io', role: 'ORM: entities, query beans, DAOs' },
      { name: 'Flyway', version: '11', url: 'https://flywaydb.org', role: 'Schema migrations' },
      { name: 'HikariCP', version: '6', url: 'https://github.com/brettwooldridge/HikariCP', role: 'JDBC connection pool' },
      { name: 'Caffeine', version: '3', url: 'https://github.com/ben-manes/caffeine', role: 'In-memory caches (blocks, tokens, stats)' },
      { name: 'Bouncy Castle', version: '1.86', url: 'https://www.bouncycastle.org/java.html', role: 'Blake2b-256 for ErgoTree → address' },
      { name: 'Jackson', version: '2', url: 'https://github.com/FasterXML/jackson', role: 'JSON' },
    ],
  },
  {
    title: 'Data',
    subtitle: 'Where the numbers come from',
    icon: 'storage',
    items: [
      { name: 'MySQL 8 / MariaDB 10.6+', version: '', url: 'https://mariadb.org', role: 'Chain index: blocks, transactions, boxes, tokens, aggregates and daily rollups' },
      { name: 'Ergo full nodes', version: '', url: 'https://github.com/ergoplatform/ergo', role: 'sv1.erg.vn and sv2.erg.vn, load-balanced; extra indexer enabled for fallback reads and the mempool' },
      {
        name: 'Emission schedule',
        version: '',
        url: 'https://github.com/ergoplatform/eips/blob/master/eip-0027.md',
        role: 'Circulating supply = issued − re-emission share (EIP-27), computed from the schedule',
      },
    ],
  },
  {
    title: 'Deployment',
    subtitle: 'Production layout',
    icon: 'cloud',
    items: [
      { name: 'Caddy', version: '2', url: 'https://caddyserver.com', role: 'HTTPS, static frontend, /api reverse proxy' },
      { name: 'systemd', version: '', url: 'https://systemd.io', role: 'Backend service (stork launcher)' },
      { name: 'Maven', version: '3', url: 'https://maven.apache.org', role: 'Build; Ebean enhancement at process-classes' },
      { name: 'Docker', version: '', url: 'https://www.docker.com', role: 'Optional image for the backend' },
    ],
  },
]

const FLOW = [
  {
    title: '1. Index',
    text: 'The indexer pulls full blocks from the nodes in parallel batches and writes blocks, transactions and boxes with sequential keys in chain order, binary hashes, ErgoTrees and addresses deduplicated into a script table, spends as separate rows, plus per-address / per-token / per-day aggregates — one transaction per batch. Reorgs roll the affected block back.',
  },
  {
    title: '2. Serve',
    text: 'The API answers from the database first; heights not indexed yet fall back to the node, so the explorer works while the initial sync runs. Balances, rich lists and charts need a complete index.',
  },
  {
    title: '3. Show',
    text: 'The Quasar frontend calls /api/v1 on the same host. Addresses are derived from ErgoTrees locally, registers R4–R9 are decoded, and the emission schedule gives the circulating supply.',
  },
]

const SOURCE = [
  {
    title: 'Open source, MIT',
    text: 'The whole explorer is published under the MIT license at github.com/dungvn3000/ergoexplorer: the Java backend (REST API, chain indexer, MCP server) and this Quasar frontend.',
  },
  {
    title: 'Run your own',
    text: 'Build with Maven and Quasar, point ergo.nodes at your full node with the extra indexer enabled, and Flyway creates the MySQL schema on first start. Deployment notes are in backend/deploy.',
  },
  {
    title: 'Contribute',
    text: 'Bugs, wrong numbers and missing pool labels are best reported as GitHub issues with the block, transaction or address in question. Pull requests are welcome.',
  },
]

onMounted(async () => {
  try {
    info.value = await getInfo()
  } catch {
    info.value = null
  }
})
</script>

<style scoped lang="scss">
.full-height {
  height: 100%;
}
.num {
  font-variant-numeric: tabular-nums;
}
</style>
