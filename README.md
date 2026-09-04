# CoverDex

> **This repository is being rewritten as a native Android app.** The web
> app and GitHub Pages described below are being retired; see
> [`docs/plan/README.md`](docs/plan/README.md) for the migration plan and
> [`docs/STATUS.md`](docs/STATUS.md) for what's actually shipped so far.
> This README is rewritten for the native app in Phase 6.

**Know your team's type coverage before you take it into battle.**

CoverDex is a free Pokémon team builder that shows you, instantly, what
your team hits hard, what it's weak to, and where the holes are. Build a
team, switch to the Analysis tab, and see the whole picture — no math,
no spreadsheets, no guessing.

It's built for two kinds of trainers: competitive players who want to
stress-test a team before committing to it, and ROM hack / nuzlocke
players who need a tool that can handle custom typings and off-dex
Pokémon, which most team builders simply can't.

No account, no ads, nothing sent to a server. CoverDex runs entirely on
your device, works offline once it's loaded, and never asks for your data.

**[Open the web app →](https://marcogn.github.io/CoverDex/)**
Also available as a native Android app — see [Get CoverDex](#get-coverdex) below.

## Why CoverDex

- **See the whole board at once.** Every type your team covers
  offensively, every shared weakness, every uncovered type — one screen,
  updated live as you edit your team.
- **Ability-aware coverage.** Levitate, Flash Fire, Water Absorb and
  other coverage-relevant abilities are factored into the defensive
  math automatically, not just shown as a badge.
- **Smart suggestions, not guesswork.** CoverDex ranks which Pokémon
  would actually improve your team, weighing the new coverage they add
  against the weaknesses they'd introduce.
- **"Surprise Me" generator.** Don't want to start from scratch? Lock in
  a Pokémon or two, set a few constraints (starters, legendaries, mega
  evolutions...), and let CoverDex build a coverage-optimized team
  around them.
- **First-class ROM hack support.** Override any Pokémon's type on a
  per-slot basis for a randomizer or a hack with different typings —
  every calculation adapts to match, without touching the real data.
- **Speaks Showdown.** Import a team you already built on Pokémon
  Showdown, or export yours in the same format to share or battle with.
- **Actually offline.** Pokémon data downloads once, then CoverDex
  never needs the internet again — install it and take it anywhere.

## Get CoverDex

| | |
|---|---|
| **Web app** | Open **[marcogn.github.io/CoverDex](https://marcogn.github.io/CoverDex/)** in any modern browser. Works on desktop and mobile, and can be installed as an app — see [Installing as a PWA](#installing-as-a-pwa). |
| **Android app** | Download the latest APK from the [Releases page](https://github.com/marcogn/CoverDex/releases) and install it directly on your device. Not on the Play Store yet — see [Android app](#android-app) for what that means in practice. |

Both versions have the same features, restyled to feel native on each
platform. They're independent installs and don't share data with each
other — a team built on the web app stays on the web app, and a team
built on the Android app stays there.

## Features

- **Multiple saved teams**, six slots each, stored on your device.
- **Full PokéAPI species search**, including alternate forms, with
  instant results as you type.
- **Per-slot type overrides** for ROM hack typings, kept separate from
  the underlying species data.
- **Ability picker** with known coverage effects (immunities,
  multipliers) reflected directly in the analysis, plus free-text entry
  for anything a randomizer throws at you.
- **Four move slots per Pokémon**, from PokéAPI or entered as custom
  moves, with move-aware offensive coverage when you use them.
- **A personal custom-Pokémon roster** for anything that doesn't exist
  in the official Pokédex — build it once, reuse it across every team.
- **Deep coverage analysis**: per-Pokémon breakdown, an offensive grid,
  a defensive grid, shared team weaknesses, and a list of everything
  your team doesn't hit.
- **Ranked suggestions** for who to add or swap in, including your
  custom roster if you want it in the mix.
- **"Surprise Me" team generator** with seed Pokémon and fine-grained
  slot budgets for starters, legendaries/mythicals, mega evolutions,
  Dynamax/Gmax forms, and custom Pokémon.
- **English and Italian**, with more languages easy to add.
- **Showdown-format import/export**, via clipboard or `.txt` file.
- **Installable, offline-first PWA** on desktop, iOS, and Android.

## Custom Pokémon & ROM hacks

CoverDex doesn't require a Pokémon to exist in the official Pokédex to
be usable:

1. In any slot, choose *Custom* (or just type a name PokéAPI doesn't
   know) and set its types and moves by hand.
2. Save it to your **custom roster** to reuse it across every team.
3. Enable *Include custom roster* in the picker to bring saved custom
   Pokémon into a team, and into the suggestion engine.
4. Rename or delete custom Pokémon any time from the **Custom Roster**
   panel.

Combined with per-slot type overrides, this covers the two things a
"real" Pokédex team builder can't: Fakemon and re-typed movesets from a
randomizer or ROM hack.

## Showdown import/export

CoverDex reads and writes [Pokémon Showdown](https://pokemonshowdown.com)-style
team text, so you can move a team in or out in seconds. On export it
also writes a `# Types: ...` comment per Pokémon, which is how a ROM
hack's type overrides survive the round trip — Showdown itself ignores
the comment, so the file still works normally there too. Unknown moves
on import are kept as flagged placeholders rather than silently dropped,
so you always know what still needs a closer look.

## Installing as a PWA

**Desktop (Chrome, Edge, Brave)** — open the web app, then click the
install icon in the address bar (or the menu → *Install CoverDex*).

**iOS (Safari)** — open the web app, tap the share icon, then
*Add to Home Screen*.

**Android (Chrome)** — open the web app, tap the menu, then
*Install app* / *Add to Home screen*. (This installs the PWA; the
native Android app from [Get CoverDex](#get-coverdex) above is a
separate, independent option.)

On first launch, CoverDex downloads the full Pokémon dataset — this
takes roughly 30–90 seconds depending on your connection, with a
progress bar showing what's happening. After that, everything is cached
on-device and every later launch is instant, fully offline included.

## Android app

CoverDex also ships as a native Android app, wrapping the same web app
in a lightweight native shell so it installs, launches, and feels like
a normal Android app. It's currently **sideload-only** — there's no
Play Store listing yet, so you install the APK from the
[Releases page](https://github.com/marcogn/CoverDex/releases) the same
way you would any APK from outside the Play Store (you'll need to allow
installs from that source once).

Building it yourself only takes two commands once the prerequisites are
in place:

```bash
npm run android:build   # builds the Android bundle and syncs it into android/
npm run android:open    # opens the project in Android Studio
```

See [`docs/android/BUILD.md`](docs/android/BUILD.md) for prerequisites,
signing a release build, and CI details.

## For developers

```bash
npm install
npm run generate-icons  # generate PWA icons (gitignored, not committed)
npm run dev              # dev server at http://localhost:5173
npm run test              # run the test suite
```

Requires Node.js 18+ (the Android tooling separately requires Node 22+,
see above). Full setup, environment variables, and the GitHub Pages
deployment process are in [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md).

To understand how the app is put together before changing anything, read:

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — how the app fits
  together: data flow, the two calculation engines, the web/Android split.
- [`docs/STATUS.md`](docs/STATUS.md) — current snapshot of what's done
  and what's still open.
- [`CLAUDE.md`](CLAUDE.md) — rules and invariants for anyone (human or
  AI) changing the code, with pointers to the more detailed docs it
  summarizes.

Contributions are welcome — see
[`.github/CONTRIBUTING.md`](.github/CONTRIBUTING.md) for the workflow
and house rules.

## Known limitations

- No accounts and no cloud sync — a team lives only on the device (and
  the platform, web or Android) it was created on.
- The Pokémon dataset only refreshes when you ask it to, from
  Settings → Data → Redownload.
- Suggestions look at the top candidates by coverage gain, not an
  exhaustive search across every possible substitution.
- The Android app is sideload-only for now; there's no Play Store listing.

## About this project

CoverDex is an independent fan project, built by [marcogn](https://github.com/marcogn)
in close collaboration with [Claude](https://claude.com), Anthropic's AI
coding assistant — Claude wrote and reviewed most of the code, under
direct human design and review at every step. Pokémon data comes from
[PokéAPI](https://pokeapi.co); team import/export follows the
[Pokémon Showdown](https://pokemonshowdown.com) format. Pokémon and all
related media are trademarks of Nintendo, Game Freak, and The Pokémon
Company — CoverDex is unofficial, non-commercial, fan-made software with
no affiliation to any of them.

## License

[MIT](LICENSE)
