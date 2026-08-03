import fs from 'node:fs';
import type { Plugin } from 'vite';

/**
 * Android build mode (`vite build --mode android`) resolves any local
 * import that has a sibling `<name>.android.tsx`/`.android.ts` file to that
 * sibling instead of the default file. This is the platform-presentation
 * convention documented in CLAUDE.md: shared hooks/logic stay in the
 * default file, only the Android-specific JSX lives in `.android.tsx`.
 * A no-op in every other mode, so the PWA build path is untouched.
 *
 * Kept dependency-light (only `node:fs` + a type-only `vite` import) so it
 * can be unit-tested without pulling the `vite` runtime into a jsdom test
 * environment — see src/test/androidPlatformResolve.test.ts.
 */
export function androidPlatformResolve(mode: string): Plugin | false {
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
      // An .android.tsx file importing (non-type) values from its own base
      // sibling — e.g. a pure helper function — is intentional reuse, not a
      // platform substitution request. Redirecting it back to itself would
      // create a self-referential import that can't find those exports.
      if (androidId === importer) return null;
      return fs.existsSync(androidId) ? androidId : resolved.id;
    },
  };
}
