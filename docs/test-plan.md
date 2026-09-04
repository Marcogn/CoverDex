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

## Phase 1 — Dataset sync

- [ ] **First launch sync.** Fresh install, no cache. Open the app — the
  Teams screen's sync banner appears (a short "Syncing Pokémon data…" line
  with a progress bar) and disappears within a few seconds on a normal
  connection, without ever blocking the empty state underneath it. This is
  the whole point of the phase — confirm it's actually fast, not just that
  it works.
- [ ] **Settings → Data, first launch.** Before the sync finishes: shows
  "Data not downloaded yet." After it finishes: shows the real species/move
  counts (should read 1351 Pokémon, 919 moves against the pinned revision —
  see `docs/implementation-decisions.md` for why 919, not 937), a synced-at
  timestamp in the device's own locale/format, and the short dataset
  revision (`d4f9a4a`).
- [ ] **Sync now.** Settings → Data → "Riscarica dati Pokémon"/"Redownload
  Pokémon data" re-syncs even though the cache is already fresh (confirm
  the synced-at timestamp actually updates), without disturbing anything
  else.
- [ ] **Clear cached data.** Tap "Cancella dati salvati"/"Clear cached
  data", confirm the dialog, confirm the cache reports "not downloaded yet"
  immediately after. Reopen Teams — the sync banner reappears and re-syncs
  on its own (no manual re-sync needed).
- [ ] **Offline first launch.** Turn off network before first launch (or
  after clearing cache). The sync banner shows briefly, Settings → Data
  reports the failure with the real underlying reason (not a generic
  message) and a "Riprova"/"Retry" button. Nothing else in the app is
  blocked or crashes. Turning network back on and tapping Retry succeeds.
- [ ] **Language switch mid-sync-display.** With Settings → Data showing a
  synced summary, switch language — the section title, summary sentence
  (singular "1 mossa"/"1 move" vs plural, if you can catch a build with
  exactly 1 move — otherwise just confirm the plural form reads
  correctly for the real count) and both button labels switch immediately.

### Known regressions

None yet.

## Phase 2 — Teams, slots and the custom roster

- [ ] **Create, rename, delete a team.** Teams screen's FAB opens a name
  dialog (Confirm disabled while blank); the new team opens immediately.
  Three-dot menu on a team row → Rename (pre-filled with the current name)
  and Delete (a confirmation dialog naming the team) both work and neither
  disturbs the other teams' order in the list.
  [ ] **Order survives a rename.** With three or more teams, rename one
  in the middle of the list — it must not jump to the end.
- [ ] **The slot editor's species picker.** Open any slot, type a partial
  species name — matches appear only after the first character (no list
  on focus alone), picking one fills the sprite, both type badges and the
  default ability. Picking a *different* species afterward fully replaces
  the slot (old moves/ability gone), not merged with the previous pick.
- [ ] **Type overrides persist independently per slot.** Change Type 1
  and/or Type 2 on one slot, save, reopen it — the override is still
  there; a different slot with the same species is unaffected.
- [ ] **Ability field accepts free text.** Type an ability that isn't in
  the suggestion list at all (a ROM-hack-only ability name), save, reopen
  the slot — the typed text is preserved verbatim, not rejected or
  cleared.
- [ ] **"Enable move slots" toggle** shows/hides the four move rows on
  every slot editor and on the roster editor alike (it's one shared,
  global setting) and survives an app restart.
- [ ] **A custom move's defaults.** In a move slot, type a name with no
  cache match into the "Custom move name" field (leave the picker above
  it untouched) — the type/power/category fields that appear default to
  Normal / blank / Physical, per `docs/implementation-decisions.md`
  ("Phase 2"), not Status.
- [ ] **"Save as custom" from a slot** adds the slot's current Pokémon to
  the custom roster (check the Custom Pokémon screen) without altering
  the team slot itself.
- [ ] **Back discards unsaved edits, on every path.** Open a slot, change
  several fields, then back out via (a) the top bar's arrow, (b) the
  system back gesture — reopen the slot both times: nothing was saved.
  Only the checkmark (Save) action commits.
- [ ] **Custom roster CRUD.** Add an entry (name required — Save disabled
  while blank), edit an existing one (tap its row), delete it (its own
  icon button, no confirmation prompt). Deleting one entry never affects
  the others' sort order.
- [ ] **Wiping the Pokédex cache leaves every team and roster entry
  untouched** — Settings → Data → "Clear cached data" with at least one
  populated team and one roster entry; reopen both afterward and confirm
  every field (species name, types, ability, moves) is exactly as it was.
- [ ] **Debug seed data**, debug build only: a fresh install with an empty
  database shows two teams ("Kanto Starters", partially filled; "National
  Dex All-Stars", full) and two custom roster entries on first launch,
  with no re-seeding (and no duplication) on subsequent launches or after
  creating a real team of your own.

### Known regressions

None yet.

## Phase 3 — Coverage analysis

- [ ] **The coverage basis notice is accurate.** With "Enable move slots"
  on and every filled slot carrying at least one damaging move, the
  notice reads "Analysis based on entered moves." With the toggle off
  (even if slots still have moves saved), it reads "Analysis based on
  Pokémon types only." With the toggle on and only *some* slots carrying
  a damaging move, it reads the mixed message naming which species use
  which basis.
- [ ] **Per-Pokémon cards are collapsed by default** and expand on tap,
  showing weaknesses (4×/2×), resistances (½×/¼×), immunities, the
  ability's own coverage effect (if any — e.g. Levitate showing a Ground
  immunity, Wonder Guard showing its note) and, when it has damaging
  moves, its move-type coverage.
- [ ] **Both coverage grids scroll independently, horizontally, without
  ever scrolling the screen itself sideways** — the Pokémon name column
  stays pinned in both while the 18 type columns (plus the Team
  best/Most vulnerable summary row) scroll under your finger.
- [ ] **Shared weaknesses show a count**, not just a badge — two team
  members both weak to the same type shows "×2", three shows "×3".
- [ ] **Uncovered types** lists every type nothing on the team hits
  super-effectively; with a team that covers all 18, the section
  instead shows the "full coverage" message.
- [ ] **Type overrides on a slot change the Analysis tab too** — override
  a slot's type from the slot editor (Phase 2), reopen the team, confirm
  the Analysis tab's grids and per-Pokémon card reflect the override, not
  the species' real types.
- [ ] **An empty team shows the "add Pokémon first" message**, not any of
  the seven sections, on the Analysis tab.

### Known regressions

None yet.
