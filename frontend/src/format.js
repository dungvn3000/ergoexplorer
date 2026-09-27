// Shared formatting. Amounts from the API are nanoERG; token amounts are raw integers.
export const NANO = 1e9

export const fmtInt = (n) => Number(n).toLocaleString('en-US')

// nanoERG → "12.3456" (no unit)
export const erg = (nano, maxDec = 9) => (Number(nano) / NANO).toLocaleString('en-US', { minimumFractionDigits: 0, maximumFractionDigits: maxDec })

// decimals (R6) is user-written on-chain data: clamp to what toLocaleString accepts
export const clampDecimals = (d) => Math.min(20, Math.max(0, Number.isFinite(Number(d)) ? Math.trunc(Number(d)) : 0))
export const tokAmt = (amount, decimals) => {
  const d = clampDecimals(decimals)
  return (Number(amount) / Math.pow(10, d)).toLocaleString('en-US', { maximumFractionDigits: d })
}

export const bytes = (n) => (n < 1024 ? n + ' B' : n < 1048576 ? (n / 1024).toFixed(1) + ' KB' : n < 1073741824 ? (n / 1048576).toFixed(2) + ' MB' : (n / 1073741824).toFixed(2) + ' GB')

export const short = (h, a = 8, b = 6) => {
  h = String(h || '')
  return h.length <= a + b + 1 ? h : h.slice(0, a) + '…' + h.slice(-b)
}

export const ago = (ts) => {
  const s = Math.max(0, Math.round((Date.now() - ts) / 1000))
  if (s < 60) return s + 's ago'
  const mi = (s / 60) | 0
  if (mi < 60) return mi + 'm ago'
  const h = (mi / 60) | 0
  if (h < 48) return h + 'h ' + (mi % 60) + 'm ago'
  return ((h / 24) | 0) + 'd ago'
}

const pad = (n) => String(n).padStart(2, '0')
export const fmtDate = (ts) => {
  const d = new Date(ts)
  return `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())} ${pad(d.getUTCHours())}:${pad(d.getUTCMinutes())}:${pad(d.getUTCSeconds())} UTC`
}
export const isoDay = (ts) => new Date(ts).toISOString().slice(0, 10)

// difficulty in peta
export const fmtPeta = (d) => (Number(d) / 1e15).toFixed(3) + ' P'

export const fmtSeconds = (sec) => {
  const t = Math.round(sec)
  return `${Math.floor(t / 60)}m ${pad(t % 60)}s`
}

export const fmtCompact = (v) =>
  Math.abs(v) >= 1e9 ? +(v / 1e9).toFixed(1) + 'B' : Math.abs(v) >= 1e6 ? +(v / 1e6).toFixed(1) + 'M' : Math.abs(v) >= 1e3 ? +(v / 1e3).toFixed(1) + 'k' : String(+Number(v).toFixed(2))

export const sum = (list, f) => list.reduce((s, x) => s + Number(f(x)), 0)

// nanoERG held by a list of boxes
export const totalValue = (boxes) => sum(boxes, (o) => o.value)

// token avatar colour: stable per id (the backend has no colours)
export const tokenColor = (tokenId) => {
  let h = 0
  const s = String(tokenId)
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) >>> 0
  return `hsl(${h % 360} 45% 42%)`
}
