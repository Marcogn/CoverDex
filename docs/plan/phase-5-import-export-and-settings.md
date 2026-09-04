# Phase 5 — Showdown import/export, settings and local backup

**Goal:** feature parity with the PWA is reached. Teams round-trip through
the Showdown format, every setting from the PWA exists, and the user can
back up and restore their data to a file.

**Depends on:** Phase 4.

**Read first:** `native-spec.md` → "Showdown format contract", and:

```
legacy-web/src/utils/showdownParser.ts                158 lines
legacy-web/src/utils/__tests__/showdownParser.test.ts
legacy-web/src/components/ImportExport/ImportExport.tsx
legacy-web/src/components/Settings/SettingsPage.tsx
legacy-web/src/hooks/useAppShell.ts                   (import wiring, lines ~140-195)
```

---

## 1. Showdown round-tripping — `domain/showdown/`

A direct port of `exportMemberToShowdown` and `parseShowdownBlock`, plus the
multi-block wrappers. **External users may rely on round-tripping**, so this
is a contract, not an implementation.

The contract is written out in full in `native-spec.md`; the two details
that break silently if you get them wrong:

- The `# Types: type1[/type2]` comment is **case-insensitive** and is
  **ignored entirely** if either name is not one of the 18 types. It is not
  an error and it does not fail the block.
- An unresolved move becomes a placeholder — `isCustom = true`,
  `damageClass = STATUS`, `power = null`, original name preserved — and its
  name is appended to the returned unknown-move list. **The block still
  imports.** Dropping it, or failing the import, is wrong.

Move resolution goes through `PokedexRepository`, so an import run before
the dataset has synced resolves nothing and flags every move. That is
acceptable and must not crash; surface it as "N moves need completing",
the same message the PWA shows.

## 2. Import/export UI

Two entry points, both in Settings and both reachable from a team's
overflow menu:

- **Export** — renders the active team to a Showdown block and offers
  *copy to clipboard* and *save to a file* via
  `ActivityResultContracts.CreateDocument`.
- **Import** — a paste field plus *open a file* via
  `ActivityResultContracts.OpenDocument`. Shows what it parsed before
  committing, and flags slots with unknown moves.

The PWA is clipboard-only; file pickers are the native affordance and are
worth adding here. Use SAF, not raw storage permissions — Hall of Memories'
`LocalBackupManager` is the pattern to copy.

## 3. Settings

Everything the PWA has, plus what Phase 1 already added:

| Row | Backing store |
|---|---|
| Theme (system / light / dark) | `ThemePreferences` (Phase 0) |
| Language (Italiano / English) | `AppCompatDelegate.setApplicationLocales` |
| Include Mega/Dynamax forms | `SettingsPreferences.includeMegaDynamax` |
| Include legendaries in suggestions | `SettingsPreferences.excludeLegendaries`, **inverted in the UI** |
| Enable move slots | `SettingsPreferences.showMoves` |
| Include custom Pokémon in analysis | `SettingsPreferences.includeCustomsAnalysis` |
| Dataset status / sync / clear | Phase 1 |
| Export / import Showdown | §2 |
| Back up / restore all data | §4 |
| App version | `BuildConfig.VERSION_NAME` |

Two carried-over details worth not rediscovering:

- The legendaries row is presented **inverted**: the switch reads "include
  legendaries" and stores `excludeLegendaries = !checked`. Keep the stored
  name and the inversion, so the ported engine tests stay comparable.
- `includeMegaDynamax = false` filters the pool with the regex
  `-mega|-gmax|-dynamax|-mega-x|-mega-y` against the form name. Port the
  pattern verbatim; it is applied to the suggestion pool only, never to the
  species picker.

All settings live in the **same DataStore file** (`settings_prefs`) opened in
Phase 0. One file, not five.

## 4. Local backup

Copy Hall of Memories' `data/backup/` wholesale: a zip archive containing a
`backup.json` of the user data, written and read through SAF.

Rules, all of them carried over and all of them non-negotiable:

- **A backup never contains the Pokédex cache.** It is re-downloadable data,
  and including it would multiply the archive size for no benefit.
- **Restore is a full replace**, in a single transaction, with ids and
  `createdAt` timestamps preserved. No merging, no conflict resolution, no
  "keep both".
- Restore asks for explicit confirmation and says plainly that it replaces
  everything.
- The payload is versioned. A backup from a newer schema is refused with a
  clear message, not partially applied.
- `domain/backup/` holds the DTOs and the mapping (pure, testable);
  `data/backup/` holds the zip and the SAF plumbing.

> `Json.encodeToString(value)` without
> `import kotlinx.serialization.encodeToString` binds to the wrong overload
> and fails with a misleading type error. Import it explicitly.
>
> kotlinx.serialization defaults do not cover an explicit `null` — a default
> fills a *missing* key, but `"field": null` still throws unless the type is
> nullable. Configure `Json { ignoreUnknownKeys = true; coerceInputValues = true }`
> and make every optional field nullable.

## Deliverables

- [ ] `domain/showdown/` — export and parse, contract-complete.
- [ ] Import/export UI with clipboard **and** SAF file paths.
- [ ] Every settings row in the table above, wired and persisted.
- [ ] `domain/backup/` + `data/backup/` — zip backup and full-replace restore.
- [ ] Strings in **both** locales.
- [ ] `CHANGELOG.md`, `docs/test-plan.md`, `docs/implementation-decisions.md`.

## Tests

- **Port `showdownParser.test.ts` case by case**, same expected strings.
- Round-trip property: export → parse → export produces a byte-identical
  block, for a member with two types, an ability and four moves; for one
  with a single type and no ability; for one with a custom move.
- An unknown move imports as a flagged placeholder and the block survives.
- A `# Types:` comment with an invalid type name is ignored, not fatal.
- `BackupPayloadTest` (pure) — DTO round-trip including empty slots and
  null abilities.
- `BackupArchiveTest` (Robolectric, `@Config(sdk = [26])`) — write then read
  a zip; a restore replaces existing data entirely; **a restore leaves the
  Pokédex cache untouched**; a payload with a future version is refused.
- `SettingsPreferencesTest` (Robolectric) — every key round-trips; an empty
  store returns the documented defaults.

## Manual verification (`docs/test-plan.md`)

Not automatable, and all of it should be listed for the user to run:
clipboard copy/paste against the real Pokémon Showdown site, the two SAF
pickers, a real backup/restore cycle across an app reinstall, and the
language switch applying to every screen.

## Not in this phase

The release pipeline, signing, the README rewrite, deleting `legacy-web/`.
