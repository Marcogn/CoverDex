# Module Responsibilities

Per-file invariants, moved out of [`CLAUDE.md`](../CLAUDE.md) to keep
that file short — see it for the rules that apply across the whole
codebase (what not to change, common pitfalls, etc.). This file is a
reference: read the entry for a module before changing it, not the
whole thing before every edit.

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

### `src/hooks/useUserDataStorage.ts` / `useUserDataStorage.android.ts`

Owns reading and writing `teamdex_userdata` (the `AppState` — teams, custom
Pokémon, settings) as a single hook, `useAppShell.ts`'s only source of
`state`/`setState`. This is the one place the platform-resolve convention
(see [`docs/android/PLATFORM.md`](android/PLATFORM.md)) is applied to a
hook instead of a component:
- **Web** (`useUserDataStorage.ts`, also the default/shared file): reads
  `localStorage` synchronously on first render, writes back on every
  `state` change. `ready` is always `true` — nothing to wait for.
- **Android** (`useUserDataStorage.android.ts`): reads/writes through
  `@capacitor/preferences` instead, which is async. `state` starts at the
  same default (a single fresh team) the web version falls back to,
  `ready` starts `false`, and the effect that persists `state` on change is
  gated on `ready` so it can never fire with the default value before the
  real persisted value has been read (that would silently overwrite it).
  `useAppShell` exposes `ready` as `userDataReady`; `App.android.tsx` holds
  `LoadingScreen` until it flips `true`, the same pattern already used for
  `usePokemonData().loading`. **One-time migration:** if Preferences has
  nothing yet but the WebView's `localStorage` does (a device that had the
  app installed before this migration shipped), that legacy value is
  adopted once, persisted into Preferences, and the old `localStorage` key
  is cleared.
- Must **not** contain team/coverage/suggestion logic — `useAppShell.ts`
  still owns all of that, this hook only owns getting `AppState` in and out
  of storage. `USER_DATA_KEY` (`'teamdex_userdata'`) is exported from the
  web file; the Android file imports it from there rather than redeclaring
  it, so the key can't drift between platforms.

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
