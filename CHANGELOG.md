# Changelog

All notable changes to CoverDex are recorded here, one entry per
release. Format loosely follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
versions follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

- **Post-migration review of the coverage and suggestion engines.** A
  code-level audit of the shipped app against the phase plans, diffing every
  Kotlin port against the TypeScript original recovered from git history.
  Records six findings and an ordered remediation plan in
  [`docs/post-migration-review.md`](docs/post-migration-review.md);
  no behaviour changes in this entry.

## [2.0.0] - 2026-09-04

CoverDex is now a native Android app — the full six-phase rewrite
described in [`docs/plan/README.md`](docs/plan/README.md) is complete.

- **CoverDex is now a native Android app.** Kotlin, Jetpack Compose,
  Material 3, Room, Hilt — no more WebView, no more Capacitor. Same
  `applicationId` (`com.marcogn.coverdex`) so it installs over the old
  build.
- **First launch is effectively instant.** The Pokémon catalogue now
  syncs in about 8 requests and ~208 KB, down from roughly 3,875
  requests and ~426 MB in the old app — measured, not estimated, see
  `docs/plan/reference-pokedata.md`. The app is usable immediately behind
  a small non-blocking progress banner, not a loading screen.
- **The web app and GitHub Pages are discontinued.** The React/Capacitor
  codebase was parked in `legacy-web/` as a behavioural reference for the
  rewrite and has now been deleted — every engine, string and behaviour
  it defined has a native equivalent.
- **Saved teams do not carry over from the old app.** A deliberate,
  one-time clean break (see `docs/implementation-decisions.md`, "Phase
  0"). **Export your teams to Showdown format from the old app before
  updating**, if you still have it installed, then re-import them here
  afterward.
- **The suggestion engine's generation filter uses each candidate's real
  generation**, not the hardcoded Pokédex-id ranges the original web app
  used (`docs/plan/reference-pokedata.md` §4) — an alternate-form Pokémon
  with a high catalogue id (a Mega evolution, say) now filters by the
  generation its species actually belongs to, not always "Generation 9".
- **Teams are real now.** Create, rename, delete a team; each has six
  slots. Every slot has a species picker backed by the synced catalogue,
  type overrides (a ROM-hack-friendly feature the old app also had),
  an ability field, and — behind a persisted "Enable move slots" toggle —
  four move slots, each accepting either a cached move or a typed custom
  one. A slot can be saved into a reusable custom-Pokémon roster.
- **The custom Pokémon roster is a full screen now**: create, edit and
  delete entries with the same type/ability/move editor as a team slot,
  minus the species picker.
- **Coverage analysis is real now.** Every team's Analysis tab shows: what
  basis the analysis runs on (entered moves, Pokémon types, or a mix,
  clearly stated); a per-Pokémon breakdown of weaknesses, resistances,
  immunities and ability effects; full 18-type offensive and defensive
  coverage grids; the team's shared weaknesses; and the types nothing on
  the team can hit super-effectively.
- **Suggestions are real now.** The Analysis tab's seventh section ranks
  up to five candidates to add (team under six) or swap in (a full team)
  — sprite, types, coverage gain, composite score, newly covered types and
  new weaknesses — filterable by generation and "include custom Pokémon"
  right on the screen, plus two app-wide preferences in Settings (include
  Mega/Dynamax forms, include legendaries/mythicals). Tapping a card adds
  or replaces it in the team immediately.
- **Surprise Me builds a team for you.** A new screen, reached from Teams
  via the dice icon: lock 0-5 Pokémon you want kept, set how many
  starters/legendaries-mythicals/Mega/Dynamax slots to reserve, Generate,
  regenerate individual slots or the whole team, then Keep to create the
  new team.
- **Teams round-trip through Pokémon Showdown's team format.** Export a
  team from its overflow menu — copy to clipboard or save a `.txt` file —
  and import one from Settings, pasted or opened from a file: every
  recognized Pokémon is shown before you commit, with unresolved moves
  flagged per slot (they still import, as a placeholder to fill in) and
  unresolved species skipped and listed.
- **Every setting from the old app is here**, alongside theme, language
  and the dataset status/sync/clear controls: include Mega/Dynamax/
  Gigantamax forms and include legendaries/mythicals in Suggestions.
- **Local backup.** Settings → Local backup exports every team and the
  custom roster to a single file (never the Pokédex cache — that's
  re-downloaded) and restores from one, asking for confirmation first
  since a restore fully replaces what's on the device, with no merging.

## [1.0.0]

First public release, shipped simultaneously as the web app (PWA, on
GitHub Pages) and as a native Android app.

This is the initial release, so there is no prior version to diff
against — every feature described in [`README.md`](README.md) is new as
of this version. See that file for the full feature list, and
[`docs/STATUS.md`](docs/STATUS.md) for the current implementation
snapshot.
