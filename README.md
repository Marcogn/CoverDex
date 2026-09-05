# CoverDex

**Know your team's type coverage before you take it into battle.**

CoverDex is a free, native Android app that shows you, instantly, what
your team hits hard, what it's weak to, and where the holes are. Build a
team, switch to the Analysis tab, and see the whole picture — no math,
no spreadsheets, no guessing.

It's built for two kinds of trainers: competitive players who want to
stress-test a team before committing to it, and ROM hack / nuzlocke
players who need a tool that can handle custom typings and off-dex
Pokémon, which most team builders simply can't.

No account, no ads, no backend, nothing sent to a server. CoverDex runs
entirely on your device, works offline once its one-time Pokémon data
sync finishes, and never asks for your data.

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
- **Actually offline.** Pokémon data downloads once — about 8 requests
  and ~208 KB — then CoverDex never needs the internet again.

## Get CoverDex

Download the latest APK from the
**[Releases page](https://github.com/marcogn/CoverDex/releases)** and
install it directly on your device. CoverDex is **sideload-only** —
there's no Play Store listing (see [`ROADMAP.md`](ROADMAP.md)) — so
you'll need to allow installs from outside the Play Store the first
time, the same as any APK you get from somewhere other than the Store.

On first launch, CoverDex syncs the Pokémon dataset in the background —
a few seconds on a normal connection — while the rest of the app is
already usable underneath it. After that, everything is cached
on-device and every later launch is instant, fully offline included.

> **Upgrading from the old web/Capacitor build?** Saved teams do **not**
> carry over — this is a deliberate, one-time clean break (see
> [`docs/implementation-decisions.md`](docs/implementation-decisions.md),
> "Phase 0"). Export each of your teams to Showdown format from the old
> app *before* installing this one, then re-import them here afterward.

## Features

- **Multiple saved teams**, six slots each, stored on your device.
- **Full Pokédex species search**, including alternate forms, with
  instant results as you type.
- **Per-slot type overrides** for ROM hack typings, kept separate from
  the underlying species data.
- **Ability field** with known coverage effects (immunities,
  multipliers) reflected directly in the analysis, plus free-text entry
  for anything a randomizer throws at you.
- **Four move slots per Pokémon**, from the synced catalogue or entered
  as custom moves, with move-aware offensive coverage when you use them.
- **A personal custom-Pokémon roster** for anything that doesn't exist
  in the official Pokédex — build it once, reuse it across every team.
- **Deep coverage analysis**: per-Pokémon breakdown, an offensive grid,
  a defensive grid, shared team weaknesses, and a list of everything
  your team doesn't hit.
- **Ranked suggestions** for who to add or swap in, including your
  custom roster if you want it in the mix, filterable by generation.
- **"Surprise Me" team generator** with seed Pokémon and fine-grained
  slot budgets for starters, legendaries/mythicals, mega evolutions,
  and Dynamax/Gmax forms.
- **English and Italian**, following the system language or set
  explicitly, with more languages easy to add.
- **Showdown-format import/export**, via clipboard, a `.txt` file, or
  copy/paste — export from a team's menu, import from Settings.
- **Local backup and restore** to a single file — every team and the
  custom roster, never the (re-downloadable) Pokédex cache.

## Custom Pokémon & ROM hacks

CoverDex doesn't require a Pokémon to exist in the official Pokédex to
be usable:

1. In any slot, type a name the synced catalogue doesn't know and set
   its types and moves by hand.
2. Save it to your **custom roster** ("Save as custom") to reuse it
   across every team.
3. Turn on "Include custom Pokémon" in the Analysis tab's Suggestions
   filters to bring saved custom Pokémon into the suggestion engine too.
4. Edit or delete custom Pokémon any time from the **Custom Pokémon**
   screen, reached from the navigation drawer.

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
so you always know what still needs a closer look, and importing always
creates a brand-new team rather than overwriting one you already have.

## For developers

CoverDex is a native Kotlin/Jetpack Compose app — Material 3, Room,
Hilt, ViewModel/StateFlow with unidirectional data flow. There is no
web build, no Vite, no Capacitor, and no `npm` command in this
repository; everything runs through Gradle.

```bash
./gradlew assembleDebug            # debug APK
./gradlew testDebugUnitTest        # JVM unit tests (domain + Robolectric)
./gradlew lintDebug                # Android Lint
./gradlew connectedDebugAndroidTest  # instrumented tests (needs a device)
```

To understand how the app is put together before changing anything, read:

- [`CLAUDE.md`](CLAUDE.md) — architecture, conventions, and the rules
  and invariants for anyone (human or AI) changing the code.
- [`docs/STATUS.md`](docs/STATUS.md) — current snapshot of what's
  shipped, what's deferred, and what's never been verified on a device.
- [`docs/plan/`](docs/plan/) — the phase-by-phase record of how this
  native rewrite was built, including every non-obvious decision made
  along the way (`docs/implementation-decisions.md`).
- [`docs/release-signing.md`](docs/release-signing.md) — generating the
  release keystore and wiring up a signed build.

Contributions are welcome — see
[`.github/CONTRIBUTING.md`](.github/CONTRIBUTING.md) for the workflow
and house rules.

## Known limitations

- No accounts and no cloud sync — a team lives only on the device it was
  created on. Local backup (Settings → Local backup) covers moving data
  between devices or surviving a reinstall.
- The Pokémon dataset only refreshes when you ask it to, from
  Settings → Data → Redownload.
- Suggestions look at the top candidates by coverage gain, not an
  exhaustive search across every possible substitution.
- Sideload-only — there's no Play Store listing, and none is planned;
  see [`ROADMAP.md`](ROADMAP.md).

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
