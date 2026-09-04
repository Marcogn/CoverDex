# legacy-web

This is the React/Capacitor PWA CoverDex was before the native Android
rewrite (see [`../docs/plan/README.md`](../docs/plan/README.md)). It is kept
only as the behavioural reference for the port — its Vitest suite is the
oracle for expected values when porting an engine to Kotlin. It is never
edited, and it is deleted once Phase 6 of the migration plan confirms
everything in it has a native equivalent.

```bash
npm ci && npm test
```
