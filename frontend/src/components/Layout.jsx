// Page chrome: header (nav, search, theme toggle), footer and the copy toast.
import { useEffect, useState } from 'react'
import { Link, Outlet, ScrollRestoration, useLocation, useNavigate } from 'react-router'
import { resolveSearch } from '../api.js'
import { useTheme, useHeight, useToast } from '../app-context.js'
import { fmtInt } from '../format.js'
import { Menu, Moon, Search, Sun, X } from 'lucide-react'
import { Logo } from './icons.jsx'
import { Spinner } from './ui.jsx'

// nav item → path prefixes it is active for
const NAV = [
  ['Blocks', '/blocks', ['/blocks', '/block/']],
  ['Mempool', '/mempool', ['/mempool']],
  ['Tokens', '/tokens', ['/tokens', '/token/']],
  ['Rich list', '/richlist', ['/richlist']],
  ['Charts', '/charts', ['/charts']],
  ['API & MCP', '/api', ['/api']],
  ['About', '/about', ['/about']],
]

// search box: asks the backend what the string is and opens that page
// variant: 'header' (dark, in the top bar on wide screens) · 'menu' (dark, full width in the mobile menu) · 'hero' (home page)
const SEARCH_FORM = {
  header: 'relative ml-auto flex min-w-0 max-w-[460px] flex-1 items-center max-[820px]:hidden',
  menu: 'relative flex w-full items-center',
  hero: 'relative flex w-full max-w-[760px] items-center',
}
const SEARCH_INPUT_DARK = 'h-10 w-full rounded-lg border border-nav-line bg-nav-2 pl-9.5 pr-3 text-[13.5px] text-nav-ink placeholder:text-nav-muted focus:border-accent focus:outline-none'
const SEARCH_INPUT = {
  header: SEARCH_INPUT_DARK,
  menu: SEARCH_INPUT_DARK,
  hero: 'h-13 w-full rounded-lg border border-line-strong bg-surface pl-11.5 pr-10 text-lg text-ink shadow-card placeholder:text-faint focus:border-accent focus:outline-none focus:ring-3 focus:ring-accent/35',
}

export function SearchForm({ variant = 'header' }) {
  const navigate = useNavigate()
  const [q, setQ] = useState('')
  const [busy, setBusy] = useState(false)
  const hero = variant === 'hero'

  async function submit(e) {
    e.preventDefault()
    const query = q.trim()
    if (!query || busy) return
    setBusy(true)
    let route = null
    try {
      route = await resolveSearch(query)
    } catch {
      // backend unreachable: fall through to the "no match" page
    }
    setBusy(false)
    if (route) setQ('')
    navigate(route || '/search/' + encodeURIComponent(query))
  }

  return (
    <form onSubmit={submit} role="search" className={SEARCH_FORM[variant]}>
      <Search size={16} className={`pointer-events-none absolute ${hero ? 'left-4 text-faint' : 'left-3 text-nav-muted'}`} />
      <input
        value={q}
        onChange={(e) => setQ(e.target.value)}
        type="search"
        enterKeyHint="search"
        autoComplete="off"
        spellCheck={false}
        aria-label="Search"
        placeholder={hero ? 'Address, transaction, block, box, token id or name, height' : 'Address, tx, block, token name or height'}
        className={SEARCH_INPUT[variant]}
      />
      {busy && <Spinner className={`absolute ${hero ? 'right-4' : 'right-3'}`} />}
    </form>
  )
}

// menu links; `className` sets the look (inline bar on wide screens, two-column grid in the mobile menu)
function NavLinks({ pathname, className }) {
  return NAV.map(([label, to, prefixes]) => (
    <Link key={to} to={to} aria-current={prefixes.some((p) => pathname.startsWith(p)) ? 'page' : undefined} className={className}>
      {label}
    </Link>
  ))
}

const NET_PILL = 'inline-flex items-center gap-1.75 whitespace-nowrap rounded-full border border-nav-line px-2.5 py-1.25 text-sm font-medium text-nav-muted'
const HEADER_BUTTON =
  'grid size-8.5 flex-none cursor-pointer place-items-center rounded-lg border border-nav-line text-nav-muted hover:bg-nav-2 hover:text-nav-ink focus-visible:border-accent focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-accent-soft'

const NetPill = ({ className = '' }) => (
  <div className={`${NET_PILL} ${className}`}>
    <i className="size-1.75 rounded-full bg-good ring-3 ring-good/30" />
    Mainnet
  </div>
)

