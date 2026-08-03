import { defineConfig, type Plugin } from 'vite';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';
import fs from 'node:fs';

/// <reference types="vitest" />

/**
 * Android build mode (`vite build --mode android`) resolves any local
 * import that has a sibling `<name>.android.tsx`/`.android.ts` file to that
 * sibling instead of the default file. This is the platform-presentation
 * convention documented in CLAUDE.md: shared hooks/logic stay in the
 * default file, only the Android-specific JSX lives in `.android.tsx`.
 * A no-op in every other mode, so the PWA build path is untouched.
 */
function androidPlatformResolve(mode: string): Plugin | false {
  if (mode !== 'android') return false;
  return {
    name: 'android-platform-resolve',
    enforce: 'pre',
    async resolveId(source, importer, options) {
      if (!importer) return null;
      const resolved = await this.resolve(source, importer, { ...options, skipSelf: true });
      if (!resolved || resolved.external) return null;
      if (!/\.tsx?$/.test(resolved.id) || /\.android\.tsx?$/.test(resolved.id)) return null;
      const androidId = resolved.id.replace(/\.(tsx|ts)$/, '.android.$1');
      return fs.existsSync(androidId) ? androidId : resolved.id;
    },
  };
}

// Base path is configurable via VITE_BASE_URL env var for GitHub Pages deploys.
export default defineConfig(({ mode }) => ({
  base: process.env.VITE_BASE_URL ?? '/',
  build: {
    outDir: mode === 'android' ? 'dist-android' : 'dist',
    sourcemap: true,
    rollupOptions: {
      // vite-plugin-pwa isn't loaded in android mode (see plugins below), so
      // this virtual module has no provider. The only import site
      // (registerServiceWorker.ts) is gated behind Capacitor.isNativePlatform()
      // and never reaches it at runtime — safe to externalize.
      external: mode === 'android' ? ['virtual:pwa-register'] : [],
    },
  },
  plugins: [
    react(),
    androidPlatformResolve(mode),
    // Web-only: the Capacitor WebView never registers the service worker
    // (src/utils/registerServiceWorker.ts), so Workbox assets would be
    // dead weight in the Android build.
    mode !== 'android' && VitePWA({
      registerType: 'autoUpdate',
      strategies: 'generateSW',
      injectRegister: false,
      includeAssets: ['favicon.ico', 'icons/*.png'],
      manifest: {
        name: 'CoverDex',
        short_name: 'CoverDex',
        description: 'Build Pokémon teams and analyze type coverage offline.',
        theme_color: '#1a1a2e',
        background_color: '#1a1a2e',
        display: 'standalone',
        start_url: '.',
        icons: [
          { src: 'icons/icon-192x192.png', sizes: '192x192', type: 'image/png' },
          { src: 'icons/icon-512x512.png', sizes: '512x512', type: 'image/png', purpose: 'any maskable' },
          { src: 'icons/apple-touch-icon.png', sizes: '180x180', type: 'image/png' },
        ],
      },
      workbox: {
        globPatterns: ['**/*.{js,css,html,png,svg,ico}'],
        navigateFallback: 'index.html',
        runtimeCaching: [
          {
            urlPattern: /^https:\/\/pokeapi\.co\/.*$/i,
            handler: 'CacheFirst',
            options: {
              cacheName: 'pokeapi-cache',
              expiration: { maxEntries: 500, maxAgeSeconds: 60 * 60 * 24 * 30 },
              cacheableResponse: { statuses: [0, 200] },
            },
          },
          {
            urlPattern: /^https:\/\/raw\.githubusercontent\.com\/PokeAPI\/.*$/i,
            handler: 'CacheFirst',
            options: {
              cacheName: 'pokeapi-sprites',
              expiration: { maxEntries: 500, maxAgeSeconds: 60 * 60 * 24 * 30 },
              cacheableResponse: { statuses: [0, 200] },
            },
          },
        ],
      },
    }),
  ],
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html'],
      include: ['src/utils/**', 'src/hooks/**', 'src/components/**'],
    },
  },
}));
