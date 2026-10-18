import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';

export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['aph-mark.svg'],
      manifest: {
        name: 'APH • AbdulmajeedPharma',
        short_name: 'APH',
        description: 'Bilingual, offline-first pharmacology and medicine library.',
        theme_color: '#0b5c63',
        background_color: '#f4f7f6',
        display: 'standalone',
        orientation: 'portrait-primary',
        start_url: '/',
        lang: 'en',
        dir: 'ltr',
        icons: [
          { src: '/pwa-192x192.png', sizes: '192x192', type: 'image/png' },
          { src: '/pwa-512x512.png', sizes: '512x512', type: 'image/png' },
          { src: '/pwa-512x512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' }
        ]
      },
      workbox: {
        globPatterns: ['**/*.{js,css,html,svg,png,woff2}'],
        runtimeCaching: [
          {
            urlPattern: /^https:\/\/(rxnav\.nlm\.nih\.gov|api\.fda\.gov|pubchem\.ncbi\.nlm\.nih\.gov)\//,
            handler: 'NetworkFirst',
            options: {
              cacheName: 'aph-trusted-sources',
              networkTimeoutSeconds: 8,
              expiration: { maxEntries: 160, maxAgeSeconds: 60 * 60 * 24 * 30 }
            }
          }
        ]
      }
    })
  ],
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('@e965/xlsx')) return 'xlsx';
          if (id.includes('react') || id.includes('scheduler')) return 'react-vendor';
          if (id.includes('dexie') || id.includes('fuse.js') || id.includes('papaparse')) return 'data-vendor';
          if (id.includes('lucide-react')) return 'icons';
        },
      },
    },
  },
  server: {
    host: '0.0.0.0',
    port: 4173,
    allowedHosts: true
  }
});
