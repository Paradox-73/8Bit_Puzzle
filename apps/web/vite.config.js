import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      // We provide our own icon (logo.png); do not auto-generate.
      includeAssets: ['logo.png'],
      manifest: {
        name: '8Bit Daily Puzzle',
        short_name: '8Bit',
        description: 'A retro Wordle-style daily college puzzle.',
        theme_color: '#0f0f14',
        background_color: '#0f0f14',
        display: 'standalone',
        orientation: 'portrait',
        start_url: '/',
        scope: '/',
        icons: [
          { src: '/logo.png', sizes: '192x192', type: 'image/png', purpose: 'any' },
          { src: '/logo.png', sizes: '512x512', type: 'image/png', purpose: 'any' },
          { src: '/logo.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
      workbox: {
        globPatterns: ['**/*.{js,css,html,svg,png,ico,woff2}'],
        navigateFallback: '/index.html',
        // Don't let the SW intercept API calls under /auth, /admin etc. as navigations.
        navigateFallbackDenylist: [/^\/(auth|puzzles|leaderboard|me|users|push|admin)\//],
        runtimeCaching: [
          {
            // Cache today's puzzle so it can be played offline (stale-while-revalidate).
            urlPattern: ({ url }) =>
              url.pathname === '/puzzles/today' || url.pathname.endsWith('/puzzles/today'),
            handler: 'StaleWhileRevalidate',
            method: 'GET',
            options: {
              cacheName: 'puzzle-today',
              expiration: {
                maxEntries: 8,
                maxAgeSeconds: 60 * 60 * 24, // 1 day
              },
              cacheableResponse: { statuses: [0, 200] },
            },
          },
          {
            // Generic GET fallback for read endpoints (network-first).
            urlPattern: ({ url, request }) =>
              request.method === 'GET' &&
              /\/(leaderboard|me|users)/.test(url.pathname),
            handler: 'NetworkFirst',
            options: {
              cacheName: 'api-read',
              networkTimeoutSeconds: 5,
              expiration: { maxEntries: 50, maxAgeSeconds: 60 * 60 },
              cacheableResponse: { statuses: [0, 200] },
            },
          },
        ],
      },
      devOptions: {
        enabled: false,
      },
    }),
  ],
  server: {
    host: true,
    port: 5173,
    // Allow access via LAN IP and tunnel hostnames (e.g. *.trycloudflare.com) without
    // Vite's host-check blocking the request.
    allowedHosts: true,
  },
  preview: {
    host: true,
    port: 4173,
    allowedHosts: true,
  },
});
