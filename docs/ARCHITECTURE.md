# Architecture

Crux is a single-module Android app. Everything runs on the phone: there is no server.

```
ui (Compose screens + ViewModels)
  │  StateFlow<UiState>, plain event functions
  ▼
data (repositories → Room DAOs, DataStore, image store)
  │
  ▼
Room database files + DataStore preferences + files/area_images
```

## Layers

- **`ui/`**: one package per tab (`home`, `train`, `journal`, `progress`, `you`), plus
  `places`, `settings`, the shared `components`, `charts` and `theme`. Each screen pairs a
  `@HiltViewModel` with a stateless `…Content` composable. ViewModels expose a `StateFlow` and
  plain functions for events. Screens that are easy to preview and test take state and lambdas,
  not the ViewModel.
- **`data/`**: repositories behind interfaces (`ClimbRepository`, `PlaceRepository`,
  `BodyRepository`, `TemplateRepository`, `ExerciseRepository`, `NoteRepository`,
  `RecordRepository`), each with an `Offline…` implementation over Room. Tests use the fakes in
  `app/src/test/.../data/Fakes.kt`. Also here:
  - `prefs/`: DataStore settings.
  - `dashboard/`: the Home layout, stored in DataStore.
  - `backup/`: the JSON backup format.
  - `images/`: copies photos and videos into app storage.
  - `diagnostics/`: on-device crash reports.
  - `seed/`: starter and demo data.
- **`di/`**: Hilt modules. `DatabaseModule` decides how each database is built.
- **`work/`**: WorkManager workers, with the Hilt worker factory set up in `CruxApplication`.

## Navigation

Navigation Compose with type-safe `@Serializable` routes ([Routes.kt](../app/src/main/java/com/hardtekpt/crux/ui/navigation/Routes.kt)).
Each bottom-bar tab is a nested graph (`HomeGraph`, `TrainGraph`, `JournalGraph`,
`ProgressGraph`, `YouGraph`), so every tab keeps its own back stack. Places and Settings live in
the You graph. The Log button opens a quick-log sheet over any tab.

## Two databases: yours and the demo

[`CruxDatabases`](../app/src/main/java/com/hardtekpt/crux/data/local/CruxDatabases.kt) holds two
Room databases with the same schema:

| Database | File | When it's used | If a migration is missing |
| --- | --- | --- | --- |
| The climber's own | `crux-user.db` | Normally | Crux fails to open it, and the old file is copied to `files/db-backups/` first. It is **never wiped**. |
| Demo | `crux.db` | *Settings → Demo mode* | It is rebuilt and seeded again |

Repositories never hold a database. They call `dbs.observe { db -> … }` for flows, which switch
to the other database when demo mode changes, and `dbs.current()` for writes. Turning demo mode on
or off never touches the other data set.

## Domain decisions worth knowing

- **Grades** are stored as a scale (`GradeScale`: Font, V, French, YDS, or a place's local scale)
  plus an index, and they're **never converted**. A local scale also stores the label or tape
  colour. Bests are kept per scale, and for local scales per place.
- **Places → sections → areas → problems.** A place can hold several kinds of climbing (gym,
  crag, board). Each kind is a named section. Walls (areas) and problems hang off sections.
  Climbs link to a place, and optionally a section, wall and problem. A climb copies its problem's
  grade but stays editable.
- **Projects are derived, not flagged**: a problem that's still up, has been tried, and has no send.
- **Retired** means taken down: hidden from pickers and projects, but its history is kept.
- **Units**: everything is stored in kg and cm. Imperial only changes what's shown.
- **Media**: photos are scaled to 2048 px JPEGs in `files/area_images`, and videos are copied as
  they are. The database stores only file names.

## Backups

[`BackupRepository`](../app/src/main/java/com/hardtekpt/crux/data/backup/Backup.kt) writes a
self-describing JSON file (`format: "crux-backup"`, `version`) with optional sections. Records
refer to each other by name, not database id, so a file imports into any install. Importing only
adds, and anything already there is skipped. Photos travel as base64; videos don't. A file with
a newer `version` is refused with a clear message. Older files must keep importing:
`BackupRepositoryTest` imports a real v0.1.0 backup to check that.

## Testing

| Where | What | Runs |
| --- | --- | --- |
| `app/src/test` | ViewModels with fake repositories, repositories and backups on in-memory Room (Robolectric), Compose components (Robolectric), and screenshots of the main screens with the demo data in both themes (Roborazzi, goldens in `app/src/test/screenshots`) | Every push (`./gradlew verifyRoborazziDebug`) |
| `app/src/androidTest` | Room migrations from every old schema, DAOs, full-app Compose flows with Hilt | PRs into `main`, before releases, on demand |

Coroutines in tests run on `MainDispatcherRule`'s test dispatcher, and test DataStores come from
`mainDispatcherRule.preferencesDataStore(…)`, so nothing waits on a real clock.

## Build and release

[CLAUDE.md](../CLAUDE.md) holds the branch and release rules. In short: work lands on `dev`; CI
runs ktlint, the unit tests, Android Lint and a release build. Merging into `main` runs the device
tests, builds a signed APK, checks its signing certificate and publishes a GitHub release.
