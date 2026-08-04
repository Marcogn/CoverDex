# CoverDex

A React + TypeScript Progressive Web App (and native Android app) for
assembling Pokémon teams and analysing their offensive and defensive type
coverage. It is built primarily for ROM hack players and competitive
builders who want fast iteration over team composition without depending
on a backend or an account system.

For how the app is put together under the hood, see
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md). For a snapshot of what's
implemented and what's still open, see [`docs/STATUS.md`](docs/STATUS.md).

## Features

- Multiple saved teams with six slots each, persisted locally on-device
  (`localStorage` on the web app, native storage on Android — see
  "Android App" below).
- Searchable picker covering every PokéAPI species and alternate form.
  Results appear only after typing at least 1 character — no initial list
  shown on focus. All matching results are displayed (no pagination cap).
- Per-slot type overrides for ROM hack typings; overrides never mutate the
  cached species data.
- **Ability field** per slot: filterable dropdown showing known abilities
  with coverage effects, plus free-text fallback for rom-hack compatibility.
  Pre-populated from PokeAPI's default ability. Abilities with known effects
  (immunities, multipliers) are reflected in defensive coverage analysis and
  tagged in the dropdown.
- Four move slots per Pokémon, picked from PokéAPI or entered as custom moves.
- Personal custom roster: any team member can be saved, renamed, deleted,
  and re-used across teams.
- Coverage analysis: per-member offensive grid, defensive profile
  (weak/resist/immune), shared team weaknesses, uncovered types.
  Ability-modified multipliers are shown in the defensive grid.
  Type columns use official PokeAPI type sprites (Scarlet/Violet style).
  Pokémon name columns are truncated with ellipsis on mobile.
- Smart suggestions: additions when the team has fewer than six members,
  weakest-link replacements when the team is full, optional inclusion of the
  custom roster, final-evolution preference, legendary handling.
- **"Surprise Me" team generator**: generates a coverage-optimised team of 6
  using a greedy algorithm. Supports seed Pokémon (lock 0–5 slots), and
  +/− counter constraints for starters, legendaries/mythicals (merged),
  Mega/Dynamax forms, and custom Pokémon. All counters use "exactly N"
  semantics — reserved slots are filled first from the category sub-pool.
  Budget rule: anchors + counters ≤ 6; remaining slots are filled freely
  by composite score. Includes per-slot regeneration (random pick among
  top 5 candidates).
- Language switcher (English / Italian) with full i18n support.
- Showdown-format import and export through clipboard or `.txt` file.
  Ability lines are now parsed and exported.
- PWA install support: works offline once the PokéAPI cache is built.
- Settings panel to reset the PokéAPI data cache.

## Tech stack

