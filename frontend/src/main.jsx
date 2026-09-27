// fonts are self-hosted (bundled from @fontsource), so the site makes no third-party requests
import '@fontsource/ibm-plex-sans/400.css'
import '@fontsource/ibm-plex-sans/500.css'
import '@fontsource/ibm-plex-sans/600.css'
import '@fontsource/ibm-plex-sans/700.css'
import '@fontsource/ibm-plex-mono/400.css'
import '@fontsource/ibm-plex-mono/500.css'
import '@fontsource/ibm-plex-mono/600.css'
import './styles.css'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { createBrowserRouter, RouterProvider } from 'react-router'
import { AppProvider } from './context.jsx'
import { Layout } from './components/Layout.jsx'
import { Home } from './pages/Home.jsx'
import { Blocks, Block } from './pages/Blocks.jsx'
import { Tx } from './pages/Tx.jsx'
import { Box } from './pages/Box.jsx'
import { Address } from './pages/Address.jsx'
import { Tokens, Token } from './pages/Tokens.jsx'
import { RichList } from './pages/RichList.jsx'
import { Mempool } from './pages/Mempool.jsx'
import { Charts } from './pages/Charts.jsx'
import { Api, SearchMiss, NotFound } from './pages/Misc.jsx'
import { About } from './pages/About.jsx'
import { Privacy } from './pages/Privacy.jsx'

// history routes (/block/…); the server answers unknown paths with index.html (Caddy: try_files {path} /index.html)
const router = createBrowserRouter([
  {
    element: <Layout />,
    children: [
      { path: '/', element: <Home /> },
      { path: '/blocks', element: <Blocks /> },
      { path: '/block/:id', element: <Block /> },
      { path: '/tx/:id', element: <Tx /> },
      { path: '/box/:id', element: <Box /> },
      { path: '/address/:address', element: <Address /> },
      { path: '/tokens', element: <Tokens /> },
      { path: '/token/:id', element: <Token /> },
      { path: '/richlist', element: <RichList /> },
      { path: '/mempool', element: <Mempool /> },
      { path: '/charts/:chart?', element: <Charts /> },
      { path: '/api', element: <Api /> },
      { path: '/about', element: <About /> },
      { path: '/privacy', element: <Privacy /> },
      { path: '/search/:q', element: <SearchMiss /> },
      { path: '*', element: <NotFound /> },
    ],
  },
])

createRoot(document.getElementById('app')).render(
  <StrictMode>
    <AppProvider>
      <RouterProvider router={router} />
    </AppProvider>
  </StrictMode>,
)
