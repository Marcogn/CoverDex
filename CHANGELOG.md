# Changelog

All notable changes to CoverDex are recorded here, one entry per
release. Format loosely follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
versions follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

CoverDex is being rewritten as a native Android app; see
[`docs/plan/README.md`](docs/plan/README.md). This section accumulates
phase by phase and is cut to a real `## [2.0.0]` entry in Phase 6.

- **The web app and GitHub Pages are discontinued.** The React/Capacitor
  codebase is parked in `legacy-web/` as a behavioural reference for the
  rewrite (its test suite stays green throughout) and is deleted once the
  native app has everything it needs from it.
- **The Android app is now fully native** — Kotlin, Jetpack Compose,
  Material 3, Room, Hilt — instead of a Capacitor WebView shell. Same
  `applicationId` (`com.marcogn.coverdex`) so it installs over the old
  build.
- **Saved teams do not carry over from the old app.** A deliberate, clean
  break — see `docs/implementation-decisions.md`. Export your teams to
  Showdown format from the old app before updating, if you still have it
  installed.
- Theme (System/Light/Dark) and language (System/Italiano/English)
  settings, both persisted.
- **The Pokémon catalogue now syncs in about 8 requests and ~208 KB**,
  down from roughly 3,875 requests and ~426 MB in the old app — measured,
  not estimated, see `docs/plan/reference-pokedata.md`. First launch no
  longer blocks the app behind a loading screen: a small banner shows
  progress on the Teams screen while everything else stays usable.
- Settings → Data: dataset status, re-sync, and a "Clear cached data"
  action that never touches saved teams.
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
  new weaknesses — filterable by generation, "include custom Pokémon" and
  "exclude legendaries/mythicals". Tapping a card adds or replaces it in
  the team immediately.
- **The suggestions' generation filter uses each candidate's real
  generation**, not the hardcoded Pokédex-id ranges the original web app
  used (`docs/plan/reference-pokedata.md` §4) — an alternate-form Pokémon
  with a high catalogue id (a Mega evolution, say) now filters by the
  generation its species actually belongs to, not always "Generation 9".
- **Surprise Me builds a team for you.** A new screen, reached from Teams
  via the dice icon: lock 0-5 Pokémon you want kept, set how many
  starters/legendaries-mythicals/Mega/Dynamax slots to reserve, Generate,
  regenerate individual slots or the whole team, then Keep to create the
  new team.

## [1.0.0]

First public release, shipped simultaneously as the web app (PWA, on
GitHub Pages) and as a native Android app.

This is the initial release, so there is no prior version to diff
against — every feature described in [`README.md`](README.md) is new as
of this version. See that file for the full feature list, and
[`docs/STATUS.md`](docs/STATUS.md) for the current implementation
snapshot.
