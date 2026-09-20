const routes = [
  {
    // Ergo Explorer — every page is public, no login (meta.public)
    path: '/',
    component: () => import('layouts/MainLayout.vue'),
    meta: { public: true },
    children: [
      { path: '', name: 'ExplorerHome', component: () => import('pages/HomePage.vue'), meta: { public: true } },
      { path: 'blocks', name: 'ExplorerBlocks', component: () => import('pages/BlockListPage.vue'), meta: { public: true } },
      { path: 'block/:id', name: 'ExplorerBlock', component: () => import('pages/BlockDetailPage.vue'), meta: { public: true } },
      { path: 'tx/:id', name: 'ExplorerTx', component: () => import('pages/TxDetailPage.vue'), meta: { public: true } },
      { path: 'box/:id', name: 'ExplorerBox', component: () => import('pages/BoxDetailPage.vue'), meta: { public: true } },
      { path: 'address/:address', name: 'ExplorerAddress', component: () => import('pages/AddressDetailPage.vue'), meta: { public: true } },
      { path: 'tokens', name: 'ExplorerTokens', component: () => import('pages/TokenListPage.vue'), meta: { public: true } },
      { path: 'richlist', name: 'ExplorerRichList', component: () => import('pages/RichListPage.vue'), meta: { public: true } },
      { path: 'token/:id', name: 'ExplorerToken', component: () => import('pages/TokenDetailPage.vue'), meta: { public: true } },
      { path: 'mempool', name: 'ExplorerMempool', component: () => import('pages/MempoolPage.vue'), meta: { public: true } },
      { path: 'charts/:chart?', name: 'ExplorerCharts', component: () => import('pages/ChartsPage.vue'), meta: { public: true } },
      { path: 'api', name: 'ExplorerApi', component: () => import('pages/PlaceholderPage.vue'), meta: { public: true, page: 'api' } },
      { path: 'about', name: 'ExplorerAbout', component: () => import('pages/AboutPage.vue'), meta: { public: true } },
      { path: 'privacy', name: 'ExplorerPrivacy', component: () => import('pages/PrivacyPage.vue'), meta: { public: true } },
    ],
  },
  // Always leave this as last one,
  // but you can also remove it
  {
    path: '/error',
    name: 'Error',
    component: () => import('pages/ErrorPage.vue'),
  },
  {
    path: '/:catchAll(.*)*',
    component: () => import('pages/ErrorNotFound.vue'),
  },
]

export default routes
