import { describe, it, expect, vi, beforeEach } from 'vitest';

const isNativePlatform = vi.fn();

vi.mock('@capacitor/core', () => ({
  Capacitor: { isNativePlatform: () => isNativePlatform() },
}));

describe('shouldRegisterServiceWorker', () => {
  beforeEach(() => {
    isNativePlatform.mockReset();
  });

  it('returns false when running inside the Capacitor native WebView', async () => {
    isNativePlatform.mockReturnValue(true);
    const { shouldRegisterServiceWorker } = await import('../registerServiceWorker');
    expect(shouldRegisterServiceWorker()).toBe(false);
  });

  it('returns true when running as a regular web app', async () => {
    isNativePlatform.mockReturnValue(false);
    const { shouldRegisterServiceWorker } = await import('../registerServiceWorker');
    expect(shouldRegisterServiceWorker()).toBe(true);
  });
});
