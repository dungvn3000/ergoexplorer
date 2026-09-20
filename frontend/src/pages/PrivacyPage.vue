<template>
  <div class="text-h6 text-grey-8 q-pb-sm"><q-icon name="shield" /> Privacy</div>
  <div class="text-caption text-grey-6 q-mb-md">Last updated 20 September 2026 · applies to explorer.erg.vn and its API</div>

  <q-card flat bordered class="q-mb-md">
    <q-card-section>
      <div class="text-subtitle1 text-weight-medium q-mb-xs">The short version</div>
      <div class="text-grey-8">
        Ergo Explorer does not track you. There are no accounts, no cookies, no analytics, no advertising, no third-party scripts and
        <strong>no server-side logging of who requested what</strong>. The site shows public Ergo blockchain data and nothing about its visitors is recorded.
      </div>
    </q-card-section>
  </q-card>

  <div class="row q-col-gutter-sm">
    <div class="col-12 col-md-6" v-for="s in SECTIONS" :key="s.title">
      <q-card flat bordered class="full-height">
        <q-card-section class="q-py-sm">
          <div class="text-subtitle1 text-weight-medium"><q-icon :name="s.icon" class="q-mr-xs" />{{ s.title }}</div>
        </q-card-section>
        <q-separator />
        <q-list dense separator>
          <q-item v-for="(p, i) in s.points" :key="i">
            <q-item-section avatar class="pt">
              <q-icon :name="p.ok ? 'check_circle' : 'info'" :color="p.ok ? 'green-7' : 'grey-6'" size="18px" />
            </q-item-section>
            <q-item-section>
              <q-item-label>{{ p.text }}</q-item-label>
              <q-item-label caption v-if="p.detail">{{ p.detail }}</q-item-label>
            </q-item-section>
          </q-item>
        </q-list>
      </q-card>
    </div>
  </div>

  <q-card flat bordered class="q-mt-md">
    <q-card-section class="q-py-sm"
      ><div class="text-subtitle1 text-weight-medium"><q-icon name="mail" class="q-mr-xs" />Questions</div></q-card-section
    >
    <q-separator />
    <q-card-section class="text-grey-8">
      Ergo Explorer is run by <a href="https://erg.vn" target="_blank" rel="noopener" class="text-primary2">Ergo Vietnam</a>. The source of both the frontend and the indexer is what you see described
      on the <router-link to="/about" class="text-primary2">About</router-link> page; if you believe a pool or contract label is wrong or want one removed, contact us there. Because the data is the
      public Ergo blockchain, the explorer cannot delete transactions or addresses — it only displays them.
    </q-card-section>
  </q-card>
</template>

<script setup>
defineOptions({
  name: 'PrivacyPage',
})

const SECTIONS = [
  {
    title: 'What we do not collect',
    icon: 'block',
    points: [
      {
        ok: true,
        text: 'No IP addresses, user agents or request logs',
        detail: 'The web server (Caddy) runs with access logging disabled; the API process logs only its own errors and indexer progress, never requests.',
      },
      { ok: true, text: 'No cookies, no localStorage, no fingerprinting', detail: 'The app keeps nothing in your browser between visits — not even preferences.' },
      { ok: true, text: 'No analytics or advertising', detail: 'No Google Analytics, Matomo, Plausible, pixels or ad networks. Nothing reports page views anywhere.' },
      { ok: true, text: 'No third-party requests', detail: 'Fonts, icons, scripts and styles are served from explorer.erg.vn itself. Your browser talks to no other domain.' },
      { ok: true, text: 'No accounts, no sign-in, no e-mail', detail: 'Every page is public; there is nothing to register for.' },
    ],
  },
  {
    title: 'How a page view works',
    icon: 'route',
    points: [
      {
        ok: false,
        text: 'Your browser requests explorer.erg.vn over HTTPS',
        detail: 'The connection is encrypted (Let’s Encrypt). Search terms and addresses you open are sent to the API on the same host, never to a third party.',
      },
      {
        ok: false,
        text: 'The API answers from its own database',
        detail: 'A MySQL index of the chain. When a block is not indexed yet the server asks the Ergo full nodes sv1.erg.vn / sv2.erg.vn — from the server, so the nodes never see your IP.',
      },
      {
        ok: false,
        text: 'Everything shown is public blockchain data',
        detail: 'Blocks, transactions, boxes, addresses and tokens are on the Ergo ledger for anyone to read. Pool names are labels we attach to well-known addresses.',
      },
      { ok: false, text: 'Transient data only', detail: 'Responses are cached in server memory for seconds to minutes to spare the nodes; caches hold chain data, not who asked for it.' },
      { ok: false, text: 'The API is open', detail: 'You can call /api/v1 directly; the same no-logging policy applies. Please be gentle with request rates.' },
    ],
  },
]
</script>

<style scoped lang="scss">
.full-height {
  height: 100%;
}
.pt {
  min-width: 28px;
  padding-right: 4px;
}
</style>
