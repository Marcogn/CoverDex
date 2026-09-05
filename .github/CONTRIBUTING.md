# Contributing

Thanks for considering a contribution to CoverDex.

## Workflow

1. **Fork** the repository to your own account.
2. **Branch** from `main` using one of the following prefixes:
   - `feature/<short-name>` for new functionality
   - `fix/<short-name>` for bug fixes
   - `docs/<short-name>` for documentation-only changes
3. Make your changes.
4. **Run the checks** before opening a PR:
   ```bash
   ./gradlew testDebugUnitTest lintDebug assembleDebug
   ```
5. Open a pull request against `main`.

## PR description

Every pull request description must include:

- **What changed** — a concise summary of the user-visible or
  architectural change.
- **Why** — the motivation or the issue it addresses.
- **Which tests cover it** — the test files (and ideally test names)
  that exercise the change.

## House rules

- **Room migrations are additive and numbered.** Don't change an
  existing schema version in place; add a new `MIGRATION_x_y` and bump
  the database version. `fallbackToDestructiveMigration()` is banned —
  see [`CLAUDE.md`](../CLAUDE.md).
- **Do not add new Gradle dependencies** without discussing the need in
  an issue first. The dependency catalogue is pinned; prefer the
  standard library, an existing dependency, or a small local
  implementation (this repo hand-rolls its HTTP client, CSV parsing and
  image fallback chains for exactly this reason).
- Keep changes scoped. Unrelated fixes belong in their own PR.
- Follow the conventions documented in [`CLAUDE.md`](../CLAUDE.md) —
  especially the module responsibilities, the bilingual string-resource
  rule, and the Showdown format contract.
