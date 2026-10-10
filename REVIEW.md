# REVIEW.md

What a review of a CoverDex change must look at. Rules come from `CLAUDE.md`,
`docs/plan/native-spec.md` and `docs/implementation-decisions.md`; each was
checked against the code when this file was written.

## What Important means here

A bug, lost or altered user data (teams, roster, backups), a broken migration,
a release that can't install over the previous one, or a break of a rule
below. Everything else is a Nit.

## Always check

- `domain/` stays pure Kotlin: no `android.*` import.
- Room migrations are additive and numbered (`data/local/migration/Migrations.kt`);
  never `fallbackToDestructiveMigration()`. A `NOT NULL` added column needs a
  `DEFAULT` in the SQL and a matching `@ColumnInfo(defaultValue = ...)`.
- Never `clearAllTables()`: cache invalidation names the cache tables
  (`PokedexDao`), or it takes teams and roster with it.
- No `@Insert(onConflict = REPLACE)` as the edit path of a row with
  `ON DELETE CASCADE` children (`team` → `team_member` → `team_member_move`,
  `custom_pokemon` → its moves): use `@Update`/the exists-check, or rewrite
  every child in the same transaction.
- Team slots keep denormalized snapshots (species, type overrides, ability,
  item, moves); nothing on a slot may depend on the cached catalogue.
- Sprite URLs are derived from the id (`domain/sprite`), never stored.
- Backups never contain the cached catalogue; restore is a full replace in
  one transaction, ids and timestamps preserved.
- No hardcoded user-visible strings; every new key in both
  `values/strings.xml` (Italian) and `values-en/strings.xml`.
- `MainActivity` stays an `AppCompatActivity`; screens with custom back logic
  have a `BackHandler`.
- `HttpURLConnection`: no hand-set `Accept-Encoding`.
- Robolectric test classes carry `@Config(sdk = [26])`; test names are ASCII.
- No new dependency without an explicit request; nothing outside
  `native-spec.md` or a phase plan (no backend, damage calc, legality checks).
- A user-visible change has a `CHANGELOG.md` `[Unreleased]` entry
  (`- **Summary.** detail`).

## Verification bar

Findings name `file:line` and a concrete path from a real caller or input to
the failure. For the engines, compare against the tests in `domain/`.

## Do not report

Style already consistent with the surrounding code, formatting, generated
Room code, `docs/plan/` historical text, missing tests for pure UI layout.

## Shape

Important first, then Nits; one line each with `file:line`, the rule or bug,
and the fix in a sentence.
