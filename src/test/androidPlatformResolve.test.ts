import { describe, expect, it } from 'vitest';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { androidPlatformResolve } from '../../vite-plugins/androidPlatformResolve';

const srcDir = path.dirname(fileURLToPath(import.meta.url)).replace(/\/test$/, '');
const mainTsx = path.join(srcDir, 'main.tsx');
const appTsx = path.join(srcDir, 'App.tsx');
const appAndroidTsx = path.join(srcDir, 'App.android.tsx');

function fakeResolveContext(resolvedId: string) {
  return { resolve: async () => ({ id: resolvedId, external: false }) };
}

function getResolveId() {
  const plugin = androidPlatformResolve('android');
  if (!plugin || typeof plugin.resolveId !== 'function') {
    throw new Error('expected android plugin with a resolveId hook');
  }
  return plugin.resolveId as (...a: unknown[]) => unknown;
}

describe('androidPlatformResolve (vite android build plugin)', () => {
  it('is a no-op outside android mode', () => {
    expect(androidPlatformResolve('production')).toBe(false);
    expect(androidPlatformResolve('development')).toBe(false);
  });

  it('redirects an import to its .android.tsx sibling when one exists', async () => {
    const result = await getResolveId().call(fakeResolveContext(appTsx), './App', mainTsx, {});
    expect(result).toBe(appAndroidTsx);
  });

  it('falls back to the resolved file when no .android sibling exists', async () => {
    const noSiblingFile = path.join(srcDir, 'i18n.ts');
    const result = await getResolveId().call(fakeResolveContext(noSiblingFile), './i18n', appTsx, {});
    expect(result).toBe(noSiblingFile);
  });

  it('does not redirect an already-android-specific import', async () => {
    const result = await getResolveId().call(fakeResolveContext(appAndroidTsx), './App.android', mainTsx, {});
    expect(result).toBeNull();
  });

  it('does not redirect an .android.tsx file importing values from its own base sibling', async () => {
    // e.g. CoverageGrid.android.tsx doing `import { collectAttackingTypes } from './CoverageGrid'`
    // — returning null here defers to normal resolution (-> the base file),
    // rather than looping the import back to the android file itself.
    const result = await getResolveId().call(fakeResolveContext(appTsx), './App', appAndroidTsx, {});
    expect(result).toBeNull();
  });
});
