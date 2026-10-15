import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'
import { VitePWA } from 'vite-plugin-pwa'

export default defineConfig({
  plugins: [
    vue(),
    tailwindcss(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['favicon.png', 'apple-touch-icon.png', 'mark.png', 'wordmark.png'],
      manifest: {
        name: 'Hyttebok',
        short_name: 'Hyttebok',
        description: 'Booking, lister og hyttebok for hytta',
        lang: 'nb',
        start_url: '/',
        display: 'standalone',
        theme_color: '#ffffff',
        background_color: '#f4f4f5',
        // Lets Android Chrome's getInstalledRelatedApps() tell the install page the app is already installed.
        related_applications: [{ platform: 'webapp', url: '/manifest.webmanifest' }],
        icons: [
          { src: '/pwa-192.png', sizes: '192x192', type: 'image/png' },
          { src: '/pwa-512.png', sizes: '512x512', type: 'image/png' },
          { src: '/pwa-maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
      workbox: {
        // App shell is precached; any route opens offline.
        globPatterns: ['**/*.{js,css,html,png,woff2}'],
        navigateFallback: '/index.html',
        // Login and logout must reach the server, not the cached app shell.
        navigateFallbackDenylist: [/^\/api\//, /^\/oauth\//, /^\/logout/],
        // Push notifications: show them and open the right page on tap.
        importScripts: ['/push-sw.js'],
        runtimeCaching: [
          {
            // Weather icons: fetched the first time they're shown, then kept (they never change).
            urlPattern: ({ url }) => url.pathname.startsWith('/weather/'),
            handler: 'CacheFirst',
            options: { cacheName: 'weather-icons', cacheableResponse: { statuses: [200] } },
          },
          {
            // Photo files never change (new upload = new id), so the cache can answer first.
            urlPattern: ({ url }) => /^\/api\/photos\/\d+\/file$/.test(url.pathname),
            handler: 'CacheFirst',
            options: { cacheName: 'photos', expiration: { maxEntries: 500 }, cacheableResponse: { statuses: [200] } },
          },
          {
            // Data: fresh when online, last known copy when offline or on very slow signal.
            urlPattern: ({ url, request }) => url.pathname.startsWith('/api/') && request.method === 'GET',
            handler: 'NetworkFirst',
            options: { cacheName: 'api', networkTimeoutSeconds: 5, cacheableResponse: { statuses: [200] } },
          },
          {
            urlPattern: ({ url }) => url.origin === 'https://fonts.googleapis.com' || url.origin === 'https://fonts.gstatic.com',
            handler: 'StaleWhileRevalidate',
            options: { cacheName: 'fonts', cacheableResponse: { statuses: [0, 200] } },
          },
        ],
      },
    }),
  ],
  server: { proxy: { '/api': 'http://localhost:8080', '/oauth': 'http://localhost:8080', '/login': 'http://localhost:8080', '/logout': 'http://localhost:8080' } },
})
