# Test plan

Manual, on-device verification for the native Android migration
(`docs/plan/`). Automated tests (`./gradlew testDebugUnitTest`) cover pure
logic and Robolectric-testable Room/DataStore code; this file covers
everything that genuinely needs a real device or emulator — the real
network, locale switching, image rendering, file pickers — plus one
"Known regressions" entry per real bug a manual pass actually found.

One new section per phase, added in the same PR that ships the phase.

## Phase 0 — Foundation

- [ ] **Fresh install.** Uninstall any existing CoverDex build, install the
  debug APK. App launches without crashing, shows the Teams screen with its
  empty state (title + subtitle, no crash, no infinite spinner).
- [ ] **Upgrade over the old Capacitor build**, if a device still has it
  installed. The native APK (same `applicationId`,
  `com.marcogn.coverdex`) installs as an *update*, not a fresh install
  prompt, and launches straight into the new native UI. (No data carries
  over — see `docs/implementation-decisions.md`; this step only confirms
  the install itself succeeds, since the two builds' signing keys must
  eventually match for this to work at all once Phase 6 signs a release —
  covered again there.)
- [ ] **Drawer navigation.** Open the drawer (hamburger icon), confirm all
  three items are present (Your Teams / Custom Pokémon / Settings) and each
  one navigates to its screen. Re-opening the drawer and tapping the
  already-open screen's item does not push a duplicate back-stack entry
  (back from Roster after Teams → Roster → Settings → Roster should return
  to the app's previous screen, not silently re-enter Roster twice).
- [ ] **Theme picker.** Settings → Appearance: switching between System /
  Light / Dark actually changes the app's colours immediately. Force-close
  and reopen the app — the chosen theme is still applied (not reset to
  System).
- [ ] **Language picker.** Settings → Language: switching to Italiano/
  Italian and English changes every visible string on every screen (drawer
  labels, screen titles, empty-state text, the Settings screen's own
  labels) — not just the screen you were on when you switched. Force-close
  and reopen the app — the chosen language is still applied. Switching to
  "System default" follows the device's own language.
- [ ] **Dynamic colour (API 31+ device/emulator only).** With Material You
  wallpaper-based colour enabled at the OS level, the app's colour scheme
  follows the wallpaper instead of the seeded brand purple. On an
  API ≤ 30 device or with dynamic colour unavailable, the app falls back
  to the seeded purple scheme (`#5B21B6`-derived) without crashing.
- [ ] **Launcher icon.** Home screen and app switcher show the ported
  Capacitor icon (purple background, unchanged artwork) at every density
  the device renders it at, adaptive-icon masking (circle/squircle/etc.)
  included.
- [ ] **Dark mode + edge-to-edge.** With the system in dark mode and gesture
  navigation, the status bar and navigation bar are transparent and the
  content isn't obscured by either.

### Known regressions

None yet.
