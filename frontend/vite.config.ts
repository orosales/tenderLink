import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// The dev server proxies /api to the Spring Boot backend, so no CORS setup is needed in development.
// Override the target with VITE_API_PROXY_TARGET if the backend runs elsewhere.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.VITE_API_PROXY_TARGET ?? 'http://localhost:8090',
        // Proxied calls are same-origin for the browser. Drop Origin so opening the dev server as
        // 127.0.0.1 or a LAN address is not rejected by the backend's CORS allow-list (403 "Invalid CORS request").
        configure: proxy => proxy.on('proxyReq', req => req.removeHeader('origin')),
      },
    },
  },
})
