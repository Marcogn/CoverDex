# CLAUDE.md

Instructions for AI coding agents (Claude Code, GitHub Copilot, etc.)
working in this repository. Read this file in full before editing any
code — it's kept short on purpose; the detail it summarizes lives in
these companion docs, read on demand rather than up front:

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — narrative walkthrough
  of how the app fits together (data flow, the two engines, import/
  export, the web/Android split).
- [`docs/MODULES.md`](docs/MODULES.md) — per-file invariants for
  `src/utils`/`src/hooks`/`src/data` modules. Read the entry for a
  module before changing it.
- [`docs/android/PLATFORM.md`](docs/android/PLATFORM.md) — Android
  architectural invariants (build outputs, the shared-logic/platform-
  presentation convention, storage isolation).
- [`docs/android/BUILD.md`](docs/android/BUILD.md) — operational Android
  build detail: local builds, signing, CI, Firebase distribution, icon
  generation, troubleshooting.
- [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md) — local dev setup,
  environment variables, GitHub Pages deployment.
- [`docs/STATUS.md`](docs/STATUS.md) — current snapshot of what's
  implemented and what's known to be missing or in progress. Check this
  before starting a new session.
- [`CHANGELOG.md`](CHANGELOG.md) — one entry per release.

## Project Identity

CoverDex (formerly known as the Pokémon Team Analyzer) is a single-page PWA for building Pokémon
teams and analysing their type coverage offensively and defensively.
It supports custom Pokémon and per-slot type overrides, which makes it
useful for ROM hack runs and competitive draft building. It is **not**
a battle simulator, **not** a Pokémon Showdown replacement, and **not**
a damage calculator.

## Architecture Overview

