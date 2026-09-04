# Phase 3 — Coverage analysis

**Goal:** the Analysis tab works. The coverage engine is ported to Kotlin
with its behaviour proven identical to the TypeScript original.

**Depends on:** Phase 2.

**Read first:** `native-spec.md` → "Domain rules", and the source files this
phase ports:

```
legacy-web/src/utils/coverageEngine.ts              149 lines
legacy-web/src/data/abilityEffects.ts                75 lines
legacy-web/src/utils/__tests__/coverageEngine.test.ts
legacy-web/src/components/__tests__/analysisPage.integration.test.tsx
legacy-web/src/components/__tests__/analysisShowMoves.test.tsx
```

---

## 1. The engine — `domain/coverage/`

A direct port. Same function names, same signatures, same order of
operations. Resist the urge to make it more idiomatic than the original: the
value of this port is that a reviewer can put the two files side by side.

```kotlin
fun defensiveMultiplier(
    chart: TypeChart,
    attacking: PokemonType,
    defenderTypes: Pair<PokemonType, PokemonType?>,
    ability: String? = null,
): Double

fun offensiveCoverageForMember(chart: TypeChart, m: TeamMember): Set<PokemonType>
fun memberHasMoves(m: TeamMember): Boolean

data class TeamCoverage(...)                       // mirror the TS interface field for field
fun analyseTeam(chart: TypeChart, members: List<TeamMember>): TeamCoverage

data class DefensiveProfile(...)
fun defensiveProfile(
    chart: TypeChart,
    types: Pair<PokemonType, PokemonType?>,
    ability: String? = null,
): DefensiveProfile

fun sharedWeaknesses(chart: TypeChart, members: List<TeamMember>): List<PokemonType>
```

The three rules that the tests exist to protect:

1. **Dual-type defense multiplies.** 2× × 2× = 4×; 2× × 0× = 0×. Never
   additive. The immunity case is the one people get wrong.
2. **`memberHasMoves` is `damageClass != STATUS && (power ?: 0) > 0`** on at
   least one move. 78 of 937 real moves are non-status with null power, so
   this is not equivalent to "has any move".
3. **Offense from a move uses the move's type**, not the Pokémon's. No STAB.

`domain/ability/AbilityEffects.kt` ports `abilityEffects.ts` **verbatim** —
the same slugs, the same immunity/multiplier/badge-only kinds, the same
`KNOWN_ABILITIES_WITH_EFFECTS` display list. Adding or removing an entry is
a spec change, not an implementation detail.

## 2. The screen — `ui/team/analysis/`

Seven sections, **in this order and no other**. The order is part of the
spec; a reader scanning down the screen is following an argument.

1. **Coverage basis notice** — says whether the analysis is running off
   moves or off types, and why. This is the section that makes the rest
   legible; do not drop it as decoration.
2. **Per-Pokémon breakdown** — one card per member: sprite, types, ability,
   what it covers offensively, what it is weak to.
3. **Offensive grid** — 18 columns, team members as rows, plus the union row.
4. **Defensive grid** — 18 columns, members as rows, multipliers as cells.
5. **Shared weaknesses** — types two or more members are weak to.
6. **Uncovered types** — types no member hits for super-effective damage.
7. **Suggestions** — a placeholder in this phase; Phase 4 fills it.

Both grids are wide. Each goes in its own horizontally scrolling container
with the member column pinned; the screen itself must never scroll
sideways.

`AnalysisViewModel` `combine()`s the team flow, the type chart, the roster
and the local toggles (`showMoves`, `includeCustomsAnalysis`, the generation
filter) into one `StateFlow<AnalysisUiState>`.

**The `showMoves` gate belongs in the ViewModel, not the engine.** When the
toggle is off, members reach `analyseTeam` with their moves cleared, so the
engine uniformly falls back to type-based coverage. This is exactly what
`TeamDetailPage.tsx` does with its `analysisMembers` memo, and
`analysisShowMoves.test.tsx` is the test that pins it.

## Deliverables

- [ ] `domain/coverage/CoverageEngine.kt` — all seven public functions.
- [ ] `domain/ability/AbilityEffects.kt` — verbatim port.
- [ ] `AnalysisViewModel` with the `showMoves` gate.
- [ ] The seven sections, in order, both grids scrolling independently.
- [ ] Strings in **both** locales, ported from `legacy-web`'s i18n files.
- [ ] `CHANGELOG.md`, `docs/test-plan.md`, `docs/implementation-decisions.md`.

## Tests

**Port `coverageEngine.test.ts` case by case.** Every `expect` in the
TypeScript becomes an assertion in `CoverageEngineTest` with the **same
expected value**, ported rather than re-derived. Where the TS builds a
fixture team from `testFixtures.ts`, build the same team in Kotlin. If a
Kotlin assertion disagrees with its TypeScript twin, the TypeScript is right
and the Kotlin has a bug — that is the entire point of doing it this way.

Beyond the ported cases, assert explicitly:

- `defensiveMultiplier` for a 4× case, a 0.25× case, and the
  immunity-cancels-weakness case.
- `memberHasMoves` is false for a member holding only status moves, false
  for a member holding only null-power physical moves (an OHKO move), true
  with one real damaging move.
- An ability immunity zeroes the relevant multiplier;
  `wonder-guard` is badge-only and changes no number.
- `sharedWeaknesses` returns types shared by ≥ 2 members and nothing else.

`AbilityEffectsTest` — every entry in the map round-trips, and the map's
key set matches `legacy-web`'s exactly (assert the count and the sorted key
list, so a dropped entry fails loudly).

`AnalysisViewModelTest` (Robolectric, `@Config(sdk = [26])`) — with
`showMoves` off, a member holding damaging moves still produces
type-based coverage; with it on, move-based.

## Not in this phase

Suggestions (the section is a placeholder), the generator, import/export.

## Notes

> `legacy-web/src/components/CoverageGrid/CoverageGrid.tsx` line 333 repeats
> the damaging-move filter inline rather than calling `memberHasMoves`. Port
> it as a call to the shared function; if that changes a result, it is a
> latent bug in the PWA and belongs in `docs/implementation-decisions.md`
> with the case that exposed it.
