// Client for the explorer backend (backend/, Jooby) at /api/v1.
// Responses are wrapped as { errorCode, error, data }; a 404 resolves to null.
export const API_BASE = '/api/v1'

async function get(path, params) {
  const qs = params ? '?' + new URLSearchParams(params) : ''
  const res = await fetch(API_BASE + path + qs, { headers: { Accept: 'application/json' } })
  if (res.status === 404) return null
  let body = null
  try {
    body = await res.json()
  } catch {
    // not JSON (proxy error page): fall back to the HTTP status below
  }
  if (!res.ok) throw new Error((body && body.error) || `HTTP ${res.status}`)
  return body ? body.data : null
}

const enc = encodeURIComponent

export const getInfo = () => get('/info')
export const getNetworkState = () => get('/networkState')
export const getLatestBlocks = (limit = 10) => get('/blocks/latest', { limit })
export const getLatestTransactions = (limit = 10) => get('/transactions/latest', { limit })
// { items, total } — total is the chain height
export const getBlocks = (page = 1, rowsPerPage = 25) => get('/blocks', { page, rowsPerPage })
// by height or id
export const getBlock = (idOrHeight) => get('/blocks/' + enc(idOrHeight))
// the block exactly as the node returns it ({ header, blockTransactions, extension, adProofs, size }) — not wrapped in { data }
export async function getBlockRaw(id) {
  const res = await fetch(API_BASE + '/blocks/' + enc(id) + '/raw', { headers: { Accept: 'application/json' } })
  if (res.status === 404) return null
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json()
}
export const getTransaction = (id) => get('/transactions/' + enc(id))
export const getBox = (id) => get('/boxes/' + enc(id))
export const getAddress = (address, page = 1, rowsPerPage = 20) => get('/addresses/' + enc(address), { page, rowsPerPage })
// unspent boxes, newest first: { items, total }
export const getAddressBoxes = (address, page = 1, rowsPerPage = 20) => get('/addresses/' + enc(address) + '/boxes', { page, rowsPerPage })
// featured tokens, or with q the tokens whose name contains it
export const getTokens = (q) => get('/tokens', q ? { q } : undefined)
export const getToken = (id) => get('/tokens/' + enc(id))
// { items: [{ rank, address, amount }], total }
export const getTokenHolders = (id, page = 1, rowsPerPage = 10) => get('/tokens/' + enc(id) + '/holders', { page, rowsPerPage })
// { items, total, size, fees }
export const getMempool = () => get('/mempool/transactions')
// { items: [{ rank, address, amount, boxCount }], total, synced, indexedHeight }
export const getRichList = (page = 1, rowsPerPage = 50) => get('/richlist', { page, rowsPerPage })
export const getBalanceDistribution = () => get('/richlist/distribution')

// { name, unit, points: [{ t, v }] }; days = most recent N days, 0 = all history. Cached per session.
const chartCache = new Map()
export const getChart = (name, days = 30) => {
  const key = name + ':' + days
  if (!chartCache.has(key))
    chartCache.set(
      key,
      get('/charts/' + name, { days }).catch((e) => {
        chartCache.delete(key)
        throw e
      }),
    )
  return chartCache.get(key)
}

// what a search string is → app route, or null
// "tokens": several tokens carry that name, id is the search text
const SEARCH_ROUTES = { block: '/block/', transaction: '/tx/', address: '/address/', token: '/token/', box: '/box/', tokens: '/tokens?q=' }
export const resolveSearch = async (q) => {
  q = String(q || '').trim()
  if (!q) return null
  const found = await get('/search', { q })
  return found ? SEARCH_ROUTES[found.type] + encodeURIComponent(found.id) : null
}
