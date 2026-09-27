// Chart catalogue and series loading.
import { getChart } from './api.js'
import { fmtInt } from './format.js'

/*
 * Every series the backend offers (GET /charts/{name}); descriptions as in the v1 frontend.
 * scale: API value → displayed unit · dp / int: number format · hourly: hourly buckets instead of UTC days
 * featured: shown as a card at the top of the Charts page (the rest are picked from the metric menu)
 */
export const CHARTS = [
  // network
  { key: 'hashrate', group: 'Network', title: 'Hashrate', unit: 'TH/s', dp: 2, featured: true, desc: 'Average difficulty / 120 s, per day.' },
  { key: 'difficulty', group: 'Network', title: 'Difficulty', unit: 'P', dp: 3, scale: 1e-15, featured: true, desc: 'Average block difficulty per day, in peta.' },
  { key: 'blocks', group: 'Network', title: 'Blocks per day', unit: 'blocks', int: true, desc: 'Blocks mined per UTC day.' },
  { key: 'blockTime', group: 'Network', title: 'Block time', unit: 's', dp: 1, featured: true, desc: 'Average seconds between blocks. The target is 120 s.' },
  { key: 'blockSize', group: 'Network', title: 'Block size', unit: 'KB', dp: 1, scale: 1 / 1024, featured: true, desc: 'Average block size per day.' },
  // activity
  { key: 'transactions', group: 'Activity', title: 'Transactions', unit: 'tx/day', int: true, featured: true, desc: 'Transactions per day, including the emission transaction of each block.' },
  { key: 'activeAddresses', group: 'Activity', title: 'Active addresses', unit: '/day', int: true, featured: true, desc: 'Addresses that received or spent a box that day.' },
  { key: 'fundedAddresses', group: 'Activity', title: 'Addresses with balance', unit: 'addresses', int: true, desc: 'Addresses holding ERG at the end of the day.' },
  { key: 'tokenTransfers', group: 'Activity', title: 'Token transfers', unit: '/day', int: true, featured: true, desc: 'Boxes carrying tokens, per day.' },
  { key: 'tokensMinted', group: 'Activity', title: 'Tokens minted', unit: '/day', int: true, desc: 'New EIP-4 tokens per day.' },
  // fees and supply
  { key: 'fees', group: 'Fees & supply', title: 'Fees', unit: 'ERG/day', dp: 2, featured: true, desc: 'Total transaction fees paid to miners per day.' },
  { key: 'avgFee', group: 'Fees & supply', title: 'Average fee', unit: 'ERG', dp: 4, desc: 'Fees divided by non-reward transactions.' },
  { key: 'emission', group: 'Fees & supply', title: 'Miner reward per day', unit: 'ERG/day', int: true, desc: 'ERG kept by miners (emission − re-emission part).' },
  { key: 'circulatingSupply', group: 'Fees & supply', title: 'Circulating supply', unit: 'M ERG', dp: 3, scale: 1e-6, desc: 'Issued − earmarked for re-emission (EIP-27), end of day.' },
  // UTXO set
  { key: 'boxesCreated', group: 'UTXO set', title: 'Boxes created', unit: '/day', int: true, desc: 'Transaction outputs per day.' },
  { key: 'boxesSpent', group: 'UTXO set', title: 'Boxes spent', unit: '/day', int: true, desc: 'Transaction inputs per day.' },
  { key: 'utxoSize', group: 'UTXO set', title: 'UTXO set size', unit: 'boxes', int: true, desc: 'Unspent boxes at the end of the day.' },
  // mempool (hourly)
  { key: 'mempoolTxs', group: 'Mempool', title: 'Mempool size', unit: 'tx', dp: 1, hourly: true, desc: 'Unconfirmed transactions, hourly average.' },
  { key: 'mempoolBytes', group: 'Mempool', title: 'Mempool bytes', unit: 'KB', dp: 1, scale: 1 / 1024, hourly: true, desc: 'Bytes waiting, hourly average.' },
]

export const FEATURED = CHARTS.filter((c) => c.featured)

// [group, metrics] in catalogue order, for the metric menu
export const GROUPS = [...new Set(CHARTS.map((c) => c.group))].map((g) => [g, CHARTS.filter((c) => c.group === g)])

export const RANGES = [
  ['7d', 7],
  ['30d', 30],
  ['90d', 90],
  ['1y', 365],
  ['All', 0],
]

export const fmtValue = (meta, v) => (meta.int ? fmtInt(Math.round(v)) : v.toFixed(meta.dp))

// point time as text: UTC day for daily series, UTC hour for hourly ones
export const pointLabel = (meta, t) => (meta.hourly ? new Date(t).toISOString().slice(0, 16).replace('T', ' ') : new Date(t).toISOString().slice(0, 10))

// the last point is the bucket the index is still working through (today / this hour when synced, the index tip
// day when it lags behind the node): its totals are partial and its averages skewed, so it is left out
export const series = (meta, days) => getChart(meta.key, days).then((c) => (c ? c.points.slice(0, -1).map((p) => ({ t: p.t, v: meta.scale ? p.v * meta.scale : p.v })) : []))
