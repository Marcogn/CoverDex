# Phase 4 — Suggestions and the Surprise Me generator

**Goal:** the Suggestions section works and the team generator ships. The
two share a scoring function and must keep sharing it.

**Depends on:** Phase 3.

**Read first:** `native-spec.md` → "Suggestion engine" and "Team generator",
and the files this phase ports:

```
legacy-web/src/hooks/suggestionEngine.ts             308 lines
legacy-web/src/hooks/teamGenerator.ts                362 lines
legacy-web/src/hooks/useSuggestions.ts                26 lines
legacy-web/src/hooks/__tests__/suggestionRanking.test.ts
legacy-web/src/hooks/__tests__/useSuggestions.test.ts
legacy-web/src/hooks/__tests__/useSuggestionsHook.test.ts
legacy-web/src/hooks/__tests__/compositeScoring.test.ts
legacy-web/src/hooks/__tests__/addToTeam.test.ts
legacy-web/src/hooks/__tests__/teamGenerator.test.ts
legacy-web/src/hooks/__tests__/surpriseMe.test.ts
```

---

## 1. The suggestion engine — `domain/suggestion/`

A direct port of `computeSuggestions`, `memberFromEntry` and the `Suggestion`
/ `SuggestionOptions` shapes. Same field names, same ranking, same top-5 cut.

The algorithm, restated so you can check the port against it without
re-reading the TypeScript:

1. Filter the pool to `isFinalEvolution`. Append the custom roster when
   `includeCustoms` is on.
2. Apply the generation filter — **see §2, this is the one intentional
   change in the plan**.
3. Apply the legendary filter: when `excludeLegendaries` is on, drop
   legendaries and mythicals *unless the active team already contains one*.
4. Drop candidates already on the team, matched on lowercased species name.
5. Team of fewer than 6 → **addition mode**: `gain` is the count of types the
   candidate covers that the team does not. Top 5 by `gain`.
6. Team of exactly 6 → **replacement mode**: find the weakest link (smallest
   unique contribution), compute base coverage without it, then
   `gain(R) = |base ∪ coverage(R)| − |team coverage|`. Top 5 by `gain`,
   deduplicated by species name.
7. Candidates are evaluated **by types only**, always. Never invent a
   movepool for a candidate.

Composite score:

```
score = offensive_gain − 0.5 × new_weaknesses − 1.0 × aggravated_shared_weaknesses
```

**The 0.5 and 1.0 are load-bearing and shared with the generator.** Put them
in one place — `domain/suggestion/Scoring.kt` — and have the generator call
it. Changing either weight means updating `native-spec.md`, `CLAUDE.md` and
the tests in the same commit.

## 2. The generation filter — the one intentional behaviour change

`suggestionEngine.ts` filters by hardcoded **id ranges** (`GEN_RANGES`).
That is wrong for the 327 alternate forms with id > 10000: every one of them
lands in the `'9'` bucket, so Mega Charizard X is offered as a Generation IX
candidate and never as a Generation I one.

Phase 1 stores the real `generationIntroduced` from
`pokemon_species.generation_id`. **Use it. Delete `GEN_RANGES`.** Full
reasoning in `reference-pokedata.md` §4.

Consequences you must handle rather than discover:

- The ported `suggestionRanking.test.ts` cases that exercise the generation
  filter will produce different sets. **Do not weaken the assertions to make
  them pass** — update them to the new expected values and put a comment in
  the Kotlin test naming this change.
- `CHANGELOG.md` gets a user-facing bullet.
- `docs/implementation-decisions.md` gets the reasoning.
- `docs/test-plan.md` gets a manual step: filter suggestions to Generation
  I and confirm Mega forms of Generation I species now appear.

## 3. The generator — `domain/generator/`

Port `buildEligiblePool`, `generateTeam`, `regenerateSlot`,
`GeneratorConstraints`, `DEFAULT_CONSTRAINTS` and `STARTER_FINALS`.

`STARTER_FINALS` is a hardcoded per-generation list of species names. Port
it **verbatim** — it is data, and adding a generation later means editing
it, which is a known and accepted cost.

Randomness must be injectable: take a `Random` (defaulting to
`Random.Default`) as a parameter so tests can seed it. The TypeScript uses
`Math.random()` directly and its tests are probabilistic as a result; the
Kotlin should not have to be.

## 4. UI

### Suggestions section (`ui/team/analysis/`)

Fills section 7 of the Analysis screen. Up to five cards, each showing:
sprite, name, type badges, `gain`, composite score, newly covered types, new
weaknesses, and — in replacement mode — which member it would replace and
why that member is the weakest link. A tap adds or swaps it into the team.

`ui/team/analysis/SuggestionFilters.kt` carries the generation dropdown, the
"include custom Pokémon" toggle and the "exclude legendaries" toggle, wired
to `SuggestionOptions`.

### Surprise Me (`ui/surprise/`)

Reached from Teams. An optional anchor picker (the same searchable
dropdown), the constraint controls, a Generate button, per-slot regenerate,
and Keep — which writes the generated team through `TeamRepository`.

`legacy-web/src/components/SurpriseMe/SurpriseMeModal.tsx` is the
behavioural reference. Note that it is the one screen the PWA never
MUI-restyled for Android, so there is no Android-flavoured variant to copy
from; design it to match the rest of this app's Material 3 screens.

## Deliverables

- [ ] `domain/suggestion/` — engine + shared `Scoring.kt`.
- [ ] `domain/generator/` — generator with injectable `Random`.
- [ ] `GEN_RANGES` gone, `generationIntroduced` used, all four docs updated.
- [ ] Suggestions section with its filters.
- [ ] Surprise Me screen, generate / regenerate-slot / keep.
- [ ] Strings in **both** locales.
- [ ] `CHANGELOG.md`, `docs/test-plan.md`, `docs/implementation-decisions.md`.

## Tests

Port every case from the seven `legacy-web` test files listed above, with
the same expected values, except where the generation-filter change makes a
case obsolete — and there, update the expectation and comment why.

Assert additionally:

- Addition mode returns at most 5, ranked by `gain` descending.
- Replacement mode picks the member with the smallest unique contribution as
  the weakest link, and never returns two candidates of the same species.
- `excludeLegendaries` keeps legendaries in the pool when the team already
  holds one, and drops them when it does not.
- A mid-evolution is never suggested.
- **Composite scoring**: a seeded case where the highest raw `gain` is *not*
  the highest composite score, proving the penalties are applied. Port this
  from `compositeScoring.test.ts`.
- **`teamGenerator.test.ts`'s "anchor composite score validation"**: with
  Swampert (Water/Ground) as the anchor, the generated team holds no more
  than one additional Water type. The TS version runs it five times and
  accepts 4/5 because `Math.random()` is not injectable. **With a seeded
  `Random` the Kotlin version should assert deterministically** across a
  fixed set of seeds — a strictly better test. Say so in the test's comment.
- `STARTER_FINALS` has the same generations and the same members as the
  TypeScript (assert the whole structure, so a typo in a species name fails).

## Not in this phase

Showdown import/export, settings polish, backup, release.
