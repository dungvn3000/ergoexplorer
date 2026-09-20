<template>
  <div class="row items-center q-gutter-x-sm q-pb-sm">
    <div class="text-h6 text-grey-8"><q-icon :name="icon" /> {{ title }}</div>
    <q-badge color="grey-3" text-color="grey-8" label="Read-only, no key required" />
  </div>
  <q-card flat bordered>
    <q-card-section>
      <p class="text-grey-8">{{ description }}</p>
      <q-list dense bordered separator class="rounded-borders q-mt-md" v-if="items.length">
        <q-item v-for="(it, i) in items" :key="i">
          <q-item-section avatar v-if="it.badge"><q-badge color="indigo-7" :label="it.badge" /></q-item-section>
          <q-item-section
            ><span class="mono">{{ it.text }}</span></q-item-section
          >
        </q-item>
      </q-list>
    </q-card-section>
  </q-card>

  <q-card flat bordered class="q-mt-md" v-if="mcp">
    <q-card-section>
      <div class="row items-center q-gutter-x-sm">
        <div class="text-subtitle1 text-weight-medium"><q-icon name="smart_toy" class="q-mr-xs" />MCP server for AI agents</div>
        <q-badge color="grey-3" text-color="grey-8" label="Streamable HTTP · stateless" />
      </div>
      <p class="text-grey-8 q-mt-sm">
        The same data is available to AI agents (Claude, Cursor, any MCP client) over the Model Context Protocol. Point the client at the endpoint below; no key, no session, nothing is stored about
        the caller.
      </p>
      <div class="text-caption text-grey-6">Endpoint</div>
      <div class="mono q-mb-sm">POST {{ mcp.endpoint }}</div>
      <div class="text-caption text-grey-6">Claude Desktop / Cursor config</div>
      <pre class="snippet mono">{{ mcp.config }}</pre>
      <div class="text-caption text-grey-6">Claude Code</div>
      <pre class="snippet mono">{{ mcp.cli }}</pre>
      <div class="text-caption text-grey-6">OpenCode — opencode.json (project root or ~/.config/opencode/)</div>
      <pre class="snippet mono">{{ mcp.opencode }}</pre>
      <q-list dense bordered separator class="rounded-borders q-mt-md">
        <q-item v-for="t in mcp.tools" :key="t[0]">
          <q-item-section avatar><q-badge color="teal-7" label="tool" /></q-item-section>
          <q-item-section>
            <span class="mono">{{ t[0] }}</span>
            <span class="text-caption text-grey-6">{{ t[1] }}</span>
          </q-item-section>
        </q-item>
      </q-list>
    </q-card-section>
  </q-card>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

defineOptions({
  name: 'ExplorerPlaceholderPage',
})

const route = useRoute()

const PAGES = {
  api: {
    icon: 'api',
    title: 'API',
    description: 'The explorer backend exposes a REST JSON API. Every answer is { data } or { errorCode, error }; all endpoints are public and read-only.',
    items: [
      { badge: 'GET', text: '/api/v1/info' },
      { badge: 'GET', text: '/api/v1/networkState' },
      { badge: 'GET', text: '/api/v1/blocks?page&rowsPerPage' },
      { badge: 'GET', text: '/api/v1/blocks/latest?limit' },
      { badge: 'GET', text: '/api/v1/blocks/{height|id}' },
      { badge: 'GET', text: '/api/v1/blocks/{height|id}/raw' },
      { badge: 'GET', text: '/api/v1/transactions/latest?limit' },
      { badge: 'GET', text: '/api/v1/transactions/{id}' },
      { badge: 'GET', text: '/api/v1/addresses/{address}?page&rowsPerPage' },
      { badge: 'GET', text: '/api/v1/addresses/{address}/boxes?page&rowsPerPage' },
      { badge: 'GET', text: '/api/v1/boxes/{id}' },
      { badge: 'GET', text: '/api/v1/tokens' },
      { badge: 'GET', text: '/api/v1/tokens/{id}' },
      { badge: 'GET', text: '/api/v1/tokens/{id}/holders?page&rowsPerPage' },
      { badge: 'GET', text: '/api/v1/richlist?page&rowsPerPage' },
      { badge: 'GET', text: '/api/v1/mempool/transactions' },
      { badge: 'GET', text: '/api/v1/charts' },
      { badge: 'GET', text: '/api/v1/charts/{name}?days' },
      { badge: 'GET', text: '/api/v1/search?q=' },
    ],
    mcp: {
      endpoint: 'https://explorer.erg.vn/api/mcp',
      config: '{ "mcpServers": { "ergo-explorer": { "url": "https://explorer.erg.vn/api/mcp" } } }',
      cli: 'claude mcp add --transport http ergo-explorer https://explorer.erg.vn/api/mcp',
      opencode: '{\n  "$schema": "https://opencode.ai/config.json",\n  "mcp": {\n    "ergo-explorer": { "type": "remote", "url": "https://explorer.erg.vn/api/mcp", "enabled": true }\n  }\n}',
      tools: [
        ['getNetworkState', 'height, hashrate, difficulty, supply, mempool, pool shares'],
        ['getBlock', 'block by height or id with its transactions'],
        ['listBlocks', 'newest blocks, paged'],
        ['getTransaction', 'transaction by id, confirmed or in the mempool'],
        ['getAddress', 'balance, tokens, history page of an address'],
        ['getAddressBoxes', 'unspent boxes of an address'],
        ['getBox', 'box by id with decoded registers'],
        ['getToken', 'EIP-4 token metadata, holders, transfers'],
        ['getTokenHolders', 'token rich list, paged'],
        ['listTokens', 'featured tokens'],
        ['getRichList', 'ERG rich list, paged'],
        ['listCharts', 'available chart series'],
        ['getChart', 'a chart series as {t, v} points'],
        ['getMempool', 'unconfirmed transactions'],
        ['search', 'what a string is: height, block, tx, box, token or address'],
        ['getStatus', 'indexed height and database size'],
      ],
    },
  },
}

const page = computed(() => PAGES[route.meta.page] || PAGES.api)
const icon = computed(() => page.value.icon)
const title = computed(() => page.value.title)
const description = computed(() => page.value.description)
const items = computed(() => page.value.items)
const mcp = computed(() => page.value.mcp || null)
</script>

<style scoped lang="scss">
.mono {
  font-family: $mono-font-family;
  font-size: 13px;
}
.snippet {
  background: #f4f5f9;
  border: 1px solid #e0e3ea;
  border-radius: 4px;
  padding: 8px 10px;
  margin: 2px 0 10px;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
