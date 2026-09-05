# Post-migration review — coverage and suggestions

A code-level audit of the shipped native app against
[`docs/plan/native-spec.md`](plan/native-spec.md) and the phase plans, run
after Phase 6 closed. The focus, as requested, is the coverage engine and
the suggestion/generator path; the other subsystems were swept for
concrete defects rather than re-reviewed line by line.

**Method.** `legacy-web/` was deleted in `3e11ea8`, but it survives in git
history, so every Kotlin port was diffed against its TypeScript original
recovered with `git show 3e11ea8^:legacy-web/...`. That is the same oracle
Phase 6 used, applied a second time and independently.

**What was not done.** This sandbox has no Android SDK (`ANDROID_HOME` is
empty), so nothing here was compiled or run. Every claim below is either a
source-level reading, a diff against the recovered TypeScript, or a
standalone JVM reproduction that is named as such. No wall-clock figure was
measured on a device.

## Verdict

The engines are ported **faithfully**. `CoverageEngine.kt`,
`AbilityEffects.kt`, `Scoring.kt` and `SuggestionEngine.kt` match their
TypeScript originals function for function, including the tie-break order,
the `0.5`/`1.0` weights, and the deliberate `generationIntroduced`
deviation. The 78 domain unit tests are genuinely thorough. Nothing in the
maths is wrong.

The defects are all **at the seams**: one port that silently changed a
sort into a non-deterministic one, two engines invoked on the main thread,
a shipped stepper that placed nothing, redundant per-candidate rework, and
one place where ability was honoured on one side of a screen and ignored
on the other. Most of these are not reachable by the existing unit tests,
for a structural reason worth stating plainly: the test pools are 5–20
entries, the production pool is several hundred, and no test asserted
anything about threading before this review added one.

Separately: **0 of 59 items in [`docs/test-plan.md`](test-plan.md) were
ticked** at the time of this review. Every phase is marked ✅ in
`CLAUDE.md` on the strength of CI and unit tests alone. Findings 1, 2 and
5 below are exactly the kind a single pass on a real device would have
caught immediately — that gap has not been closed by this review or the
fixes that followed it; it is still a real gap.

**All six findings below have since been fixed**, each in its own commit
on top of this review, including one correction (finding 4, see below) to
a claim in the original version of this document that turned out to be
wrong before it was acted on. The findings are kept below as written at
the time, not rewritten to look correct in hindsight, because the record
of what was found and how it was fixed is more useful than a document that
only ever describes a codebase with no known problems in it.

## Findings

### 1. `regenerateSlot` can crash, and its "top 5" is not the top 5

`domain/generator/TeamGenerator.kt:228`

```kotlin
val scored = candidatePool
    .map { entry -> entry to memberFromEntry(entry) }
    .sortedByDescending { (_, member) -> computeScore(chart, member, otherMembers, random) }
```

`computeScore` adds fresh random noise on every call
(`TeamGenerator.kt:88`). Kotlin's `sortedByDescending` expands to
`sortedWith(compareByDescending(selector))`, and `compareByDescending`
invokes the selector **once per comparison per operand** — it does not
memoize. Two consequences:

- **The comparator is non-deterministic**, so it violates
  `Comparator`'s transitivity contract. `sortedWith` routes to
  `java.util.Collections.sort` → `Arrays.sort` → TimSort, which detects
  broken merge invariants and throws
  `IllegalArgumentException: Comparison method violates its general
  contract!`. `SurpriseMeViewModel.regenerateSlot` is called straight from
  a Compose `onClick` with nothing catching it, so this is an app crash on
  the per-slot reroll button.
- **The ordering is meaningless even when it doesn't throw**, so
  `scored[random.nextInt(topN)]` picks from an arbitrary five, not the five
  best.

Composite scores land on a 0.5 lattice while the noise is ±0.01, so the
noise *only* ever reorders exact ties — within a tie group the comparator
is a coin flip, which is the worst case for TimSort. A standalone JVM
reproduction of that distribution (JDK 21; Android's libcore uses the same
TimSort) throws at these rates:

