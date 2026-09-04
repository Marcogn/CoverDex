# Implementation decisions

Non-obvious choices made while executing the native Android migration plan
(`docs/plan/`), and why. Add an entry here whenever a phase requires a
judgment call the plan didn't already settle. One section per phase.

## Phase 0 — Foundation

- **`minSdk` stays 24, not Hall of Memories' 26.** The Capacitor build
  shipped `minSdk 24` and nothing in this app needs `java.time` (the only
  reason Hall of Memories requires 26 without desugaring). Raising it would
  drop real API 24–25 devices for no benefit. If a later phase genuinely
  needs `java.time`, the right fix is raising `minSdk` then, with a
  changelog note — not adding desugaring now against a need that doesn't
  exist yet.
- **`versionCode` starts at 2, `versionName` at "2.0.0".** The Capacitor
  build shipped `versionCode 1` / `versionName "1.0"`; the native app must
  be installable over it, so `versionCode` has to increase. The major
  version bump (`1.x` → `2.0.0`) is honest: this release drops the web app
  entirely and does not carry user data forward (see the next entry).
- **No data migration from the Capacitor build — decided, not deferred.**
  The old app persisted `teamdex_userdata` in `@capacitor/preferences`
  (native Android SharedPreferences under a Capacitor-specific key/format).
  Reading and converting that format into Room was considered and rejected
  in the planning session (see `docs/plan/native-spec.md`, "Storage"): the
  user explicitly chose a clean break over the added complexity and risk of
  a one-shot migration path that only ever runs once per device and is
  otherwise dead code forever after. Consequence: anyone upgrading in place
  loses their saved teams and custom roster silently unless warned first.
  Phase 6's release notes must lead with this, in plain language, with the
  concrete mitigation (export each team to Showdown format from the old
  app before updating) — not buried under feature bullets.
- **The launcher icon is a verbatim copy of the Capacitor build's mipmap
  set, not a re-laid-out vector.** The phase file's literal instruction was
  to "recover the foreground drawable and re-lay it out as
  `mipmap-anydpi-v26/ic_launcher.xml` + the density buckets" (implicitly
  assuming a vector source, as in Hall of Memories). Inspection showed the
  Capacitor asset is already a complete, correct Android launcher icon set
  — raster `ic_launcher_foreground.png`/`ic_launcher_background.png` per
  density (mdpi through xxxhdpi, 108dp), a legacy raster
  `ic_launcher.png`/`ic_launcher_round.png` fallback per density for
  pre-API-26 devices (needed here since `minSdk` is 24, unlike Hall of
  Memories' 26), and `mipmap-anydpi-v26/ic_launcher{,_round}.xml` using an
  inset foreground. Copying it unchanged is strictly more faithful to "the
  app's visual identity does not change in this rewrite" than reconstructing
  it as a hand-authored vector would have been, and it already handles the
  pre-26 fallback the vector approach doesn't. `./gradlew lintDebug`
  confirms it's a valid, complete adaptive icon (only a benign "missing
  monochrome tag" advisory, an Android-13+ nicety absent from the original
  Capacitor asset too — not a regression, not fixed here).
- **Settings' three-way language picker (System/Italian/English) has no
  PWA equivalent.** `legacy-web`'s i18next-based switcher
  (`src/components/Settings/SettingsPage.tsx`) exposes only two bare
  buttons, `EN`/`IT`, no spelled-out names, and no "follow the system
  locale" option — i18next has no concept of tracking the OS locale
  automatically the way `AppCompatDelegate.setApplicationLocales(empty)`
  does. Since Rule 4 ("port the text, do not reinvent it") has no PWA
  wording to port for this native-only feature, the three option labels
  ("Predefinito sistema"/"System default", "Italiano"/"Italian",
  "Inglese"/"English") are ported from Hall of Memories' Settings screen
  instead, which already solved the identical UI problem.
