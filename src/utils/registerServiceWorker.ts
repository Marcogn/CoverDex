import { Capacitor } from '@capacitor/core';

/**
 * The Workbox service worker assumes assets are served over the network.
 * Inside the Capacitor WebView, assets are bundled locally, so registering
 * it there is both pointless and can shadow the bundled app with a stale
 * cached one. Only register on web.
 */
export function shouldRegisterServiceWorker(): boolean {
  return !Capacitor.isNativePlatform();
}

export async function initServiceWorker(): Promise<void> {
  if (!shouldRegisterServiceWorker()) {
    return;
  }
  const { registerSW } = await import('virtual:pwa-register');
  registerSW({ immediate: true });
}
