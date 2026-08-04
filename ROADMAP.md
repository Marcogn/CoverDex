# Roadmap

Deferred, intentionally-out-of-scope items from past work, tracked here so
they aren't forgotten. Not a task tracker — see GitHub Issues for that.
For a broader snapshot of what's implemented versus still open, see
[`docs/STATUS.md`](docs/STATUS.md).

## Android

- **Migrate `teamdex_userdata` from `localStorage` to `@capacitor/preferences`.**
  The Android WebView's `localStorage` is not guaranteed as durable as
  native storage (can be cleared under storage pressure, isn't backed up
  the same way). Capacitor's Preferences plugin would give native-backed
  persistence on Android, at the cost of an async read/write API replacing
  today's synchronous `localStorage` calls in `usePersistedState` and
  friends — a real refactor, not a drop-in swap. Not implemented as part of
  the initial Android build (see CLAUDE.md non-goals for that pass).
- **Promote `.github/workflows/android-build.yml` from manual-dispatch to
  automatic on merge to `main`.** Currently triggers only on
  `workflow_dispatch` and pushes to the Android development branch, once
  the Android build has proven stable over a few iterations (no flaky
  Gradle/signing failures) this should switch to running on every push to
  `main`, mirroring `deploy.yml`.
- **Play Store submission.** Explicitly out of scope for the initial
  Capacitor integration — CI produces a signed APK/AAB and distributes the
  APK only to invited testers via Firebase App Distribution (see
  `docs/android/BUILD.md`); the AAB is built for eventual Play Store
  readiness but isn't uploaded anywhere yet. Fastlane/Play Console
  automation is a separate future project once the app is stable enough to
  warrant store distribution.
- **iOS via Capacitor.** Capacitor was chosen over a Trusted Web Activity
  specifically to keep this path open, but no iOS project setup has been
  done yet.
