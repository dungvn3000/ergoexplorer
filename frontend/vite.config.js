import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// Dev: the Jooby backend runs on :8080; proxying /api keeps the app on one origin (no CORS).
// Prod: the same host serves the app and reverse-proxies /api to the backend (see backend/deploy/Caddyfile).
export default defineConfig({
  plugins: [react(), tailwindcss()],
  preview: { port: 4176, proxy: { '/api/v1': 'http://localhost:8080' } },
  server: {
    port: 5176,
    // only the REST API: the app has its own /api page
    proxy: { '/api/v1': 'http://localhost:8080' },
  },
})
