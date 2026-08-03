# CLAUDE.md

Instructions for AI coding agents (Claude Code, GitHub Copilot, etc.)
working in this repository. Read this file in full before editing any
code. For a narrative, human-facing walkthrough of how the app fits
together (data flow, the two engines, import/export, the web/Android
split), see [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — this file
stays focused on rules and invariants for agents.

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
  top-level `AppState` (`src/types/index.ts`) is persisted to
  `localStorage` and rehydrated on boot.
- PokéAPI data (Pokémon list, types, moves, evolution chain summaries)
  is fetched once on first load, cached in `localStorage`, and **never
  re-fetched** unless the user explicitly resets the cache from the
  Settings panel.
- No backend. No authentication. No telemetry. Network is used only for
  the initial PokéAPI fetch and for sprite URLs.

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
   the only case where the whole UI blocks (`App.tsx`/`App.android.tsx`
   render `LoadingScreen` while `usePokemonData().loading` is true); a
   manual re-download via Settings → Data (`refresh()`) keeps the
   existing data usable in the rest of the app until the new fetch
   completes or fails.
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

### `src/utils/pokeApiFetch.ts`

Pure (no React, no `localStorage`) fetch/assembly engine — the actual
PokéAPI/api-data mirror client. Exports `fetchPokemonData(onProgress?,
signal?)`, `CACHE_KEY`, `CACHE_VERSION`, and the `FetchProgress`/
`FetchStage` types. The mirror only serves numeric-ID paths (e.g.
`/pokemon/1/index.json`); name-based paths 404, so every resource type
resolves its numeric ID from an index/list endpoint first. Batches 50
requests at a time, 50 ms between batches, one retry per resource
(`RETRY_DELAY_MS`), and throws rather than returning a partial result if
zero final evolutions come back (a signal the evolution-chain fetches all
failed). Must **not** import React or touch `localStorage` — that's
`usePokemonData.ts`'s job.

### `src/hooks/usePokemonData.ts`

Thin React wrapper around `pokeApiFetch.ts`: reads/writes the
`localStorage` cache under `teamdex_pokeapi_cache`, exposes `loading`
(true only for a genuine first-launch fetch with nothing cached yet),
`refreshing` (true for a background/manual re-fetch while old data is
still usable), `progress`, `error`, and `refresh()` (clears the cache
and re-fetches; wired to the Settings → Data "Redownload" button). Must
**never** touch `teamdex_userdata`. Must **not** contain coverage logic,
suggestion logic, or UI-only state. Invariants: the cache is either
complete and versioned (`{ version: CACHE_VERSION, data: { … } }`) or
fully absent — never partial. A stale-versioned cache is treated as
absent and re-fetched.

### `src/utils/spriteUtils.ts`

Owns all sprite URL resolution logic via `resolveSpriteUrl(pokemon,
context)`. Must **never** fetch images — only selects among the URL
strings stored on a `PokemonEntry`. Invariant: always returns `null`
rather than throwing when sprite data is missing or malformed.

### Sprite Resolution

- Sprites are never stored as binary data, only as URL strings on the
  `PokemonEntry` (`spriteHome`, `spriteArtwork`, `spriteDefault`).
- Card/slot context priority: HOME → official artwork → pixel sprite
  → `null` (caller renders the placeholder).
- Dropdown thumbnails always use the pixel sprite — HOME renders are
  too heavy for list items.
- Custom Pokémon saved to the roster never carry sprite URLs.
- Resolution is centralised in `src/utils/spriteUtils.ts`. Do **not**
  inline fallback logic in components.

### `src/hooks/useTypeChart.ts`

Thin selector that returns the cached type chart. Must **not** mutate
the chart. The chart is read-only at runtime; per-slot type overrides
live on `TeamMember`, never on the chart.

### `src/hooks/useCoverageAnalysis.ts`

Adapts `coverageEngine` to React: takes a team plus the chart and
returns memoised `{ team, defense, shared }`. Must **not** fetch data,
must **not** mutate inputs, must **not** include suggestion logic.
Returns `null` for an empty team rather than partial structures.

### `src/hooks/useSuggestions.ts`

Wraps the pure suggestion ranking logic and memoises results. Hosts
the policy switches (`includeCustoms`) but delegates ranking to a
pure function so it can be unit-tested without React. Must **not**
fetch data, must **not** mutate the team or roster.

### `src/utils/coverageEngine.ts`

Pure functions only. Owns the offensive coverage and defensive
multiplier math used everywhere else. Must **not** import React,
hooks, or anything from `src/hooks`. Invariant: every exported
function is referentially transparent given a `TypeChart` and a list
of `TeamMember`. The `defensiveMultiplier` function accepts an
optional `ability?: string` parameter to apply ability-based
immunities and multipliers (from `abilityEffects.ts`).

### `src/data/typeSprites.ts`

Hardcoded mapping of `PokemonType` → PokeAPI numeric type ID, plus a
`getTypeSpriteUrl(type)` helper that returns the Scarlet/Violet small
sprite URL. Used by `TypeBadge` for all type display. Do **not** fetch
type IDs at runtime — they are stable constants.

### `src/data/abilityEffects.ts`

Typed map of ability slugs (lowercase, hyphenated, PokeAPI format) to
coverage-relevant effects. Three effect kinds:
- `immunity`: incoming attacks of that type deal 0 damage.
- `multiplier`: modifies the type chart result by a factor (defensive side).
- `badge-only`: UI-only indicator, no calculation change.

Do **not** add or remove entries from this map without discussion.
The map is consumed by `coverageEngine.ts` and by UI components for
ability badges.

`KNOWN_ABILITIES_WITH_EFFECTS` is an exported array of display-format
ability names (lowercase, space-separated) used as the canonical list
for the ability picker dropdown UI. It contains the same abilities
whose slugs are keys in `ABILITY_EFFECTS`.

### `src/hooks/teamGenerator.ts`

Pure team generation algorithm used by the "Surprise Me" feature.
Uses the same composite score formula as the suggestion engine
(gain − 0.5×new_weaknesses − 1.0×aggravated_shared_weaknesses) with
a ±0.01 random tie-breaking factor. Runs fully client-side.
Exports: `generateTeam`, `regenerateSlot`, `buildEligiblePool`,
`STARTER_FINALS`, `DEFAULT_CONSTRAINTS`.

**Anchor inclusion:** Anchor (locked) Pokémon are included in the
`currentTeam` from iteration step 0 of `generateTeam`. The composite
score for each candidate is computed against the full partial team
including anchors — never against an empty team. This ensures that
`aggravated_shared_weaknesses` correctly counts weaknesses already
present in the anchor set.

**Slot budget constraint:** The constraints step uses +/- counters
(no checkboxes). Each category (starters, legendaries/mythicals,
mega, dynamax, custom) has a numeric counter starting at 0. The
budget rule is: `anchorCount + sum(all counters) ≤ 6`, enforced by
disabling the `+` button when the budget is full. No clamping — the
`+` button simply becomes unavailable. Free slots (remainder after
anchors + counters) are filled by the algorithm using composite score
with no category filter.

**Legendaries and Mythicals — merged counter:** A single counter
`legendaryMythicalSlots` controls both legendary and mythical
Pokémon. The pool includes any entry where `isLegendary === true ||
isMythical === true`. The old separate `legendarySlots` and
`mythicalSlots` fields have been removed.

**"Exactly N" constraint semantics:** All constrained counters use
"exactly N" semantics. The algorithm:
1. Reserves N slots for each constrained category.
2. Fills reserved slots first by running the composite score only
   over the category sub-pool.
3. Fills remaining free slots from the unconstrained pool, excluding
   category members whose quota is already met.

Categories and their filters:
- **Starters:** `STARTER_FINALS` species set membership.
- **Legendaries / Mythicals:** `isLegendary === true || isMythical === true`.
- **Mega evolutions:** name includes `-mega`.
- **Dynamax/Gmax:** name includes `-gmax`.

**Data field audit (isLegendary / isMythical):** `PokemonEntry.isLegendary`
and `PokemonEntry.isMythical` (both boolean) are populated by
`pokeApiFetch.ts` from the PokéAPI species endpoint's `is_legendary` and
`is_mythical` fields. Verified: Mewtwo has
`isLegendary: true`, Mew has `isMythical: true`, Articuno/Rayquaza
have `isLegendary: true`, and non-legendaries like Cinccino have
both set to `false`.

**Per-slot re-randomize:** `regenerateSlot` picks randomly among the
top 5 scoring candidates from a pool that excludes only the other 5
team members. Logs `console.error` and returns the existing member
unchanged when the candidate pool is empty.

### `src/utils/showdownParser.ts`

Owns the Showdown format contract: serialisation (`exportMemberToShowdown`,
`exportTeamToShowdown`) and parsing (`parseShowdownBlock`,
`parseShowdownTeam`). Must **not** touch `localStorage`, must **not**
fetch from PokéAPI, must **not** depend on React. All species/move
resolution is injected via the `resolveMove` and `resolveTypes`
callbacks so the parser stays pure.

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

- The `localStorage` cache schema. Breaking it requires a written
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

CoverDex ships a native Android shell via Capacitor, additive to the PWA —
it must never carry business logic of its own. Stable invariants only;
operational/build detail lives in `docs/android/BUILD.md`.

- `android/` is a **committed native project**, not build output. Its
  `res/`, Gradle config, and Java sources are source of truth; only build
  artifacts, local SDK paths, and signing secrets are gitignored (see
  `.gitignore`).
- `@capacitor/cli` requires **Node >=22** — stricter than the web app's
  Node 18+. Any command that shells out to `cap` (`npx cap sync android`,
  `npm run android:build`, CI) needs Node 22+, even though `deploy.yml`
  and `pr-check.yml` stay on Node 20 for the unrelated web pipeline.
- `capacitor.config.ts`: `appId: "com.marcogn.coverdex"`,
  `appName: "CoverDex"`, `webDir: "dist-android"`. `appId` is
  rename-sensitive — same category as the `VITE_BASE_URL` in `deploy.yml`
  — because the repo is still named `poke-team-builder` pending a rename
  to `coverdex`.
- Run `npx cap sync android` (or `npm run android:build`, which also runs
  the Android web build first) after every change and before any native
  build — it copies `dist-android/` into
  `android/app/src/main/assets/public`. A native build against a stale
  sync serves an outdated WebView.
- The Workbox service worker must not register when
  `Capacitor.isNativePlatform()` is true (`src/utils/registerServiceWorker.ts`).
  Native assets are bundled locally, not served over the network there.
- `.github/workflows/android-build.yml` requires GitHub Secrets, values
  never documented here: `ANDROID_KEYSTORE_BASE64`,
  `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`,
  `FIREBASE_APP_ID`, `FIREBASE_SERVICE_ACCOUNT`. Android build outputs
  (debug and signed release alike) must never go through
  `actions/upload-artifact` — this is a public repo, and Actions artifacts
  are downloadable by any signed-in GitHub user. They go to Firebase App
  Distribution instead, which only reaches explicitly invited testers.

### Two build outputs: `dist/` (PWA) vs `dist-android/` (Android)

`vite build` (plain, `npm run build`) produces `dist/` exactly as before —
the PWA is untouched. `vite build --mode android` (`npm run build:android`)
produces a separate `dist-android/`, built by `vite-plugins/androidPlatformResolve.ts`:
a Vite plugin, active only in `--mode android`, that resolves any local
import to its `<Name>.android.tsx`/`.android.ts` sibling when one exists,
otherwise falls through to the default file. This is what lets the
Material layer below exist only in the Android build — MUI/Emotion never
appear in the `dist/` bundle (verified by grepping the built output; there
is no automated bundle-size CI check, so re-verify by hand — `grep -c
emotion dist/assets/*.js` should print `0` — after any change to
`vite.config.ts` or the plugin). `vite-plugin-pwa` is also android-mode-only
(the WebView never needs the service worker or its manifest).

### Shared logic / platform-presentation file convention

Every restyled component follows the same pattern: the default file
(`Component.tsx`) holds the **shared** hooks/state/handlers and is also
the **web** presentation (Tailwind), consumed unchanged by the PWA. A
sibling `Component.android.tsx`, when present, is picked up automatically
by the platform-resolve plugin and holds the **Android** presentation
(MUI) — imported with the exact same props contract (exported from the
default file, e.g. `TeamsPageProps`) and, wherever the component has
non-trivial logic, driven by hooks pulled out of the default file (e.g.
`useAppShell` in `src/hooks/useAppShell.ts`, consumed identically by
`App.tsx` and `App.android.tsx`) or by pure helper functions re-exported
from the default file (e.g. `collectAttackingTypes`, `multLabel` from
`CoverageGrid.tsx`). Business logic is never forked between the two files.

One subtlety worth knowing before touching the plugin: an `.android.tsx`
file importing a *type* from its own base sibling (`import type { XProps }
from './X'`) is invisible to the plugin — TypeScript strips type-only
imports before Rollup ever resolves them. But a *value* import from the
base sibling (a shared pure helper) is a real import the plugin sees, and
naively redirecting it would loop the file back to itself; the plugin
special-cases this (see its own comments and
`src/test/androidPlatformResolve.test.ts`).

Screens restyled for Android (all of them, as of this writing): top-level
nav/shell (`App.android.tsx`), Teams list, Settings, Custom Pokémon
roster, Team Builder (Pokémon tab — slots, moves, ability picker, export/
delete dialogs), and the Analysis tab (including the offensive/defensive
grids as MUI `Table`/`TableContainer` with a manually-sticky first column,
and suggestion cards). The shared `SearchableDropdown`/`AbilityDropdown`
Autocomplete implementations cover every picker (Pokémon, move, ability,
and the Surprise Me anchor picker) from one file each. `SurpriseMeModal`
and the roster's now-dead `CustomRoster.tsx` were not restyled (the latter
isn't imported anywhere on either platform).

### Storage isolation (PWA vs Android)

The PWA's `localStorage` (both `teamdex_userdata` and, if present,
`teamdex_pokeapi_cache`) lives in the mobile/desktop browser's storage
partition for the GitHub Pages origin. The Capacitor Android app's WebView
has its own OS-sandboxed, app-private storage, entirely disconnected from
any browser — this is inherent to how Capacitor's WebView works, not
something either build configures. **The two releases never share data**:
installing the Android app does not surface a user's PWA teams, and vice
versa. Any future cross-device sync feature is a deliberate, separate
project — neither release should grow one implicitly.