- Single page React app. No router, no nested route state.
- All UI state lives in React. No Redux, no Zustand, no Recoil. The
  top-level `AppState` (`src/types/index.ts`) is persisted on-device and
  rehydrated on boot — `localStorage` on the web build, native
  `@capacitor/preferences` storage on Android (see `useUserDataStorage.ts`/
  `.android.ts` in [`docs/MODULES.md`](docs/MODULES.md) and "Storage
  isolation" in [`docs/android/PLATFORM.md`](docs/android/PLATFORM.md)).
- PokéAPI data (Pokémon list, types, moves, evolution chain summaries)
  is fetched once on first load, cached in `localStorage`, and **never
  re-fetched** unless the user explicitly resets the cache from the
  Settings panel.
- No backend. No authentication. No telemetry. Network is used only for
  the initial PokéAPI fetch and for sprite URLs. This has been proposed
  and turned down before — moving calculation/filtering into some kind of
  backend (an embedded server, a native-code layer reached over a
  Capacitor plugin bridge) wouldn't fix a real performance problem (the
  coverage/suggestion math is trivial at this data scale, comfortably
  microseconds in the WebView's JS engine) and would fork business logic
  between platforms, which is exactly what the shared-engine architecture
  exists to avoid. Don't reopen this without a measured, reproduced
  performance problem to point at.

## Key Data Flows

1. **App boot.** `usePokemonData` checks `localStorage` for the cache
   under `teamdex_pokeapi_cache`. If missing or its `version` does not
   match `CACHE_VERSION`, the hook calls `fetchPokemonData()`
   (`src/utils/pokeApiFetch.ts`), which fetches Pokémon, species,
   evolution chains, types, and moves from the static
   `raw.githubusercontent.com/PokeAPI/api-data` mirror (batched 50 at
   a time, 50 ms between batches, one retry per resource). On completion
   it writes the cache with the current `CACHE_VERSION`. `teamdex_userdata`
   is never touched during this process. This first-launch download is
   the main case where the whole UI blocks (`App.tsx`/`App.android.tsx`
   render `LoadingScreen` while `usePokemonData().loading` is true); a
   manual re-download via Settings → Data (`refresh()`) keeps the
   existing data usable in the rest of the app until the new fetch
   completes or fails. On Android there's a second, usually much shorter
   blocking wait on the same `LoadingScreen`: `App.android.tsx` also holds
   it until `userDataReady` (from `useAppShell`, backed by
   `useUserDataStorage.android.ts`) is `true` — see that module's entry
   in [`docs/MODULES.md`](docs/MODULES.md).
2. **Pokémon selection.** When the user picks a species in a slot, the
   slot reads the form's types from the cache, and pre-populates the
   ability field from `PokemonEntry.defaultAbility`. The user can
   override either type or the ability on that slot without affecting
   the cache.
3. **Analyze.** The coverage hook calls pure functions in
   `coverageEngine.ts` to produce per-member offensive coverage, the
   team union, defensive profiles, and shared weaknesses. The
   suggestion hook then runs over the same data to produce ranked
   additions or replacements.
4. **Export.** `showdownParser.ts` serialises the active team to a
   Showdown-style multi-block string, copied to the clipboard or
   downloaded as a `.txt` file.
5. **Import.** `showdownParser.ts` parses a Showdown paste, resolves
   each block against the cache, and fills the team slots. Unknown
   moves are kept as placeholder custom moves and flagged so the UI
   can prompt the user to complete them.

## Module Responsibilities

Per-file invariants for `src/utils`, `src/hooks`, and `src/data` modules
(what each one owns, what it must never do) live in
[`docs/MODULES.md`](docs/MODULES.md). Read the relevant entry before
touching one of those files.

## Type Chart Rules

- The type chart is an 18×18 matrix keyed by attacker type then
  defender type, with values from `{0, 0.5, 1, 2}`.
- Dual-type defense **multiplies** the two effectiveness values (it
  never adds them). A 2× weakness on top of another 2× weakness becomes
  4×; a 2× weakness against a 0× immunity becomes 0×.
- Type overrides set on a `TeamMember` (used for ROM hack typings)
  apply only to that team slot. They must never be written back into
  the cached `TypeChart` or into the cached `PokemonEntry`.

## Suggestion Engine Rules

The algorithm in `useSuggestions.ts`:

1. Filter the cached pool to entries with `isFinalEvolution === true`
   (mid-evolutions are dropped). Append custom Pokémon when the
   `includeCustoms` flag is on.
2. For each candidate, compute its offensive coverage from its types
   only (never from saved moves).
3. **Team size < 6 (addition mode).** For each candidate, count the
   types it covers that the team does not yet cover; that is its
   `gain`. Return the top 5 by `gain`.
4. **Team size = 6 (replacement mode).** Compute each existing
   member's `unique_contribution` = types it covers that no other
   member covers. The member with the smallest unique contribution is
   the *weakest link*. Compute the team's base coverage without the
   weakest link; for every candidate, `gain(R) = |base ∪ candidate
   coverage| − |team coverage|`. Return the top 5 by `gain`, keyed by
   species name to avoid duplicates.
5. **Final-evolution preference.** Built into step 1: mid-evolutions
   are never offered.
6. **Legendary handling.** The current build does not exclude
   legendaries (all forms are kept in the pool). If you re-introduce
   exclusion, the rule must be: legendaries are excluded unless the
   active team already contains one.

### Move-awareness fallback

Per-member offensive coverage uses the member's moves when the member
has any damaging move entered (`memberHasMoves`). Otherwise it falls
back to the member's own types. Candidates in the suggestion engine
are always evaluated by types only — we never invent a movepool for
them.

The team-detail page additionally gates this behavior on the
"Enable move slots" checkbox: when the toggle is off, members are
passed to the coverage / suggestion engines with `moves` cleared, so
analysis is type-only regardless of any moves that may still be
stored on the team data.

## Showdown Format Contract

### Read on import

- Species name (first non-comment line, splits on `@`).
- Ability line (`Ability: <name>`) — populated into `member.ability`.
- Move lines beginning with `- `; the move name is everything after
  `- ` until end-of-line.
- Type override comment of the form `# Types: type1[/type2]` (case
  insensitive, ignored if either type is not a valid `PokemonType`).

Lines matching `EVs:`, `IVs:`, or `Nature` are ignored.
Other `#` comment lines are ignored.

### Written on export

- Species name followed by `@ ` (empty item placeholder).
- `Ability: <ability>` line (ability value or empty if not set).
- `EVs: ` and ` Nature` placeholder lines.
- One `- <move name>` line per non-null move slot.
- A trailing `# Types: ...` comment line preserving the slot's types.

### Unknown moves on import

If `resolveMove(name)` returns `null` for a move, the parser builds a
placeholder move with `isCustom: true`, `damageClass: 'status'`,
`power: null`, and the original name. The move name is appended to
the returned `unknownMoveNames` list so the caller can flag the slot
for manual completion. The block is still imported.

## What NOT to Change Without Discussion

- The persisted-data schema (`AppState`/`teamdex_userdata` and the
  PokéAPI cache) on either storage backend — `localStorage` on the web,
  `@capacitor/preferences` on Android. Breaking either requires a written
  migration plan and a version bump on the storage key.
- The `coverageEngine.ts` pure-function signatures. They are consumed
  by both the analysis hook and the suggestion engine and by tests.
  Optional parameters (like `ability`) are fine to add.
- The Showdown format contract above (`exportMemberToShowdown`,
  `parseShowdownBlock`). External users may rely on round-tripping.
- The PWA manifest icon paths (`public/icons/icon-192x192.png`,
  `public/icons/icon-512x512.png`, `public/favicon.ico`, `public/favicon.svg`) — they are referenced by
  the `vite-plugin-pwa` config in `vite.config.ts`.
- The `abilityEffects.ts` map entries. Do not add or remove abilities
  without updating tests and this documentation.
- The `STARTER_FINALS` list in `teamGenerator.ts`. Adding new
  generations requires updating this hardcoded list.
- The composite score weights (0.5 / 1.0) used in both suggestion
  and team generation engines.
- `capacitor.config.ts` (`appId`, `appName`, `webDir`) and the Android
  `applicationId`/`namespace` in `android/app/build.gradle`, which must
  match it. Changing `appId` post-release changes the app's identity on
  any device that installed it under the old one.
- `package.json`'s `version` field outside of the release workflow (see
  [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md) → "Keeping the web and
  Android releases in sync"). It drives both the GitHub Release tag and
  the Android `versionName`/`versionCode`; bumping it by hand outside
  that workflow will desync the two.

## Common Pitfalls

- **Type badge display.** `TypeBadge` renders PokeAPI type sprites (small
  Scarlet/Violet icons) via `getTypeSpriteUrl()`. It no longer uses
  coloured pill text or abbreviations. Do **not** re-add `abbreviated`
  prop or inline colour classes.
- **Sprite URLs.** Always use `resolveSpriteUrl()` from
  `src/utils/spriteUtils.ts`; never access `spriteHome`,
  `spriteArtwork`, or `spriteDefault` directly from components.
- **Cache version.** If you add new fields to the cached payload
  structure, increment `CACHE_VERSION` in `src/utils/pokeApiFetch.ts` —
  otherwise users with old caches will get runtime errors on missing
  fields. A version mismatch is treated as "no cache" and silently
  re-fetched, not an error.
- **Batch fetching.** Do not increase the batch size above 50 without
  testing. GitHub's CDN rate limits are undocumented and can cause
  silent failures at higher concurrency.
- **Alternate forms.** Some Pokémon share a species but have different
  type arrays per form (Rotom, Deoxys, Wormadam, …). Always use the
  PokéAPI **form** endpoint, not the species endpoint, to read types.
- **Evolution chain timing.** The evolution chain fetch is a separate
  network round-trip from the Pokémon list. Do not assume evolution
  data is available before `usePokemonData` reports the cache as
  fully loaded.
- **Offensive type for moves.** When computing offense from moves,
  use the move's `type`, not the user Pokémon's types. Same-type
  attack bonus is not modelled.
- **Dual-type defense.** Effectiveness across two defender types is
  multiplicative, never additive. Watch for the immunity case where
  one type's 0× cancels the other type's 2×.

## Android Platform

CoverDex ships a native Android shell via Capacitor, additive to the PWA
— it must never carry business logic of its own. Architectural
invariants (build outputs, the shared-logic/platform-presentation file
convention, storage isolation) are in
[`docs/android/PLATFORM.md`](docs/android/PLATFORM.md); operational
build/CI/signing/distribution detail is in
[`docs/android/BUILD.md`](docs/android/BUILD.md). Read both before
touching anything under `android/` or an `.android.tsx`/`.android.ts`
file.

## Dev Commands

```bash
npm run dev            # local dev server
npm run build          # type-check then production build
npm run preview        # preview the production build locally
npm run test           # run the Vitest suite once
npm run test:coverage  # run the Vitest suite with coverage
npm run generate-icons # generate PWA icons locally
npm run android:icons  # regenerate PWA + Android launcher icons and splash screens
npm run build:android  # type-check then build the Android bundle to dist-android/
npm run cap:sync       # sync dist-android/ into android/
npm run android:open   # open the Android project in Android Studio
npm run android:build  # build:android + cap sync android
```

See [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md) for full local setup
and [`docs/android/BUILD.md`](docs/android/BUILD.md) for icon
regeneration detail.

### i18n

Uses i18next + react-i18next.
Translation files: src/i18n/locales/en.json and it.json.
All user-visible strings must use the useTranslation hook.
Pokémon names and move names are NOT translated.
Language persisted in localStorage key 'teamdex_lang'.

### Searchable Dropdowns

All searchable dropdowns (Pokémon picker, move picker, anchor picker in
Surprise Me, ability picker) follow the same UX pattern:
- On focus/open, do **not** show any list items.
- Show placeholder "Start typing to search..." inside the input.
- Only show results once the user has typed at least 1 character.
- Show **all** matching results (no pagination, no item cap).
- Dropdown list is internally scrollable (min 240px, max 40vh when
  fixed-position mode is enabled).

### Analysis page structure

Seven sections in order: Coverage basis notice, Per-Pokémon
breakdown, Offensive grid, Defensive grid, Shared weaknesses,
Uncovered types, Suggestions.

### Suggestion scoring

Composite score = offensive_gain - 0.5×new_weaknesses
                  - 1.0×aggravated_shared_weaknesses
See suggestionEngine.ts for full implementation.
Do not change weights without updating this documentation
and the tests.

### Key unit tests

- `teamGenerator.test.ts` — "anchor composite score validation":
  Verifies that when anchor is Swampert (Water/Ground), the generated
  team does not contain more than 1 additional Water-type Pokémon in
  at least 4 out of 5 probabilistic runs. This ensures the composite
  score correctly penalizes redundant type coverage when anchors are
  present.