- **The theme picker's three labels ARE ported from `legacy-web`**, unlike
  the language picker above — `settings.systemDefault`/`light`/`dark`
  already exist in `legacy-web/src/i18n/locales/{it,en}.json` for exactly
  this picker (`SettingsPage.tsx`'s theme `<select>`), so those exact
  strings were used rather than Hall of Memories' shorter "Sistema"/"Tema".
- **Theme colour roles are seeded from the brand purple `#5B21B6`**, not a
  new palette. That hex is the Capacitor adaptive-icon background
  (`android:icons` npm script's `--iconBackgroundColor`) and the PWA's own
  accent color — it already sits almost exactly at Material 3's
  conventional "tone 40" lightness for a light-scheme primary color (42%
  lightness, measured), so it was used directly as `primary` rather than
  algorithmically adjusted. `primaryContainer`/`onPrimaryContainer` and the
  dark-scheme roles were derived by holding hue and saturation constant and
  shifting only lightness (a plain HSL adjustment, not a full Material
  color-utilities HCT computation — no such dependency is in the pinned
  catalogue and Phase 0 doesn't add one). Material You dynamic colour
  (API 31+) overrides all of this anyway; the manual scheme only matters on
  older devices and when the user has dynamic colour off.
- **`Destination.TeamDetail`'s route exists in the sealed interface but has
  no `composable<>` registered in the `NavHost` yet.** Nothing navigates to
  it until Phase 2 gives Teams real CRUD and TeamDetail a screen to show;
  registering a route with no reachable screen would mean inventing a
  placeholder Phase 0 doesn't need. The type's shape is what needs to be
  stable for Phase 2, not its wiring.
- **Verified locally with a temporary Android SDK, not committed.** The
  sandboxed session had no `ANDROID_HOME` by default (matching
  `docs/plan/README.md`'s documented default), but `dl.google.com` was
  reachable, so `cmdline-tools` + `platform-36` + `build-tools` were
  installed to `/tmp/android-sdk` (outside the repo, `.gitignore`d either
  way via `local.properties`) specifically to run `assembleDebug`,
  `lintDebug` and `testDebugUnitTest` for real rather than only trusting
  `android-ci.yml` to catch a problem. All three were green. This is not an
  assumption future sessions can rely on — check `$ANDROID_HOME` and
  `command -v sdkmanager` per `docs/plan/README.md` before assuming a local
  build is possible, and fall back to CI if it isn't.

## Phase 1 — Dataset sync

- **`isDefaultForm` was added to `PokemonEntry`**, mid-phase, once Room's
  schema (§2) turned out to need it (`poke_species.isDefaultForm`) for
  search ranking but the domain model didn't carry it yet. Sourced from
  `pokemon.csv`'s already-parsed `is_default` column. Keeping it on the
  domain model rather than only on the entity is what CLAUDE.md's
  architecture already commits to ("Room is the single source of truth...
  Pure logic lives in `domain/`") — an entity should never carry data the
  domain layer has nowhere to read from.
- **No `Converters.kt`.** Every field on every Phase 1 entity is a native
  Room type (`String`/`Int`/`Boolean`/`Double`) — a converter class with
  nothing to convert would be dead ceremony. Same judgment as Phase 0
  skipping an empty `di/NetworkModule.kt` — except this phase's
  `NetworkModule.kt` ended up with real content once `DatasetSource` (next
  entry) needed a `@Binds`.
- **`PokeDataClient` sits behind a `DatasetSource` interface.** No mocking
  library is in the pinned catalogue, and `DatasetSyncManager`'s tests
  (a failing fetch leaves the database untouched; a successful one writes
  every table; a fresh cache never touches the network at all) need to
  control what a fetch returns or whether it throws. A hand-written fake
  implementing a one-method interface does that without a new dependency;
  `PokeDataClient` is the only real implementation, bound in
  `di/NetworkModule.kt`.
- **The repository interface dropped `suspend fun sync(): Result<Unit>`**
  from the plan's original one-paragraph sketch, in favour of Hall of
  Memories' fire-and-forget `startSyncIfNeeded()`/`forceResync()` (both
  non-suspend, observed via `syncState: StateFlow<SyncState>`). The
  awaitable version has a real race: `startIfNeeded()`/`forceResync()`
  launch onto a detached application-scoped coroutine, so a caller
  immediately awaiting `state.first { terminal }` can observe a *stale*
  terminal value left over from a previous run before the newly-launched
  attempt has had a chance to flip the state to `Running`. Hall of
  Memories' simpler shape has no such race because nothing needs to await
  a specific run's own outcome. Rule 2 ("Copy Hall of Memories, don't
  invent") applied against my own draft, not just the plan's — worth
  naming explicitly since the plan's sketch was mine to begin with.
- **`PokedexRepository` exposes both `cacheStatus` and `syncState`**, not
  just the one `cacheStatus` the plan's sketch listed. `CacheStatus` is a
  snapshot of the *persisted* meta row (usable/synced-at/counts/revision);
  it has no notion of "a sync is running right now with this stage and
  progress". The Teams screen's non-blocking banner and Settings' inline
  progress bar both need that live signal, so `syncState` (a direct pass-
  through of `DatasetSyncManager.state`) was added as a second observable
  rather than folding a "current stage" field into `CacheStatus` itself,
  which would conflate "what's saved" with "what's happening right now".
- **Search ranking goes beyond the plan's one-line sketch.** The DAO's
  `LIKE` queries rank a prefix match on the normalized name ahead of a
  contains-only match, and within a tie a species' default form ahead of
  its alternate forms (searching "zygarde" surfaces `zygarde-50` before
  `zygarde-mega`) — both proven against real ranking SQL in
  `PokedexDaoTest`, not just asserted as intent. This mirrors Hall of
  Memories' `mergeSearchResults` (prefix-then-contains), done as a single
  `CASE`-ordered SQL query instead of two separate DAO calls merged in the
  repository, because the plan's own interface returns `Flow`, not a
  one-shot suspend list — a two-query app-level merge would need to
  `combine()` two live Flows on every emission, which is more complexity
  for the same result.
- **`java.text.SimpleDateFormat`, not `java.time`,** formats the "last
  synced" timestamp in Settings → Data. `minSdk` stays 24
  (`docs/plan/native-spec.md`, "Identity"); `java.time` needs API 26
  without desugaring, which is exactly the trade CLAUDE.md already said not
  to make without a real need. `PokeCacheMetaEntity.syncedAtEpochMillis`
  and `SyncState.Success.syncedAtEpochMillis` are both plain `Long`, not
  `Instant`, for the same reason.
- **`TypeBadge` renders the Scarlet/Violet type-icon sprite**, matching
  `legacy-web/src/components/TypeBadge/TypeBadge.tsx` exactly, rather than
  Hall of Memories' coloured-pill-text convention. CoverDex's own PWA
  already made this call for its own UI; porting the visual language is
  more faithful than porting Hall of Memories' unrelated one.
- **The move-count string is split into manual `_one`/`_other` resources**
  rather than a single `%d` template — lint's `PluralsCandidate` check
  flags either shape identically (confirmed against Hall of Memories'
  already-shipped `strings.xml`, which has 22 identical findings from the
  same `_one`/`_other` convention and still lints clean); this is expected
  noise from a heuristic that doesn't understand the manual split, not a
  real problem worth suppressing or working around further.
- **`DebugSeeder` exists but has no call site yet.** With nothing to seed
  until Phase 2's team/roster tables exist, wiring a call into
  `CoverDexApplication.onCreate()` now would mean exercising Hilt's
  Application-field-injection path — untested here, unverifiable without a
  device — for a function that does nothing. Phase 2 wires it up alongside
  the actual seeding logic, where there's something real to check.

### Parity check against `legacy-web` and the pinned dataset

`cd legacy-web && npm ci && npm test` — 175/175 green, unaffected by
anything in this phase.

Every number in `docs/plan/reference-pokedata.md` §3 was re-verified
against the pinned commit's live files, both by raw `awk`/`csv` counting
and — going one step further than the plan's own instruction — by actually
running `assembleDataset()` against the full pinned CSVs (not the small
test fixtures) in a temporary, throwaway test deleted immediately after
recording its output here:

```
species (forms)          = 1351   (matches: 1351 pokemon.csv rows)
distinct species         = 1025   (matches: 1025 pokemon_species.csv rows)
moves accepted into cache = 919   (see "18 shadow moves" below)
finalEvolutions, species-level = 568   (matches)
legendary, species-level       = 71    (matches)
mythical, species-level        = 23    (matches)
type_efficacy cells             = 324   (matches: 18 x 18)
```

Two things worth writing down so a future reader doesn't trip over them
the way this session briefly did:

- **Species-level counts vs. form-level counts are not the same number,
  and both are correct.** `isFinalEvolution`/`isLegendary`/`isMythical`
  are species-level facts (from `pokemon_species.csv`) applied to every
  *form* that species has. Counting them over the 1351 assembled
  `PokemonEntry` forms (rather than grouping by `speciesId` first) gives
  840/120/38 instead of 568/71/23 — not a bug, just multi-form legendaries
  (Mega/regional/etc. forms) each contributing one row per form. Group by
  `speciesId` before comparing against `reference-pokedata.md`'s numbers,
  which are all species-level.
- **18 of 937 moves never reach the cache** — see
  `reference-pokedata.md`'s new "Consequence, verified during Phase 1
  implementation" bullet for the full explanation (Pokémon Colosseum/XD's
  "Shadow" move set, `type_id = 10002`, outside the 18-type model). 919 is
  the correct count for `PokedexRepository`'s move cache from this point
  on; do not "fix" a future test that expects 937.

## Phase 2 — Teams and roster

- **`MigrationTestHelper`'s exported schema JSONs must be wired in as a
  `debug` source-set asset, not a `test` one — confirmed by measurement,
  not by trusting the widely-repeated advice.** The officially documented
  pattern (Room's own migration guide, countless blog posts and Stack
  Overflow answers) is:
  ```kotlin
  sourceSets {
      getByName("androidTest").assets.srcDirs("$projectDir/schemas")
  }
  ```
  for instrumented tests, and by extension people carry the same
  `sourceSets["test"].assets.srcDirs(...)` (or `resources.srcDirs(...)`)
  over to a Robolectric-based `MigrationTestHelper` test under
  `testOptions.unitTests.isIncludeAndroidResources = true`. Neither worked
  here (`Migration1To2Test`, added this phase, kept failing with
  `FileNotFoundException: ... Missing file:
  com.marcogn.coverdex.data.local.CoverDexDatabase/1.json`), so this was
  run to ground with real build artifacts rather than assumed to be a
  typo:
  - `MigrationTestHelper.loadSchema()` (decompiled from
    `room-testing-2.6.1-runtime.jar`, since the class name obfuscation in
    the error message hides which API it actually calls) genuinely calls
    `instrumentation.context.assets.open("$assetsFolder/$version.json")`
    — a real `android.content.res.AssetManager` read, not a JVM classpath
    resource lookup, confirming Room's own (correct, if easy to misread)
    error wording.
  - Under Robolectric, that `AssetManager` is a shadow backed by whatever
    directory AGP's generated `test_config.properties` names as
    `android_merged_assets` — inspected directly at
    `app/build/intermediates/.../test_config.properties`, which reads
    `android_merged_assets=build/intermediates/assets/debug/mergeDebugAssets`.
    That is the **real `debug` variant's** merged assets output. There is
    no `debugUnitTest`-specific assets merge task in this AGP version
    (confirmed: `./gradlew :app:help --task mergeDebugUnitTestAssets`
    reports the task does not exist) — so a `test`-source-set asset has
    nowhere to be merged *into* before Robolectric looks for it. This was
    verified empirically, not just reasoned about: pointing
    `sourceSets["test"].assets` at the schemas directory left
    `mergeDebugAssets`'s output untouched, and the test still failed
    identically; adding the same directory to
    `sourceSets["debug"].assets` instead made the files appear at
    `app/build/intermediates/assets/debug/mergeDebugAssets/...`, and the
    test passed.
  - The Hall of Memories sibling app was checked first, as the
    architectural reference — it has no precedent to copy here. It has
    never written a second schema version (`app/schemas/` holds only
    `1.json`), so it has never needed `MigrationTestHelper` at all.
  - Net effect: `app/build.gradle.kts`'s `android { sourceSets { ... } }`
    wires `getByName("debug").assets.srcDirs("$projectDir/schemas")`,
    accepting that the exported schema JSONs (a few KB, currently two
    files) ship inside debug-only APK builds. Release builds are
    unaffected — nothing here touches the `release` source set.
- **`MigrationTestHelper`'s two-argument, string-based constructor
  (`MigrationTestHelper(instrumentation, assetsFolder: String, factory)`)
  is deprecated as of Room 2.6.1** in favour of the class-based overload
  (`MigrationTestHelper(instrumentation, databaseClass: KClass<T>, specs,
  factory)`), which is required anyway once a future phase introduces an
  `@AutoMigration`. `Migration1To2Test` uses the class-based constructor
  from the start rather than deferring the fix.
- **A new custom move defaults to `type = NORMAL`, `damageClass = PHYSICAL`,
  not `damageClass = STATUS`** as `phase-2-teams-and-roster.md`'s own prose
  says ("becomes `isCustom = true`, `damageClass = STATUS`, `power = null`").
  That line does not match `legacy-web`'s actual, executable behaviour:
  `MoveSlot.tsx`'s custom-move `onChange` builds
  `{ type: move?.type ?? 'normal', power: move?.power ?? null, damageClass:
  move?.damageClass ?? 'physical', isCustom: true }` — for a brand-new
  custom move, `move` is `null`, so every one of those `??` fallbacks
  fires: `'normal'`, `null`, `'physical'`. Per `docs/plan/README.md`'s own
  rule ("when the Kotlin and the TypeScript disagree ... the TypeScript is
  right unless this plan explicitly says otherwise" — and this is a
  paraphrase slip, not a stated intentional deviation), the code wins. Only
  `power = null` from the plan's prose survives; `type` and `damageClass`
  follow `legacy-web` instead. There is nowhere in the domain model to
  encode "no move yet" versus "a move with defaults" separately from
  `PokemonMove` itself, so this only matters at the moment a slot's
  move-picker text field goes from empty to non-empty with no cached match
  — precisely the case `MoveSlot.tsx` handles this way.
- **Team and roster entity mappers (`TeamMappers.kt`) fall back to a
  default value on an unrecognized `type`/`damageClass` string instead of
  returning `null` and dropping the row**, unlike `PokedexRepositoryImpl`'s
  cache mappers (`Mappers.kt`), which do drop a bad cache row. The cache is
  re-downloadable — a corrupt row is just re-synced away — but a team or a
  roster entry is the user's own irreplaceable data, written exclusively by
  this app's own code (never parsed from an external, occasionally-bad
  source like PokéAPI's CSVs). `parseType`/`parseDamageClass` should never
  actually hit their fallback branch in practice; the fallback exists so a
  future bug in the write path degrades a slot's display rather than
  silently deleting the user's team. Asserted directly in
  `TeamMappersTest`.
- **`TeamDao`/`CustomPokemonDao` get a real column-list `UPDATE` for rename
  (`TeamDao.renameTeam`) and edit (`CustomPokemonDao.updateFields`)**,
  instead of reusing the generic `@Update`-on-the-whole-entity that
  `upsertTeam`/`upsert` already had. Both `TeamEntity.position` (list
  order) and `CustomPokemonEntity.createdAtEpochMillis` (roster sort order)
  must survive an edit unchanged, and the repository has no reason to read
  the existing row first just to copy those two columns forward — a
  column-list `UPDATE` that never mentions them is simpler and cannot
  regress by a future caller forgetting to carry them over. Asserted
  directly: `CustomPokemonDaoTest`'s "editing an entry's name does not
  reset its creation time".
- **The Teams screen's create/rename/delete wording has no `legacy-web`
  source to port for three of its four strings.** `legacy-web/src/i18n/locales/`
  has `teams.newTeam`, `teams.createEmpty` and `teams.delete`, but: renaming
  is an inline double-click-to-edit text field in the PWA with no dialog
  and no "Rename" string anywhere (`teams_rename_team_title`,
  `teams_action_rename`, `teams_team_name_label` are new keys, phrased to
  match the existing `teams_delete_title`/`settings_data_clear_confirm_title`
  style); `DeleteTeamModal.tsx`'s confirmation copy is hardcoded English,
  never run through `t()` — a real localization gap in the PWA, not
  something to carry forward — so `teams_delete_message` is phrased fresh
  (matching `teams.confirmDelete`'s intent, plus an explicit
  "cannot be recovered" clause the way `settings_data_clear_confirm_message`
  already does elsewhere in this app) rather than left English-only. This is
  the same class of gap as `SearchableDropdown`'s "No matches"/"Clear
  selection" (see above).
- **The slot editor's species picker never offers the custom roster as a
  search source** — `legacy-web`'s `PokemonSlot.tsx` has an "Include saved
  custom Pokémon in search" checkbox that merges `customs` into the
  dropdown's options; `phase-2-teams-and-roster.md`'s own "Species picker"
  bullet describes only `PokedexRepository.searchSpecies`-backed search,
  with no mention of the roster at all. Treated as a deliberate scope cut
  by the plan (the bullet is fully specified, not an ambiguous paraphrase),
  not a bug to port — it also sidesteps a real modeling cost: the picker's
  option values would otherwise need to be `PokemonEntry | TeamMember`
  (as they literally are in the TypeScript), forcing a sealed wrapper type
  through `DropdownOption<T>`. "Save as custom" (writing a slot *into* the
  roster) is unaffected and fully implemented; only the reverse direction
  (picking *from* the roster into a slot) is deferred.
- **The ability field is `EditableComboBox` (ported from Hall of
  Memories), not `SearchableDropdown`**, despite
  `native-spec.md`'s "Searchable dropdowns" section listing "ability"
  alongside species/move/generator-anchor as if all four shared one
  contract. `legacy-web`'s actual ability picker,
  `AbilityDropdown.tsx`, is a different component from
  `SearchableDropdown.tsx` entirely: it commits whatever is typed on every
  keystroke (`onChange(val)`) and only *offers* cached abilities as
  clickable shortcuts — never rejecting free text, matching this app's own
  product decision (abilities accept free text, for ROM-hack
  compatibility). That is exactly Hall of Memories' `EditableComboBox`
  contract, so it was copied from there (`docs/plan/README.md`, "Copy Hall
  of Memories, don't invent") rather than force-fitting the strict,
  pick-only-from-a-list `SearchableDropdown` onto a field the behavioural
  reference treats differently.
- **`PokemonType.displayName()` and `DamageClass.displayName()`'s wording
  was verified, not assumed.** `legacy-web` has no full localized type
  names anywhere (its `types.*` i18n keys are three-letter abbreviations
  used for compact badges elsewhere; `PokemonSlot.tsx`'s own type
  `<select>` renders the raw English slug unlocalized) and no localized
  damage-category names at all (`<option value="physical">physical</option>`,
  literal). All 18 Italian type names and the three damage-category names
  were looked up individually against Bulbapedia (an international,
  English-language wiki, cross-referenced to its Italian-wiki
  interlanguage links) rather than recalled from memory and asserted —
  see the `PokemonType.displayName()`/`DamageClassDropdown` commits for
  the full list. "Fisica"/"Speciale"/"Stato" (rather than "Fisico") is a
  minor grammatical judgment call, not a sourced fact: the feminine
  adjective form agreeing with "mossa" ("move"), matching Bulbapedia's own
  "Mossa speciale" phrasing, kept consistent across all three labels.
- **The slot editor's own copy (species placeholder, "Empty slot", "Type
  1"/"Type 2"/"None", "Save as custom"/"Clear slot", the move picker's
  placeholders) has no `legacy-web` i18n coverage at all** —
  `PokemonSlot.tsx` and `MoveSlot.tsx` hardcode every one of these in
  English with no `t()` call. Only `slot.ability` ("Abilità"/"Ability") and
  `teamDetail.pokemonTab`/`analysisTab`/`enableMoves` had a real source to
  port; everything else under `slot_*`/`team_detail_*` is freshly phrased
  to match this app's existing wording style, the same treatment already
  given to `SearchableDropdown` and the Teams screen's dialogs above.
- **Creating a team asks for a name up front**, unlike `legacy-web`'s
  `createEmptyTeam`, which auto-names it `Team ${teams.length + 1}` with no
  prompt at all. This is not a paraphrase disagreement (contrast the
  `MoveSlot` `damageClass` entry above) — `phase-2-teams-and-roster.md`'s
  own "Screens" section explicitly specifies "Create (a name dialog)" as a
  native-only UX decision, so the plan's text is the authority here, not
  `legacy-web`'s code. Creating a team still immediately opens it
  afterward, matching `createEmptyTeam`'s own behaviour.

## Phase 3 — Coverage analysis

- **`offensiveCoverageForMember` keeps its explicit `useMoves: Boolean`
  parameter**, even though `phase-3-analysis.md` §1's own pseudocode lists
  it as `fun offensiveCoverageForMember(chart: TypeChart, m: TeamMember):
  Set<PokemonType>` — no `useMoves` argument at all. The real
  `coverageEngine.ts` signature (`offensiveCoverageForMember(chart,
  member, useMoves)`) has one, every one of its own tests exercises both
  `true` and `false` explicitly, and Phase 4's suggestion engine depends on
  being able to force `useMoves = false` for a candidate regardless of
  what moves it happens to carry ("Candidates are evaluated by types
  only, always" — phase-4's §1 rule 7, and `coverageEngineTest.ts`'s own
  "custom Pokémon type-only evaluation" group, ported verbatim as
  `CoverageEngineTest`'s last case). Dropping the parameter to match the
  plan's abbreviated pseudocode would silently break that. Treated as a
  pseudocode omission, not a signature change to follow.
- **The Pokémon tab's "Analyze team" button and "include custom Pokémon"
  checkbox are deferred to Phase 4**, not built alongside the Analysis
  tab's seven sections. Both exist in `TeamDetailPage.tsx` (next to the
  tab switcher, not inside `CoverageGrid.tsx`), but the button only
  switches to the Analysis tab — already possible by tapping the tab
  itself — and the checkbox toggles `includeCustomsAnalysis`, a flag
  Phase 3's own `AnalysisUiState` carries (per `phase-3-analysis.md` §2's
  explicit instruction to combine it now) but that affects nothing until
  Phase 4's suggestion computation exists. Shipping a checkbox with no
  observable effect would be actively confusing; `AnalysisViewModel`
  already exposes `setIncludeCustomsAnalysis`/`setGenerationFilter` so
  Phase 4's `SuggestionFilters.kt` only needs to add UI, not ViewModel
  plumbing.
- **`MainDispatcherRule` is new** — the first Robolectric test in this
  codebase (or Hall of Memories) to construct a real `ViewModel` and
  collect its `StateFlow` directly, rather than testing only the
  DAO/repository layer underneath it. `viewModelScope` dispatches onto
  `Dispatchers.Main`, which no JVM test provides by default; without
  `Dispatchers.setMain(...)` (and passing that same `TestDispatcher` to
  `runTest`), `AnalysisViewModelTest` hung indefinitely — confirmed by
  reproducing the "did not run to completion" failure before adding the
  rule, not assumed from the symptom. `MainDispatcherRule` lives at the
  test-source root (`app/src/test/java/.../MainDispatcherRule.kt`, no
  package suffix) so any future `ViewModel` test can reuse it.
- **`analysis_pokemon_column_header` and `analysis_suggestions_placeholder`
  have no `legacy-web` source.** `CoverageGrid.tsx`'s "Pokémon" column
  header is a hardcoded literal (`<th>Pokémon</th>`, no `t()`); the
  Suggestions-section placeholder text has no equivalent at all, since
  `legacy-web` never had a partial state where suggestions don't exist
  yet — the phase split is native-only. Same treatment as the other
  wording gaps recorded above.
