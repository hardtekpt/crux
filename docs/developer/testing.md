# Testing

| Suite | Where | Runs on | Command | When CI runs it |
| --- | --- | --- | --- | --- |
| Unit and Robolectric | `app/src/test` | JVM | `./gradlew testDebugUnitTest` | Every push to `dev`, every PR |
| Screenshots | `app/src/test` (`ScreenshotTest`, `StoreGraphicsTest`) | JVM (Robolectric native graphics) | `./gradlew verifyRoborazziDebug` | Every push to `dev`, every PR |
| Instrumented | `app/src/androidTest` | Emulator | `./gradlew connectedDebugAndroidTest` | PRs into `main`, before every release, pushes to `ci/*` |
| Release launch check | `scripts/release-smoke.sh` | Emulator | (CI only) | With the instrumented tests |
| Reproducible build | `.github/workflows/reproducible.yml` | CI | (CI only) | PRs into `main`, pushes to `ci/*` |

`verifyRoborazziDebug` runs the whole unit suite *and* compares screenshots, so it replaces
`testDebugUnitTest` in practice. `.\crux test` runs it with ktlint and lint.

## Unit and Robolectric tests

- **ViewModels** with fake repositories ([`Fakes.kt`](../../app/src/test/java/com/hardtekpt/crux/data/Fakes.kt))
  and a fixed clock (`FIXED_CLOCK`, Monday 5 October 2026).
- **Repositories** on in-memory Room under Robolectric (`RepositoriesTest`, `PlaceRepositoryTest`),
  including demo-mode isolation.
- **Backups**: round-trips of every section (sessions and settings too), photos and videos byte for
  byte, the media switches, each duplicate choice, and importing real v0.1.0 and v0.2.0 files.
  `BackupViewModelTest` walks the duplicate questions.
- **Database safety**: `DatabaseSnapshotsTest` (copies before migrations), `SchemaExportTest`
  (every schema version has its exported JSON).
- **Compose components** (Robolectric): the input kit, list rows, and so on.

### Coroutines

`MainDispatcherRule` swaps `Dispatchers.Main` for an `UnconfinedTestDispatcher`. Create test
DataStores with `mainDispatcherRule.preferencesDataStore(file)`, which runs on that dispatcher so
writes finish before the call returns. Prefer `runTest` to `runBlocking`, and never wait on
wall-clock timeouts: the CI runners are slow, and those tests flake.

## Screenshot tests

[`ScreenshotTest`](../../app/src/test/java/com/hardtekpt/crux/ui/ScreenshotTest.kt) renders the main
screens from **real ViewModels over the demo data** at the fixed date, in dark and light, at a
Pixel 7 size. Goldens:

- Dark Home, Journal, Progress, You, Train, Session, Log climb and Place are the **store
  screenshots**: `fastlane/metadata/android/en-US/images/phoneScreenshots/`.
- Everything else (light themes, Settings) is in `app/src/test/screenshots/`.
- `*_text_largest_dark` render Home, Log climb, Session and Settings with 1.69× text
  (`@Config(fontScale = …)`), to catch text that clips or overlaps when the font is large.
- `home_{blue,violet,pink,chalk}_{dark,light}` show Home in some of the accents other than teal.
- `StoreGraphicsTest` draws the store icon and feature graphic from the launcher icon.

Comparison is **exact**: removing one letter changes about 200 pixels, so any tolerance would hide
real changes. Windows and Linux render identically.

**When you change how a screen looks on purpose:**

```sh
./gradlew recordRoborazziDebug     # or .\crux screenshots
git diff --stat                    # see which images changed, then look at them
```

Commit the new images with the change. When CI's verification fails, the
`reports` artifact has the diffs (`app/build/outputs/roborazzi/`).

Screens must read "today" from `LocalClock`, not `LocalDate.now()`. The tests provide
`FIXED_CLOCK`, so relative dates ("2 days ago") don't change from day to day.

## Instrumented tests

[`app/src/androidTest`](../../app/src/androidTest/java/com/hardtekpt/crux), with `HiltTestRunner`
and `TestDatabaseModule` (fresh in-memory databases and DataStore per test):

- **`MigrationTest`**: migrates every exported schema to the current one, carries data typed at the
  oldest schema all the way through, and has step tests for migrations that move data.
- **`ClimbDaoTest`**: DAO queries.
- **`MainActivityTest`**: full-app flows: logging a climb, plans, the dashboard editor, demo mode.

Run them **on an emulator only**. They uninstall the app first, wiping its data:

```sh
.\crux device-test                                         # Windows, pins the emulator
ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest
```

To run them in CI on any commit: `git push origin HEAD:ci/device-tests` (delete the branch after).

## Coverage

Kover reports unit-test line coverage for the debug build (generated code excluded):

```sh
./gradlew koverHtmlReportDebug     # app/build/reports/kover/htmlDebug/index.html
```

CI uploads the report as the `coverage` artifact. There's no threshold yet.

## What a change needs

| Change | Tests |
| --- | --- |
| ViewModel logic | A JVM test with fakes |
| Repository or DAO query | A Robolectric test on in-memory Room |
| Schema | Migration step test if data moves; see [Database](database.md) |
| Backup format | Round-trip, and the v0.1.0 and v0.2.0 fixtures must still import |
| Visible UI | Re-recorded screenshots; a `MainActivityTest` flow for new interactions |
