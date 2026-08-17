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

### One-time setup

1. Go to repo Settings → Pages → Source: GitHub Actions.
2. Push to `main` — `.github/workflows/deploy.yml` handles everything
   automatically. No variables, no branch configuration needed.

### URL

https://marcogn.github.io/CoverDex/

### What happens on deploy

- The workflow runs: `test` → `build` → `deploy`.
- GitHub Pages may take 1–2 minutes to become live after the first deploy.
- The PWA cache populates on first browser visit (the PokéAPI data fetch
  described in `ARCHITECTURE.md`).
- Every push to `main` redeploys automatically, so Pages always reflects
  the latest `main` — including any version bump that lands there (see
  "Keeping the web and Android releases in sync" below).

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
targets. `.github/workflows/release-android.yml` (manual-dispatch only —
see [`docs/android/BUILD.md`](android/BUILD.md)) bumps that version,
commits the bump to `main`, tags it, and builds/publishes the signed
APK as a GitHub Release. Because that commit lands on `main`, it also
triggers `deploy.yml` automatically — so a new Android release and the
next GitHub Pages deploy always carry the same version number, without
a separate manual step.