- React 18 with TypeScript
- Vite as the build tool and dev server
- Tailwind CSS for styling (dark theme)
- `vite-plugin-pwa` for the manifest and service worker
- [PokéAPI](https://pokeapi.co) as the ultimate data source, consumed via
  the static `PokeAPI/api-data` mirror on GitHub (not the live REST API),
  cached in `localStorage` — see [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
- [Capacitor](https://capacitorjs.com/) + Material UI (MUI) for the native
  Android shell — see "Android App" below

## Getting started

### Prerequisites

- Node.js **18.x or later** (Vite 5 requires Node 18+).
- npm 9 or later (bundled with Node 18).

### Install and run

```bash
npm install
npm run generate-icons  # generate PWA icons
npm run dev
```

The dev server runs on `http://localhost:5173` by default.

### Environment variables

| Variable        | Purpose                                                       |
| --------------- | -------------------------------------------------------------- |
| `VITE_BASE_URL` | Base path for the build, e.g. `/CoverDex/` for GitHub Pages. Defaults to `/`. |

Set it in a `.env` file at the project root or inline at build time:

```bash
VITE_BASE_URL=/CoverDex/ npm run build
```

## First run behavior

On first load, the app fetches all Pokémon data from the official
PokeAPI/api-data static repository on GitHub (no API key required).
This takes approximately 30–90 seconds depending on connection speed.
A progress bar shows the loading status. All data is cached in
`localStorage` after the first load — subsequent loads are instant
and work fully offline. Your teams and custom Pokémon are stored
separately under `teamdex_userdata` (`localStorage` on the web app,
native storage via `@capacitor/preferences` on Android) and are never
affected by cache resets.

The cache is never refreshed automatically. Use **Settings → Data →
Redownload Pokémon data** to force a re-download (for example after a
PokéAPI update) — the app keeps working with the existing data until the
new download finishes. This works identically on the Android app.

## Installing as a PWA

### Desktop (Chrome, Edge, Brave)

1. Open the deployed site in the browser.
2. Click the install icon in the address bar (or use the menu →
   *Install CoverDex*).
3. The app opens in its own window and works offline.

### iOS (Safari)

1. Open the site in Safari.
2. Tap the share icon, then *Add to Home Screen*.
3. The apple-touch-icon provides the home screen icon.

### Android (Chrome)

1. Open the site in Chrome.
2. Tap the menu, then *Install app* / *Add to Home screen*.

## Android App

In addition to the installable PWA above, CoverDex ships as a native Android
app shell via [Capacitor](https://capacitorjs.com/), living in `android/`.
It wraps the same web build in a WebView — no separate business logic, no
backend. This is currently **sideload/testing-only**: there is no Play Store
listing, and CI does not publish anywhere automatically.

### Prerequisites

- Node.js **22.x or later** — `@capacitor/cli` requires it, stricter than
  the plain web app's Node 18+ requirement above.
- JDK 21 (Temurin recommended).
- Android SDK / Android Studio (for the emulator, device deployment, and SDK
  Manager).

The Android build is a **separate Vite build target** from the PWA: `vite
build --mode android` outputs to `dist-android/` (the PWA's `dist/` is
completely unaffected), and only this build includes the Material Design
(MUI) UI layer — see `CLAUDE.md` → "Android Platform" for the
shared-logic/platform-presentation file convention this relies on.

### Building locally

```bash
npm run android:build   # builds dist-android/ + cap sync android
npm run android:open    # opens android/ in Android Studio
```

From Android Studio you can run on an emulator or a connected device. For a
signed release build (APK + AAB) from the command line, see
[`docs/android/BUILD.md`](docs/android/BUILD.md), which covers generating a
release keystore and the local signed-build steps.

### CI-built artifacts

`.github/workflows/android-build.yml` builds a signed release APK and AAB
on every push to the Android development branch, or on demand via
**Actions → Android Build → Run workflow**. A debug APK is **not** built
automatically — it's a separate job that only runs when you manually
trigger the workflow, so you get one exactly when you ask for it, and can
delete that workflow run afterward from the Actions history if you don't
want it kept. Because this repo is public, the regular pipeline **never**
publishes Android build output as a GitHub Actions artifact — those are
downloadable by any signed-in GitHub user with read access, debug builds
included.
Instead, both the debug and signed release APKs are pushed to **Firebase
App Distribution**, which only reaches testers explicitly invited by
email — no public link, no Play Store review. See
[`docs/android/BUILD.md`](docs/android/BUILD.md) for
Firebase project setup and tester-group management.

There is currently also a second, temporary workflow,
`.github/workflows/android-debug-apk-artifact.yml`, that builds the debug
APK and uploads it as a plain GitHub Actions artifact instead — a
deliberate, short-lived exception to the rule above, kept only for local
testing convenience and meant to be deleted once it's no longer needed.
See the comment at the top of that file before relying on it.

## Showdown import / export format

The app reads and writes
[Pokémon Showdown](https://pokemonshowdown.com)-style team blocks separated
by blank lines.

### Fields the app actually tracks

- **Species name** (first line, optionally followed by `@ item`).
- **Ability** (`Ability: <name>`) — stored in the slot's `ability` field.
- **Types**, via the trailing `# Types: <type1>[/<type2>]` comment line.
  This is how type overrides for ROM hacks round-trip.
- **Moves**, written as lines beginning with `- `.

### Fields emitted as placeholders on export

The following Showdown fields are exported as empty placeholders so that
the output is still a valid Showdown paste, but the app does **not** track
their values:

- `EVs: `
- ` Nature`
- The item after `@` on the species line

On import these placeholder lines are ignored. If a move name is unknown
to the local PokéAPI cache it is imported as a custom placeholder move and
flagged so the user can complete it manually.

### Example exported member

```
Pikachu @ 
Ability: Static
EVs: 
 Nature
- Thunderbolt
- Iron Tail
- Quick Attack
- Volt Tackle
# Types: electric
```

## Custom Pokémon

A Pokémon does not need to exist in PokéAPI to be used in a team.

1. In any slot, pick *Custom* (or edit the species name freely) and set
   its types and moves manually.
2. Click **Save to custom roster** on the slot. The Pokémon is added to
   your personal roster and persisted locally on-device (see "First run
   behavior" above for where, per platform).
3. From the picker, enable *Include custom roster* to reuse saved custom
   Pokémon in any team. They are also offered by the suggestion engine
   when the toggle is on.
4. Custom Pokémon can be renamed and deleted from the **Custom Roster**
   panel.

## GitHub Pages deployment

### GitHub Pages Setup (one-time)

1. Go to repo Settings → Pages → Source: GitHub Actions.
2. Push to `main` — the workflow handles everything automatically.
   No variables, no branch configuration needed.

### URL

https://marcogn.github.io/CoverDex/

### First Deploy Expected Behavior

- Actions runs: `test` → `build` → `deploy`.
- GitHub Pages may take 1-2 minutes to become live after the first deploy.
- PWA cache will populate on first browser visit (PokéAPI data fetch).

### Local Preview of Production Build

```bash
npm run build && npm run preview
```

### Manual deployment

```bash
VITE_BASE_URL=/CoverDex/ npm run build
# upload the contents of dist/ as a GitHub Pages artifact
```

The `public/.nojekyll` file is bundled so subdirectory assets are served
correctly.

## Known limitations

- The move-aware suggestion engine evaluates candidates by their own
  type chart only; it does not attempt to infer movepools for candidates.
- There is no backend. Teams and custom Pokémon are stored locally
  on-device — `localStorage` on the web app, native storage via
  `@capacitor/preferences` on Android — and the PokéAPI cache lives in
  `localStorage` on both. Nothing is scoped across devices or shared
  between the web and Android releases.
- The PokéAPI cache is never refreshed automatically. Use the Settings
  panel to reset it.
- Suggestion depth is shallow (top 5 candidates); the engine does not
  perform deep search across multi-slot substitutions.

## Contributing

1. Fork the repository.
2. Create a feature branch (`feature/<name>`, `fix/<name>` or
   `docs/<name>`).
3. Run `npm run test` before opening a PR.
4. Open a pull request describing what changed, why, and which tests
   cover the change.

See [`.github/CONTRIBUTING.md`](.github/CONTRIBUTING.md) for the full
contributor guide.

## License

[MIT](LICENSE)
