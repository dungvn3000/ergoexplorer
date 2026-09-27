// Privacy: what the explorer does and does not record (carried over from the v1 frontend, corrected for this frontend:
// the theme choice is kept in localStorage).
import { Link } from 'react-router'
import { Panel, PanelHead, Crumbs, PageHead, Sub, ExternalLink } from '../components/ui.jsx'
import { CircleCheck, Info } from 'lucide-react'

const SECTIONS = [
  {
    title: 'What we do not collect',
    points: [
      {
        ok: true,
        text: 'No IP addresses, user agents or request logs',
        detail: 'The web server (Caddy) runs with access logging disabled; the API process logs only its own errors and indexer progress, never requests.',
      },
      {
        ok: true,
        text: 'No cookies, no fingerprinting',
        detail: 'The only thing kept in your browser is the light / dark theme, and only once you pick one: a single localStorage entry "ergo-theme". Clearing site data removes it.',
      },
      { ok: true, text: 'No analytics or advertising', detail: 'No Google Analytics, Matomo, Plausible, pixels or ad networks. Nothing reports page views anywhere.' },
      { ok: true, text: 'No third-party requests', detail: 'Fonts, icons, scripts and styles are served from explorer.erg.vn itself. Your browser talks to no other domain.' },
      { ok: true, text: 'No accounts, no sign-in, no e-mail', detail: 'Every page is public; there is nothing to register for.' },
    ],
  },
  {
    title: 'How a page view works',
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

export function Privacy() {
  return (
    <main className="wrap">
      <Crumbs>Privacy</Crumbs>
      <PageHead title={<h1>Privacy</h1>} sub={<Sub>Last updated 26 September 2026 · applies to explorer.erg.vn and its API</Sub>} />

      <Panel className="mb-5">
        <PanelHead title="The short version" />
        <p className="panel-b">
          Ergo Explorer does not track you. There are no accounts, no cookies, no analytics, no advertising and <strong>no server-side logging of who requested what</strong>. The site shows public
          Ergo blockchain data and nothing about its visitors is recorded.
        </p>
      </Panel>

      <div className="grid-2">
        {SECTIONS.map((section) => (
          <Panel key={section.title}>
            <PanelHead title={section.title} />
            <ul className="divide-y divide-line">
              {section.points.map((p) => (
                <li key={p.text} className="flex gap-3 px-4 py-3">
                  {p.ok ? <CircleCheck size={16} className="mt-0.5 flex-none text-good" /> : <Info size={16} className="mt-0.5 flex-none text-faint" />}
                  <div className="min-w-0">
                    <div className="font-medium">{p.text}</div>
                    <div className="mt-0.5 text-sm text-muted">{p.detail}</div>
                  </div>
                </li>
              ))}
            </ul>
          </Panel>
        ))}
      </div>

      <Panel>
        <PanelHead title="Questions" />
        <p className="panel-b">
          Ergo Explorer is run by <ExternalLink href="https://erg.vn">Ergo Vietnam</ExternalLink>. The source of both the frontend and the indexer is described on the <Link to="/about">About</Link>{' '}
          page; if you believe a pool or contract label is wrong or want one removed, contact us there. Because the data is the public Ergo blockchain, the explorer cannot delete transactions or
          addresses — it only displays them.
        </p>
      </Panel>
    </main>
  )
}