function Header() {
  const location = useLocation()
  const { pathname } = location
  const { toggleTheme } = useTheme()
  const home = pathname === '/'
  // the mobile menu is open for one location only: any navigation (link, search, back button) closes it
  const [openAt, setOpenAt] = useState(null)
  const open = openAt === location.key
  const close = () => setOpenAt(null)

  useEffect(() => {
    if (!open) return
    const onKey = (e) => {
      if (e.key === 'Escape') setOpenAt(null)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open])

  return (
    <header className="sticky top-0 z-20 border-b border-nav-line bg-nav">
      <div className="mx-auto flex h-14 max-w-[1240px] items-center gap-3 px-4 sm:gap-5 sm:px-5">
        <Link to="/" className="flex items-center gap-2.5 whitespace-nowrap text-lg font-semibold tracking-[-.01em] text-nav-ink hover:no-underline">
          <Logo />
          Ergo Explorer
        </Link>
        {/* wide screens: the whole menu inline */}
        <nav aria-label="Main" className="ml-1 hidden gap-0.5 min-[1200px]:flex">
          <NavLinks
            pathname={pathname}
            className="whitespace-nowrap rounded-md px-2.5 py-1.5 font-medium text-nav-muted hover:bg-nav-2 hover:text-nav-ink hover:no-underline aria-[current=page]:bg-nav-2 aria-[current=page]:text-nav-ink"
          />
        </nav>
        {!home && <SearchForm />}
        <NetPill className={`${home ? 'ml-auto ' : ''}max-sm:hidden`} />
        <button onClick={toggleTheme} title="Toggle light / dark theme" aria-label="Toggle light / dark theme" className={`${HEADER_BUTTON} max-sm:ml-auto`}>
          <Moon size={16} className="block dark:hidden" />
          <Sun size={16} className="hidden dark:block" />
        </button>
        {/* narrow screens: menu behind a button */}
        <button
          onClick={() => setOpenAt(open ? null : location.key)}
          aria-expanded={open}
          aria-controls="mobile-menu"
          aria-label={open ? 'Close menu' : 'Open menu'}
          className={`${HEADER_BUTTON} min-[1200px]:hidden`}
        >
          {open ? <X size={16} /> : <Menu size={16} />}
        </button>
      </div>

      {open && (
        <>
          <div className="fixed inset-0 top-14 z-10 bg-black/40 min-[1200px]:hidden" onClick={close} aria-hidden="true" />
          <div id="mobile-menu" className="absolute inset-x-0 top-full z-20 border-b border-nav-line bg-nav px-4 pb-4 pt-3 shadow-card sm:px-5 min-[1200px]:hidden">
            <div className="mx-auto flex max-w-[1240px] flex-col gap-3">
              {/* the top bar has no search box below 820px (and none on the home page, which has its own) */}
              {!home && (
                <div className="min-[820px]:hidden">
                  <SearchForm variant="menu" />
                </div>
              )}
              <nav aria-label="Main" className="grid grid-cols-2 gap-1 sm:grid-cols-4">
                <NavLinks
                  pathname={pathname}
                  className="rounded-lg px-3 py-2.5 font-medium text-nav-muted hover:bg-nav-2 hover:text-nav-ink hover:no-underline aria-[current=page]:bg-nav-2 aria-[current=page]:text-nav-ink"
                />
              </nav>
              <NetPill className="self-start sm:hidden" />
            </div>
          </div>
        </>
      )}
    </header>
  )
}

function Footer() {
  const height = useHeight()
  return (
    <footer className="mx-auto flex max-w-[1240px] flex-wrap justify-between gap-2.5 border-t border-line px-5 py-6 text-sm text-muted [&_a]:text-muted">
      <div>Ergo Explorer · indexed from Ergo full nodes by Ergo Vietnam · Made by an Ergonaut</div>
      <div>
        {height && <>Height {fmtInt(height)} · </>}
        <Link to="/about">About</Link> · <Link to="/privacy">Privacy</Link> · <Link to="/api">API &amp; MCP</Link>
      </div>
    </footer>
  )
}

function Toast() {
  const toast = useToast()
  return (
    <div
      role="status"
      className={`pointer-events-none fixed bottom-6 left-1/2 z-50 -translate-x-1/2 rounded-lg bg-ink px-3.5 py-2 text-base text-bg transition duration-200 ${toast.on ? 'translate-y-0 opacity-100' : 'translate-y-2.5 opacity-0'}`}
    >
      {toast.text}
    </div>
  )
}

export function Layout() {
  return (
    <>
      <Header />
      <Outlet />
      <Footer />
      <Toast />
      {/* restore by pathname: query-only changes (paging, chart range) keep the scroll position */}
      <ScrollRestoration getKey={(location) => location.pathname} />
    </>
  )
}
