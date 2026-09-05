# Roadmap

Deferred, intentionally-out-of-scope items, tracked here so they aren't
forgotten. Not a task tracker — see GitHub Issues for that. For a broader
snapshot of what's implemented versus still open, see
[`docs/STATUS.md`](docs/STATUS.md).

CoverDex finished its native Android rewrite at the end of Phase 6 of
[`docs/plan/README.md`](docs/plan/README.md); `docs/plan/` stays in the
repository as the record of how it was built and why. The items below are
this app's actual, current out-of-scope list — see
[`docs/plan/native-spec.md`](docs/plan/native-spec.md), "Explicitly out of
scope", for the full reasoning behind each one.

## Deliberately not planned

- **Play Store submission.** The release path is a signed APK attached to
  a GitHub Release; sideloading is the intended distribution model.
  Revisiting this would mean a Play Console account, review process and
  ongoing compliance burden with no clear benefit over the current model.
- **iOS.** Would mean a second, separately-maintained native codebase (or
  a cross-platform rewrite of this one) for a fan project with one
  developer.
- **A backend of any kind.** Proposed and turned down twice already — the
  coverage maths runs in microseconds at this data scale, and a backend
  would fork the business logic between client and server for no
  measured benefit. Would need a reproduced performance problem to
  reopen, not just a hypothetical one.
- **Any account or multi-user concept.** CoverDex is single-user,
  on-device data by design; teams, the custom roster and settings all
  live in one local Room database with no notion of "whose" they are.
- **Damage calculation, battle simulation, EV/IV tracking, legality
  validation.** Not what this app is — CoverDex analyzes type coverage,
  it doesn't simulate battles or validate a team against cartridge rules.
- **Migrating data from the old Capacitor build.** Decided against,
  explicitly, in Phase 0 — see `docs/implementation-decisions.md`.

## Ideas not yet committed to

Nothing currently — the native rewrite (`docs/plan/README.md`, Phases
0–6) covered everything in `docs/plan/native-spec.md`. A genuinely new
idea belongs in a GitHub Issue first, not here.
