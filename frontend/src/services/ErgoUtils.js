// Shared formatting helpers for the explorer
import { format, formatDistanceToNowStrict } from 'date-fns'

const NANO = 1e9

// nanoERG -> "12.3456" (no unit suffix)
export const formatErg = (nano, maxDecimals = 9) => {
  return (nano / NANO).toLocaleString('en-US', { minimumFractionDigits: 0, maximumFractionDigits: maxDecimals })
}

export const formatTokenAmount = (amount, decimals = 0) => {
  return (amount / Math.pow(10, decimals)).toLocaleString('en-US', { maximumFractionDigits: decimals })
}

export const formatInt = (n) => Number(n).toLocaleString('en-US')

// Difficulty shown in peta (P)
export const formatDifficulty = (d) => (d / 1e15).toFixed(3) + ' P'

export const shortHash = (h, start = 8, end = 6) => {
  h = String(h || '')
  return h.length <= start + end + 1 ? h : h.slice(0, start) + '…' + h.slice(-end)
}

// timestamp (ms) -> "dd-MM-yyyy HH:mm:ss"
export const formatTimestamp = (ms) => format(new Date(ms), 'dd-MM-yyyy HH:mm:ss')

// timestamp (ms) -> "4 minutes ago"
export const timeAgo = (ms) => formatDistanceToNowStrict(new Date(ms), { addSuffix: true })

export const formatSeconds = (sec) => {
  const m = Math.floor(sec / 60)
  const s = Math.round(sec % 60)
  return `${m}m ${String(s).padStart(2, '0')}s`
}
