// QR code and the page-state wrappers (loading / error / not found).
import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import QRCode from 'qrcode'
import { Loading } from './ui.jsx'

export function QrCode({ text }) {
  const [svg, setSvg] = useState('')
  useEffect(() => {
    let alive = true
    QRCode.toString(text, { type: 'svg', margin: 0, color: { dark: '#111111', light: '#ffffff' } })
      .then((s) => {
        if (alive) setSvg(s)
      })
      .catch(() => {})
    return () => {
      alive = false
    }
  }, [text])
  // the SVG is generated locally by the qrcode library from the address, not taken from the API
  return <div className="size-24 flex-none rounded-lg border border-line bg-white p-1.5 [&_svg]:block [&_svg]:size-full" title="Address QR code" dangerouslySetInnerHTML={{ __html: svg }} />
}

/*
 * Page-level states for a useApi() result: spinner on first load, error with retry, "not found" on a 404,
 * otherwise children(data).
 */
export function Loaded({ q, kind, id, children }) {
  if (q.data === undefined && !q.error)
    return (
      <main className="wrap">
        <Loading className="py-20" />
      </main>
    )
  if (q.data === undefined) return <ErrorBox error={q.error} onRetry={q.reload} />
  if (q.data === null) return <Missing title={`No ${kind} found`} text={`Nothing on mainnet matches this ${kind} identifier. Check for typos or try searching again.`} detail={id} />
  return children(q.data)
}

export const ErrorBox = ({ error, onRetry }) => (
  <main className="wrap">
    <div className="errbox">
      <h1>Something went wrong</h1>
      <div className="text-muted">The explorer backend did not answer. It may be restarting or out of sync with the node.</div>
      <div className="break-all font-mono text-sm text-faint">{String((error && error.message) || error)}</div>
      {onRetry && (
        <button className="btn" onClick={onRetry}>
          Try again
        </button>
      )}
    </div>
  </main>
)

export const Missing = ({ title, text, detail }) => (
  <main className="wrap">
    <div className="errbox">
      <h1>{title}</h1>
      <div className="text-muted">{text}</div>
      {detail && <div className="break-all font-mono text-sm text-faint">{detail}</div>}
      <Link className="btn" to="/">
        Back to home
      </Link>
    </div>
  </main>
)
