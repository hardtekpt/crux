# Contributing to Crux

Thanks for helping. Crux is a small project, so a short issue before a big change saves everyone
time.

## Reporting bugs and ideas

- **Bugs:** [open an issue](https://github.com/hardtekpt/crux/issues/new/choose) with the version,
  your phone and the steps to reproduce. If the app crashed, share the report from
  *Settings → Diagnostics → Crash reports* and paste it in.
- **Ideas:** open a feature request and say what you're trying to do. That matters more than how
  it should look.
- **Security problems:** please report them privately; see [SECURITY.md](SECURITY.md).

## Working on the code

Setup and commands are in the [README](README.md#building), and the big picture is in
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

1. Branch from `dev`, and send your pull request into `dev`. `main` only moves on releases.
2. Before pushing, run `./gradlew spotlessCheck testDebugUnitTest lintDebug` (or `.\crux test` on
   Windows). That's what CI runs. `./gradlew spotlessApply` fixes formatting.
3. If you change the database schema, follow [docs/DATABASE.md](docs/DATABASE.md). The climber's
   data must always migrate; it's never wiped.
4. Add or update tests for behaviour you change. ViewModels and repositories have JVM tests next
   to them under `app/src/test`.
5. Keep commits focused, and write messages that say what changed and why.

### Style

- Kotlin follows ktlint's Android Studio style (`.editorconfig`). Spotless enforces it.
- Write user-facing text in plain, friendly language, and say "climber", not "user".
- New lint warnings fail the build. Fix them rather than adding them to `app/lint-baseline.xml`.

## License

Crux is licensed under the [GNU GPL v3.0 or later](LICENSE). By contributing, you agree that your
contribution is licensed under the same terms. There's no separate contributor agreement.

Please also follow the [Code of Conduct](CODE_OF_CONDUCT.md).
