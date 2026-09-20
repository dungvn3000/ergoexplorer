// Explorer service: calls the backend (backend/ in this repo, Jooby) through Api.js.
// The backend answers { data } or { errorCode, error }; 404 => null.
import { api } from './Api'

const get = async (url, params) => {
  try {
    const response = await api.get(url, { params })
    return response.data.data
  } catch (error) {
    if (error.response && error.response.status === 404) {
      return null
    }
    throw error
  }
}

export const getInfo = () => get('/info')

export const getNetworkStats = () => get('/networkState')

export const getLatestBlocks = (limit = 10) => get('/blocks/latest', { limit })

export const getLatestTransactions = (limit = 10) => get('/transactions/latest', { limit })

// { items, total } — total is the chain height
export const getBlocks = ({ page = 1, rowsPerPage = 25 } = {}) => get('/blocks', { page, rowsPerPage })

// by height or id
export const getBlock = (idOrHeight) => get('/blocks/' + idOrHeight)

export const getTransaction = (id) => get('/transactions/' + id)

export const getAddress = (address, { page = 1, rowsPerPage = 20 } = {}) => get('/addresses/' + address, { page, rowsPerPage })

// Unspent boxes of an address, newest first: { items: [box], total }
export const getAddressBoxes = (address, { page = 1, rowsPerPage = 20 } = {}) => get('/addresses/' + address + '/boxes', { page, rowsPerPage })

export const getTokens = () => get('/tokens')

export const getToken = (id) => get('/tokens/' + id)

// { items, total, size, fees }
export const getMempool = () => get('/mempool/transactions')

// Rich list of a token: { items: [{ rank, address, amount }], total }
export const getTokenHolders = (id, { page = 1, rowsPerPage = 50 } = {}) => get('/tokens/' + id + '/holders', { page, rowsPerPage })

// Chart series: { name, unit, points: [{ t, v }] } — days = most recent days, 0 = all
export const getChart = (name, days = 30) => get('/charts/' + name, { days })

// Rich list ERG: { items: [{ rank, address, amount, boxCount }], total, synced, indexedHeight }
export const getRichList = ({ page = 1, rowsPerPage = 50 } = {}) => get('/richlist', { page, rowsPerPage })

export const getBox = (id) => get('/boxes/' + id)

// Raw JSON of an API path (e.g. /blocks/{id}/raw — the block exactly as the node serves it)
export const getRaw = async (path) => {
  try {
    const response = await api.get(path)
    return response.data
  } catch (error) {
    if (error.response && error.response.status === 404) return null
    throw error
  }
}

// Absolute URL of an API path, to open in a new tab
export const apiUrl = (path) => api.defaults.baseURL.replace(/\/$/, '') + path

// Labels for protocol addresses the backend does not label itself (it labels pools in block.miner / address.label)
const LABELS = {
  '2iHkR7CWvD1R4j1yZg5bkeDRQavjAaVPeTDFGGLZduHyfWMuYpmhHocX8GJoaieTx78FntzJbCBVL6rf96ocJoZdmWBL2fci7NqWgAirppPQmZ7fN9V6z13Ay6brPriBKYqLp1bT2Fk4FkFLCfdPpe': {
    name: 'Mining fee contract',
    contract: true,
  },
  '2Z4YBkDsDvQj8BX7xiySFewjitqp2ge9c99jfes2whbtKitZTxdBYqbrVZUvZvKv6aqn9by4kp3LE1c26LCyosFnVnm6b6U1JYvWpYmL2ZnixJbXLjWAWuBThV1D6dLpqZJYQHYDznJCk49g5TUiS4q8khpag2aNmHwREV7JSsypHdHLgJT7MGaw51aJfNubyzSKxZ4AJXFS27EfXwyCLzW1K6GVqwkJtCoPvrcLqmqwacAWJPkmh78nke9H4oT88XmSbRt2n9aWZjosiZCafZ4osUDxmZcc5QVEeTWn8drSraY3eFKe8Mu9MSCcVU':
    { name: 'Emission contract', contract: true },
  '6KxusedL87PBibr1t1f4ggzAyTAmWEPqSpqXbkdoybNwHVw5Nb7cUESBmQw5XK8TyvbQiueyqkR9XMNaUgpWx3jT54p': { name: 'Pay-to-reemission (EIP-27)', contract: true },
  '22WkKcVUvboYCZJe1urbmvBL3j67LKb5KEAvFhJXqA6ubYvHpSCvbvwvEY3xzUr7QvxpEtqjzMAPMsVdZh1VGWmZphvKoJdVzL1ayhsMftTtEFoA3YYdq3zKeeYXavVrrPUmK3fRXJ2HWEbZexewtBWcgAnHBw5tKvYFy9dEUi645gE2fYMUvVBtbvMExE9mjZ2W9goWkqu1VtThAsMZWZWjHxDjX116HpeQKu9b9neEUBj4kE5sX8QXaV6ZeReXxYHFJFg2rmaTknSPMxHXA8NpQKgzryBwLssp5EJ1QTqn5R6xuvGgFCEUZicCEo8qk8UNbE7e2d4WqW5qzpQPzJkKoPa5UtJEPYDWNhaCKmCpzdSc77':
    { name: 'Re-emission contract (EIP-27)', contract: true },
}
export const getAddressLabel = (address) => LABELS[address] || null

// Token avatar color: stable per id (the backend has no colors)
export const tokenColor = (tokenId) => {
  let h = 0
  for (let i = 0; i < String(tokenId).length; i++) h = (h * 31 + tokenId.charCodeAt(i)) >>> 0
  return `hsl(${h % 360} 45% 42%)`
}

// Ask the backend what a search string is; returns a route path or null
export const resolveSearch = async (q) => {
  q = String(q || '').trim()
  if (!q) return null
  const found = await get('/search', { q })
  if (!found) return null
  const routes = { block: '/block/', transaction: '/tx/', address: '/address/', token: '/token/', box: '/box/' }
  return routes[found.type] + found.id
}
