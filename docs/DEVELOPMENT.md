# Development Setup

Local setup and deployment detail that doesn't belong in the
market-facing [`README.md`](../README.md). For how the app is built
internally, see [`ARCHITECTURE.md`](ARCHITECTURE.md); for rules and
invariants, see [`CLAUDE.md`](../CLAUDE.md).

## Prerequisites

- Node.js **18.x or later** (Vite 5 requires Node 18+).
- npm 9 or later (bundled with Node 18).

The Android build has its own, stricter Node requirement — see
[`docs/android/BUILD.md`](android/BUILD.md).

## Install and run

```bash
npm install
npm run generate-icons  # generate PWA icons (gitignored, not committed)
npm run dev
```

The dev server runs on `http://localhost:5173` by default.

## Environment variables

| Variable        | Purpose                                                       |
| --------------- | -------------------------------------------------------------- |
| `VITE_BASE_URL` | Base path for the build, e.g. `/CoverDex/` for GitHub Pages. Defaults to `/`. |

Set it in a `.env` file at the project root or inline at build time:

```bash
VITE_BASE_URL=/CoverDex/ npm run build
```

## GitHub Pages deployment

GitHub Pages is **not** deployed automatically on every push to `main`.
It deploys only as part of cutting a release — see "Keeping the web and
Android releases in sync" below. This is a deliberate choice: it keeps
the live site's version number meaningful (it always matches a tagged
release, web and Android alike) instead of drifting on every merge.

### One-time setup

Go to repo Settings → Pages → Source: GitHub Actions. No variables, no
branch configuration needed — `.github/workflows/release.yml` (see below)
handles the rest.

### URL

https://marcogn.github.io/CoverDex/

### Local preview of a production build

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

## Keeping the web and Android releases in sync

CoverDex ships from one `package.json` version, read by both build
targets. `.github/workflows/release.yml` (manual-dispatch only — see
[`docs/android/BUILD.md`](android/BUILD.md) → "Cutting a public release")
is the single workflow that publishes anything public: given a version,
it bumps `package.json`/`android/app/build.gradle`, runs the test suite,
builds and publishes the signed Android release as a GitHub Release, and
in the same run redeploys GitHub Pages from the same checkout — so a
new Android release and the GitHub Pages deploy always carry the same
version number, published together, with nothing left to drift between
them. `.github/workflows/ci.yml` is the separate, non-publishing workflow
that just validates every PR and push to `main` (tests + a production
build check).
