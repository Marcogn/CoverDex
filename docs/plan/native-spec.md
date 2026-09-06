# Native CoverDex — functional specification

The authoritative description of what the native Android app must do.
Anything not in this file, or in a phase plan, is out of scope.

The app being specified is a **port**, not a redesign. The behavioural
source of truth is `legacy-web/` (this repo's React code, parked in Phase 0
and deleted in Phase 6) plus its 175 Vitest tests. This file exists so an
agent does not have to reverse-engineer intent from that code.

## Identity

| | |
|---|---|
| Repository | `marcogn/coverdex` |
| Gradle root project | `CoverDex` |
| `applicationId` / package | `com.marcogn.coverdex` |
| App label | CoverDex |
| Database file | `coverdex.db` |
| minSdk / targetSdk / compileSdk | 24 / 36 / 36 |

`applicationId` is **unchanged** from the Capacitor build — the app keeps its
identity on any device that already has it installed. `minSdk` stays at 24
(the Capacitor project's value) rather than moving to Hall of Memories' 26:
nothing in this app needs `java.time`, so there is no reason to drop API
24–25 devices. If a later phase does need `java.time`, raise minSdk to 26
rather than adding desugaring, and say so in the changelog.

## What this app is

A tool for building Pokémon teams and analysing their type coverage,
offensively and defensively. It supports custom Pokémon and per-slot type
overrides, which is what makes it useful for ROM hack runs and draft
building.

It is **not** a battle simulator, **not** a Pokémon Showdown replacement,
and **not** a damage calculator.

Single user, offline-first, no account, no backend, no telemetry. The
network is used for exactly two things: the one-off dataset sync
(§ "Dataset") and sprite image URLs.

## Screens

Five destinations, reached from a `ModalNavigationDrawer` wrapped around the
`NavHost` (Hall of Memories' navigation shape). The PWA's hand-rolled
`AppView` state machine does not survive the port — use type-safe
Navigation-Compose routes.

1. **Teams** — the list of saved teams. Create, rename, delete, open. Each
   row shows the team name and its six slots as sprites (empty slots as
   placeholders).
2. **Team detail** — two tabs for one team:
   - **Pokémon**: the six slots. Pick a species, override its two types,
     set an ability, and — when "Enable move slots" is on — up to four
     moves per slot. Save a slot's Pokémon to the custom roster.
   - **Analysis**: seven sections, in this order and no other:
     coverage-basis notice, per-Pokémon breakdown, offensive grid,
     defensive grid, shared weaknesses, uncovered types, suggestions.
3. **Custom roster** — the user's own Pokémon: name, two types, optional
   ability, optional moves. Usable in any team slot and, behind a toggle,
   as suggestion candidates.
4. **Surprise Me** — the team generator, reached from Teams. Optional
   anchor Pokémon, constraints, generate, regenerate a single slot, keep.
5. **Settings** — theme, language, "include Mega/Dynamax forms", "include
   legendaries in suggestions", dataset status and re-sync, Showdown
   import/export, local backup, app version.

## Domain rules

These are invariants, not preferences. They are carried over verbatim from
the PWA's `CLAUDE.md` and are covered by tests that must be ported.

### Type chart

- An 18 × 18 matrix keyed attacker type → defender type, values in
  `{0.0, 0.5, 1.0, 2.0}`.
- Dual-type defense **multiplies** the two effectiveness values, never adds
  them. 2× on top of 2× is 4×; 2× against a 0× immunity is 0×.
- Per-slot type overrides apply **only to that slot**. They must never be
  written back into the cached type chart or the cached species entry.

### Offensive coverage

- Per-member offensive coverage uses the member's **moves** when the member
  has at least one damaging move (`damageClass != status && (power ?? 0) > 0`);
  otherwise it falls back to the member's own **types**.
- When computing offense from moves, use the **move's** type, not the
  Pokémon's. Same-type attack bonus is not modelled.
- The team-detail screen additionally gates this on the "Enable move slots"
  toggle: with the toggle off, members reach the coverage and suggestion
  engines with `moves` cleared, so analysis is type-only regardless of what
  is stored.
- Suggestion **candidates** are always evaluated by types only. Never invent
  a movepool for a candidate.

### Suggestion engine

1. Filter the pool to `isFinalEvolution` entries. Mid-evolutions are never
   offered. Append custom Pokémon when the `includeCustoms` flag is on.
2. Compute each candidate's offensive coverage from its types only.
3. **Team size < 6 — addition mode.** A candidate's `gain` is the number of
   types it covers that the team does not. Return the top 5 by `gain`.
4. **Team size = 6 — replacement mode.** Each member's
   `unique_contribution` is the set of types only it covers; the member with
   the smallest one is the *weakest link*. Compute the team's base coverage
   without it, then `gain(R) = |base ∪ coverage(R)| − |team coverage|` for
   every candidate. Return the top 5 by `gain`, keyed by species name so a
   species cannot appear twice.
5. Legendaries are **not** excluded by default. When the user turns
   exclusion on, the rule is: legendaries and mythicals are excluded unless
   the active team already contains one.
6. Composite score = `offensive_gain − 0.5 × new_weaknesses
   − 1.0 × aggravated_shared_weaknesses`. **The 0.5 and 1.0 weights are
   load-bearing** and are shared with the team generator. Do not change
   them; changing them means updating this file, `CLAUDE.md` and the tests
   in the same commit.

### Team generator ("Surprise Me")

- Uses the same composite-score weights as the suggestion engine.
- `STARTER_FINALS` is a hardcoded per-generation list. Adding a generation
  means editing it. Port it verbatim from
  `legacy-web/src/hooks/teamGenerator.ts`.
- Anchor handling, constraint handling and single-slot regeneration are
  specified by `legacy-web/src/hooks/__tests__/teamGenerator.test.ts`,
  including the probabilistic "anchor composite score validation" case.

### Showdown format contract

External users may rely on round-tripping. The contract below is
unchanged from the PWA and is defined precisely in
`legacy-web/src/utils/showdownParser.ts` and its test file.

**Read on import**: species name (first non-comment line, split on `@`);
`Ability: <name>`; move lines starting with `- `; a type-override comment
`# Types: type1[/type2]`, case-insensitive, ignored if either type is not a
valid type. Lines matching `EVs:`, `IVs:` or `Nature` are ignored, as are
other `#` comments.

**Written on export**: species name followed by `@ `; an `Ability: <value>`
line; `EVs: ` and ` Nature` placeholder lines; one `- <move name>` line per
non-null move slot; a trailing `# Types: ...` comment preserving the slot's
types.

**Unknown moves on import**: build a placeholder move with `isCustom = true`,
`damageClass = status`, `power = null` and the original name; append the
name to the returned unknown-move list so the caller can flag the slot. The
block is still imported.

### Ability effects

`legacy-web/src/data/abilityEffects.ts` maps ability slugs to
coverage-relevant effects (type immunities, multipliers, `wonder-guard` as
a badge). Port the map **verbatim**. Do not add or remove entries without
updating this file and the tests.

### Searchable dropdowns

Every searchable picker — species, move, ability, generator anchor — behaves
the same way:

- On focus/open, show **no** list items.
- Show a "Start typing to search…" placeholder inside the input.
- Show results only from the first typed character.
- Show **all** matches: no pagination, no item cap.
- The list scrolls internally rather than growing the page.

## Dataset

The species/move/ability/type catalogue is downloaded once and cached in
Room. The full contract — sources, sizes, field derivations, sprite URLs,
pinning and invalidation — is
[`reference-pokedata.md`](reference-pokedata.md), and it is not repeated
here. What matters at the spec level:

- The sync is **~12 requests and ~565 KB**, and is fast enough that it does
  not gate the UI behind a full-screen loader the way the PWA does. Show
  progress inline; let the user reach Settings while it runs.
- Sprite URLs are **derived, never stored**, from the Pokémon's id.
- The cache is re-downloadable data. Wiping it must never touch teams,
  the custom roster or settings, and must name the cache tables explicitly.
- Restoring a backup is a **full replace** of user data in a single
  transaction, ids and timestamps preserved. No merging, no conflict
  resolution. The cache is not part of a backup.

## Storage

**Room is the single source of truth** for teams and the custom roster,
exposed as `Flow`. Settings live in a Preferences DataStore. ViewModels
`combine()` repository flows with local UI state into one `StateFlow` of UI
state; events flow up as lambdas, state flows down.

Existing Capacitor installs are a **clean break**: the native app does not
read the old `CapacitorStorage` SharedPreferences, and users upgrading in
place start with an empty team list. This was a deliberate decision — see
`docs/implementation-decisions.md`. Phase 6's release notes must say so
plainly and tell users to export their teams to Showdown format before
updating.

## Localisation

Italian is the default locale, English the alternative — the same split as
Hall of Memories, and the reverse of `legacy-web`'s i18next default. Both
`res/values/strings.xml` (Italian) and `res/values-en/strings.xml` (English)
are updated in the same commit, always. Pokémon, move and ability names are
**never** translated.

The in-app language picker uses `AppCompatDelegate.setApplicationLocales()`,
which requires `MainActivity` to extend `AppCompatActivity`. See Phase 0.

## Explicitly out of scope

- **Any web build.** GitHub Pages, the PWA manifest, the service worker,
  Vite, Capacitor and every workflow that publishes them are deleted.
- **A backend of any kind.** Proposed and turned down twice already: the
  coverage maths is microseconds at this data scale, and a backend would
  fork the business logic. Do not reopen without a measured, reproduced
  performance problem.
- **Play Store submission.** The release path is a signed APK attached to a
  GitHub Release. Sideload only.
- **iOS.**
- **Migrating data from the Capacitor build.** Decided against; see
  "Storage".
- **Damage calculation, battle simulation, EV/IV tracking, legality
  validation.** Not what this app is.