| pool size | distinct scores = 1 | = 4 | = 20 |
|---|---|---|---|
| 32 | 0.6% | 0.6% | 0% |
| 128 | 5.4% | 4.4% | 0.4% |
| 800 | 10.6% | 6.0% | 2.2% |

The production pool after `buildEligiblePool` is in the high hundreds
(568 final-evolution *species*, plus the alternate forms of those species —
see [`reference-pokedata.md`](plan/reference-pokedata.md) §"final-evolution
derivation"). The existing tests pass because their pools are under
TimSort's `MIN_MERGE = 32` threshold, below which it uses binary insertion
sort and never checks the contract.

This is also a **deviation from the original**, not an inherited bug:
`teamGenerator.ts` scored each candidate exactly once into a `{entry,
member, score}` triple and sorted on the stored `score`. Scoring on the
fly was introduced by the port.

`generateTeam:172` uses `maxByOrNull`, which calls the selector once per
element, and is therefore correct.

**Fix.** Materialize the score, then sort:

```kotlin
val scored = candidatePool
    .map { entry -> Triple(entry, memberFromEntry(entry), computeScore(chart, memberFromEntry(entry), otherMembers, random)) }
    .sortedByDescending { it.third }
```

Also drops the cost from O(n log n) scorings to O(n).

**Test.** A `regenerateSlot` case with a pool of ≥100 entries whose
composite scores mostly tie, run in a loop; it fails today and passes after.

### 2. Both engines run on the main thread

- `ui/team/analysis/AnalysisViewModel.kt:84-130` — `analyseTeam`,
  `sharedWeaknessCounts` and `computeSuggestions` all run inside the
  `combine` transform. `stateIn(scope = viewModelScope, …)` collects on
  that scope's context, which is `SupervisorJob() + Dispatchers.Main.immediate`.
- `ui/surprise/SurpriseMeViewModel.kt:104,113` — `generate()` and
  `regenerateSlot()` are plain non-suspend functions invoked directly from
  Compose click handlers.

There is no `withContext`, `flowOn` or `Dispatchers.Default` anywhere under
`ui/`.

Order of magnitude, per invocation, for a full team of six and a pool of
N ≈ 800:

- **Suggestions, replacement mode** — per candidate, 6 leave-one-out
  passes, each ≈ 432 type-chart lookups and ~12 `Set` allocations →
  **≈ 2.3M lookups and ~65k allocations per emission**, re-run on every
  team edit, toggle flip and generation-filter change.
- **`generateTeam`** — 6 slots × N candidates × a full team-coverage
  recomputation each, in one synchronous click handler with no progress
  indicator.

Wall-clock on a device is unmeasured, but this is well past the 16 ms frame
budget and the generator is a plausible ANR on a low-end phone.

**Fix.** Make the two `SurpriseMeViewModel` entry points `suspend` bodies
launched on `Dispatchers.Default` with an `isGenerating` flag driving a
progress indicator, and add `.flowOn(Dispatchers.Default)` to the
`AnalysisViewModel` pipeline (plus a debounce on the filter flows).
`CoroutinesModule` already exists as the place to inject the dispatcher,
which also makes it swappable in tests.

### 3. 83% of the suggestion work is recomputed per candidate

`domain/suggestion/Scoring.kt:52-70`. `computeCompositeScore` recomputes
`baseCov` and `otherWeaknessMap` from `otherMembers` on every call — but
those depend only on the team, not on the candidate, so they are identical
across all N candidates. In replacement mode there are just six distinct
`otherMembers` sets; today they are rebuilt N × 6 times instead of 6.

That is ~360 of the ~432 lookups per candidate-pass.

**Fix.** Hoist the per-team part into a precomputed context passed into the
scorer, computed once (addition mode) or six times (replacement mode).
Roughly a 5× reduction, and it composes with finding 2 rather than
replacing it. Behaviour-preserving: the existing
`SuggestionEngineTest`/`TeamGeneratorTest` suites are the regression net.

### 4. The Suggestions panel — corrected after further review

**This finding was substantially wrong as first written, and the record
needs to say so plainly rather than quietly fixing it.** The original
version of this section, based on a diff against
`SuggestionPanel.android.tsx`/`SuggestionFilters.android.tsx`, claimed the
native Suggestions panel was "missing" the PWA's type-filter chips,
"Best coverage"/"Random" mode toggle, and a 10-card cut (native shows 5).
Before implementing any of that, `docs/plan/native-spec.md`'s own
"Suggestion engine" section turned up and settles it:

> **Team size < 6 — addition mode.** [...] Return the top 5 by `gain`.
> **Team size = 6 — replacement mode.** [...] Return the top 5 by `gain`,
> keyed by species name so a species cannot appear twice.

Five cards is the spec, not a shortfall against it — the PWA's `slice(0,
10)` is exactly the kind of legacy-web behavior this rewrite was never
bound to reproduce. Neither the type filter nor the best/random toggle
appears anywhere in `native-spec.md` either, and
`ui/team/analysis/SuggestionFilters.kt` already carries a doc comment
saying as much: "Deliberately smaller than `legacy-web`'s own
`SuggestionFilters.tsx` [...]: neither is in this app's UI spec." That
comment was sitting in the file this review diffed against and should
have been read before writing the original table above — it wasn't. So:
**no type filter, no random mode, no 10-card cut** — implementing any of
them would be reversing a documented Phase 4 decision, not restoring a
migration defect.

What survives the correction, both narrow and independent of the retracted
items:

- **`suggestions.solidCoverage`** ("Your team coverage is solid. Showing
  alternatives:") — shown by the PWA when every displayed card has
  `gain == 0`. This doesn't depend on the type filter or the cut count;
  it's a small, orthogonal contextual message a team with complete
  offensive coverage would otherwise lack, seeing only a stack of
  zero-gain cards with no framing. Legitimate, optional, low-risk to add.
- **`suggestions_exclude_legendaries`** is a genuinely orphaned string
  resource — defined in both locale files, referenced by no composable
  (the real toggle lives in Settings under the inverted
  `settings_include_legendaries` framing). Unrelated to the rest of this
  finding; worth deleting on its own merits.

**Fix.** Add the solid-coverage message; delete the orphan string. Nothing
else from the original table.

### 5. The "Custom slots" stepper does nothing

`ui/surprise/SurpriseMeScreen.kt:156-159` renders a stepper bound to
`GeneratorConstraints.customSlots`. `TeamGenerator.kt` never reads that
field — the port deliberately dropped the `customs` parameter because it
was dead in `teamGenerator.ts` too (documented in
`implementation-decisions.md`, "Phase 4"), and `customSlots` was kept only
"as a ported struct field".

But `SurpriseMeUiState.constraintTotal` counts it, so the stepper actively
consumes the six-slot budget (`remainingSlots`, `budgetFull`) and can block
the user from allocating starter/legendary slots — in exchange for zero
custom Pokémon ever being placed. `SurpriseMeViewModel` does load
`customs` into its UI state and then never uses it.

This is shipped UI that lies to the user, which is worse than the dead
parameter it came from.

**Fix.** Either implement it — reserve N slots filled from
`CustomPokemonRepository.roster`, scored the same way — or remove the
stepper and the `customSlots` field. Implementing is the better call: it
is the one generator feature that serves this app's stated ROM-hack/draft
audience, and the roster is already in the ViewModel.

### 6. Abilities are honoured in the grid but ignored when scoring

`Scoring.kt:24` computes `weaknesses` with no ability argument, faithfully
porting `getWeaknesses`. `sharedWeaknessCounts`
(`CoverageEngine.kt:161`) does pass `m.ability`. Both feed the same
Analysis screen, so it can simultaneously report that the team is *not*
weak to Ground (Levitate) and penalise a Ground-weak candidate for
"aggravating" that weakness.

The candidate side compounds it: `memberFromEntry` sets `ability = null`,
but `applySuggestion` writes the species' `defaultAbility` — so a suggested
Pokémon is scored without the ability it is about to be given.

Inherited from the PWA, so not a migration regression, but it is a genuine
inaccuracy and the two halves of one screen disagreeing is the visible
symptom.

**Fix.** Pass the ability through `weaknesses()`, and give `memberFromEntry`
the entry's `defaultAbility`. This *changes scores* — it is a spec change,
not a refactor, so it needs an `implementation-decisions.md` entry and
updated test expectations in the same commit. Worth doing; do it on its own.

## Plan

Ordered by risk removed per unit of work. **All six items below are now
done** — this section is kept as the reasoning behind the order they were
done in, not a to-do list.

**Now — correctness**

1. ✅ Finding 1: memoize the score in `regenerateSlot`, plus the large-pool
   regression test. One-line fix, removes a crash.
2. ✅ Finding 5: decide and act on `customSlots` — implement, or remove the
   stepper. Currently misleading either way. (Implemented.)

**Next — responsiveness**

3. ✅ Finding 2: move both engines off the main thread; add the generator's
   progress indicator.
4. ✅ Finding 3: hoist the per-team precomputation out of the candidate loop.

Doing 4 before 3 is tempting and wrong: getting the work off the main
thread is what fixes the jank, and the optimisation is then a bonus rather
than load-bearing.

**Then — parity and accuracy**

5. ✅ Finding 4 (corrected): add the solid-coverage message; clear the
   orphan string. The type filter, random mode and 10-card cut are **not**
   part of this — see the correction in finding 4 itself.
6. ✅ Finding 6: thread abilities through the scoring path, as a documented
   spec change.

**Underneath all of it**

7. ⬜ Work `docs/test-plan.md` on a real device. 59 unchecked items is the
   single largest gap in confidence in this repository, and findings 1, 2
   and 5 are all things a first run-through would have surfaced. **Still
   not done** — nothing in this review or its fixes ran on a device or an
   emulator; see "What was not done" above and the same caveat repeated on
   every fix commit.
8. ✅ Add the two test shapes that would have caught these: an engine test
   at production pool scale (several hundred entries — `TeamGeneratorTest`'s
   `largeTiedScorePool`), and a ViewModel test asserting the engines are
   not invoked on the collecting dispatcher
   (`AnalysisViewModelTest`'s `coverage is computed without ever advancing
   the Main test dispatcher`). A second such test on `SurpriseMeViewModel`
   was written and then removed — CI caught it as flaky, not the
   production code: it asserted `isGenerating.value` synchronously right
   after calling `generate()`, assuming the launched coroutine couldn't
   have finished yet, but `Dispatchers.Default` is a real thread pool the
   test's `StandardTestDispatcher` does not gate, and the tiny mock pool
   in that test file finishes fast enough to occasionally (in CI,
   consistently) flip `isGenerating` back to `false` before the very next
   line ran. Removed rather than patched: there is no reliable way to
   observe that transient state from outside without adding an injectable
   dispatcher for the background work, which finding 2's own decision
   record (`implementation-decisions.md`) already explains was considered
   and rejected. The behaviour it was trying to test (the coroutine
   eventually finishes and updates state) is still covered by
   `generate fills the result from the pool and keeps a locked anchor
   first`.

## Areas swept, no defects found

Ported maths (`CoverageEngine.kt`, `AbilityEffects.kt`, `Scoring.kt`,
`SuggestionEngine.kt`) against the recovered TypeScript; the suggestion
ranking comparator; `analyseTeam`'s mixed-mode handling; `generateTeam`'s
quota logic; `applySuggestion`'s slot resolution (custom roster entries
always carry `pokedexId = null`, so a custom's ability is not overwritten);
the type chart's 18×18 completeness.