### PokéAPI download is identical on both platforms

`usePokemonData`/`pokeApiFetch.ts` (see "Key Data Flows" and "Module
Responsibilities" above) run the exact same way on the PWA and inside the
Capacitor WebView — same mirror, same batching, same `localStorage` cache
key. There is no Android-specific data step and no build-time generation
script; the dataset is never bundled into either `dist/` or
`dist-android/`. The Android WebView needs the standard Capacitor
`android.permission.INTERNET` (already required for sprite `<img>` URLs,
present in the generated `AndroidManifest.xml`) for this to work on
device. `src/test/noRuntimePokeApiFetch.test.ts` guards against ever
calling the **live** `pokeapi.co` REST API (as opposed to the static
mirror) from either platform's runtime code — it fails if an actual
`pokeapi.co` URL is constructed anywhere under `src/` outside the test
itself; a comment merely *mentioning* the host (to explain why the mirror
is used instead) does not trip it.

## Dev Commands

```bash
npm run dev            # local dev server
npm run build          # type-check then production build
npm run preview        # preview the production build locally
npm run test           # run the Vitest suite once
npm run test:coverage  # run the Vitest suite with coverage
npm run generate-icons # generate PWA icons locally
npm run build:android  # type-check then build the Android bundle to dist-android/
npm run cap:sync       # sync dist-android/ into android/
npm run android:open   # open the Android project in Android Studio
npm run android:build  # build:android + cap sync android
```

### i18n

Uses i18next + react-i18next.
Translation files: src/i18n/locales/en.json and it.json.
All user-visible strings must use the useTranslation hook.
Pokémon names and move names are NOT translated.
Language persisted in localStorage key 'teamdex_lang'.

### PWA Icons

Icons are generated at build time by scripts/generate-icons.mjs
using the sharp package. They are gitignored and regenerated
on every deploy. Do not commit icon PNG files.
To regenerate locally: npm run generate-icons

The same script also writes `assets/icon.png` and `assets/splash.png`
(gitignored), the source images `@capacitor/assets` reads to generate the
Android launcher icon densities and splash screens under
`android/app/src/main/res/` (those generated `res/` files ARE committed —
see "Android Platform" above). Regenerate with:
`npm run generate-icons && npx capacitor-assets generate --android`.

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
