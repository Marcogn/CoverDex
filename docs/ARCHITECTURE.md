# Architecture

A narrative walkthrough of how CoverDex fits together, for anyone (human
or agent) who needs the bigger picture before diving into a specific file.
For rules, invariants, and "don't touch this without discussion" style
guidance, see [`CLAUDE.md`](../CLAUDE.md) — this document explains *how it
works*, that one governs *how to change it*.

## What CoverDex is

CoverDex is a single-page app for building Pokémon teams and analysing
their offensive/defensive type coverage. It ships two ways from one
codebase:

- **A Progressive Web App**, deployed to GitHub Pages, installable on
  desktop/mobile browsers, styled with Tailwind CSS.
- **A native Android app**, wrapping the same React app in a Capacitor
  WebView, restyled with Material UI (MUI) so it feels native on Android
  instead of like an embedded website.

There is no backend, no authentication, and no server-side component of
any kind. Everything — team data, the Pokémon/move dataset, settings —
lives on-device.

## The three kinds of state

1. **Pokémon/move/type-chart dataset** — read-only reference data (every
   Pokémon, every move, the 18×18 type effectiveness chart). Downloaded
   once from a public data mirror and cached. See "Data acquisition"
   below.
2. **User data** (`teamdex_userdata` in `localStorage`) — teams, custom
   Pokémon, and app settings (theme, language, suggestion filters). The
   only state a user would be upset to lose.
3. **UI-only state** — which screen/tab is open, form inputs, modal open/
   closed. Lives in React state, never persisted.

These three are deliberately kept apart: the dataset hook
(`usePokemonData`) never touches `teamdex_userdata`, and vice versa. A
"reset Pokémon data" action can never accidentally wipe a user's teams,
and importing/exporting teams never re-triggers a data download.

## Data acquisition

On first launch, `usePokemonData` (`src/hooks/usePokemonData.ts`) finds no
usable cache and calls `fetchPokemonData()` (`src/utils/pokeApiFetch.ts`),
which downloads every Pokémon, every move, and the type chart from the
static [`PokeAPI/api-data`](https://github.com/PokeAPI/api-data) mirror on
GitHub (`raw.githubusercontent.com/PokeAPI/api-data`) — a JSON snapshot of
the live PokéAPI, chosen specifically so tens of thousands of app users
aren't all hammering the live `pokeapi.co` REST API with no CORS or
rate-limit guarantees for a browser client. The mirror only serves
numeric-ID paths (`/pokemon/1/index.json` — name-based paths 404), so the
fetcher first pulls each resource type's index/list endpoint to resolve
names to IDs, then fetches details in batches of 50 with a short delay
between batches and one retry per failed request. This takes roughly
30–90 seconds depending on connection speed, during which the whole app
is blocked behind a `LoadingScreen` showing per-stage progress (Pokémon →
species → evolution chains → types → moves).

The assembled dataset is cached in `localStorage` under
`teamdex_pokeapi_cache`, versioned (`CACHE_VERSION` in `pokeApiFetch.ts`).
Every subsequent launch reads straight from that cache — no network call,
no loading screen. The only way to re-download is explicit: the
**Settings → Data → Redownload Pokémon data** button, which clears the
cache and re-fetches in the background while the existing (now possibly
stale) data stays fully usable, only swapped in once the new fetch
succeeds. A failed refresh leaves the old data untouched and surfaces the
error in that same Settings section — nothing elsewhere in the app shows
data-download status.

This is identical on the PWA and the Android app: same mirror, same
hook, same cache key, no platform-specific data step.

## The two engines

Everything downstream of the dataset flows through two pure, dependency-
free modules (no React, no `localStorage`, fully unit-testable):

- **`src/utils/coverageEngine.ts`** — given a `TypeChart` and a list of
  team members, computes offensive coverage (which types each member's
  moves — or, absent moves, its own types — hit super-effectively),
  defensive profiles (weaknesses/resistances/immunities per member,
  ability-adjusted via `abilityEffects.ts`), and team-wide shared
  weaknesses.
- **`src/hooks/suggestionEngine.ts`** (wrapped by `useSuggestions.ts`) —
  ranks candidate Pokémon to add or swap in, by a composite score:
  `offensive_gain − 0.5×new_weaknesses − 1.0×aggravated_shared_weaknesses`.
  The same formula, with the same weights, drives the "Surprise Me" team
  generator (`src/hooks/teamGenerator.ts`), so a generated team and a
  suggestion-panel recommendation are scored the same way.

React only adapts these: `useCoverageAnalysis`/`useSuggestions` memoise
calls into the pure engines and expose the results to components. No
coverage or suggestion math lives in a component or a hook that isn't one
of these two adapters.

## Screens

No router — `AppView` (`src/types/index.ts`) is a small discriminated
union (`teams` / `team` (with a `pokemon`/`analysis` tab) / `custompkmn` /
`settings`) held in `App.tsx`'s state, switched with plain conditional
JSX. The five screens:

- **Teams** — grid of saved teams; create empty, import from Showdown, or
  hand off to "Surprise Me".
- **Team → Pokémon tab** — six slots, each a species picker, per-slot type
  override, ability picker, and (if the "Enable move slots" toggle is on)
  four move slots.
- **Team → Analysis tab** — coverage-basis notice, per-Pokémon defensive
  breakdown, offensive/defensive 18×18 grids, shared weaknesses, and the
  suggestion panel.
- **Custom Pokémon** — a personal roster of hand-built Pokémon (no PokéAPI
  entry required), reusable across teams.
- **Settings** — suggestion-engine filters, theme/language, and the Data
  section described above.

## Android vs. web presentation

Business logic (hooks, pure engines, state) is written once and shared.
Only *presentation* diverges, through a build-time file convention: a
component's default file (`Component.tsx`) is both the shared-logic owner
and the web (Tailwind) presentation; an optional sibling
`Component.android.tsx` holds the Android (MUI) presentation, picked up
automatically by a Vite plugin (`vite-plugins/androidPlatformResolve.ts`)
only when building with `--mode android`. The PWA build never sees MUI or
Emotion code — verified by grepping the built `dist/` output. See
`CLAUDE.md` → "Android Platform" for the full mechanics and the current
list of restyled screens.

## Import/export

`src/utils/showdownParser.ts` reads and writes
[Pokémon Showdown](https://pokemonshowdown.com)-style team text: species,
ability, moves, and a `# Types: ...` comment carrying any ROM-hack type
override (a Showdown concept doesn't otherwise have a slot for this). The
parser is pure — species/move lookups are injected as callbacks — so it
never touches `localStorage` or the network itself; `App.tsx`/
`useAppShell.ts` wires it to the live dataset and user data.

## Where to look next

- `CLAUDE.md` — module-by-module invariants, "what not to change without
  discussion," and the Android platform mechanics in detail.
- `docs/android/BUILD.md` — local Android build steps, signing, Firebase
  App Distribution setup, CI job structure.
- `README.md` — end-user-facing feature list and setup instructions.
- `docs/STATUS.md` — current implementation snapshot and known gaps,
  meant as a starting point for the next work session.
