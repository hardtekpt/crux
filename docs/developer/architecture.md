# Architecture

Crux is a single-module Android app. Everything runs on the phone; there is no server, account
or sync.

```mermaid
flowchart TB
    subgraph UI["ui/ · Jetpack Compose"]
        Screens["Screens + …Content composables"]
        VMs["@HiltViewModel ViewModels"]
    end
    subgraph Data["data/"]
        Repos["Repositories (interfaces + Offline… impls)"]
        DBs["CruxDatabases: real ⇄ demo"]
        Prefs["UserPreferencesRepository / DashboardRepository"]
        Media["AreaImageStore"]
        Backup["BackupRepository"]
    end
    subgraph Storage["On the device"]
        RealDB[("crux-user.db")]
        DemoDB[("crux.db (demo)")]
        DS[("DataStore prefs")]
        Files[("files/area_images, crashes, db-backups")]
    end
    VMs -- "StateFlow of UiState" --> Screens
    Screens -- "events" --> VMs
    VMs --> Repos
    Repos --> DBs
    DBs --> RealDB
    DBs --> DemoDB
    VMs --> Prefs --> DS
    Repos --> Media --> Files
    Backup --> DBs
    Backup --> Media
```

## Layers

### `ui/`: Compose screens and ViewModels

- One package per area: `home`, `train`, `journal`, `session`, `progress`, `you`, `places`,
  `datasets` (datasets, playlists and board catalogues), `settings`, `quicklog`. Shared code lives in `components` (the design system's building blocks
  and the touch input kit), `charts`, `navigation` and `theme`.
- A screen is a `@HiltViewModel` plus composables. The ViewModel exposes `StateFlow`s built with
  `combine(…).stateIn(viewModelScope, WhileSubscribed(5_000), initial)`, and plain functions for
  events. Screens collect with `collectAsStateWithLifecycle()`.
- The main tabs have a stateless `…Content(uiState, …)` composable (`HomeContent`,
  `JournalContent`, `ProgressContent`, `YouContent`, `SettingsContent`, `LogClimbContent`) for
  previews and screenshot tests. Other screens take their ViewModel as a parameter (defaulting to
  `hiltViewModel()`), so tests can pass one built by hand.
- **Composition locals** for app-wide context: `LocalUnits` (metric or imperial), `LocalGradeScales`,
  `LocalClock` ("today"; the system clock in the app, a fixed clock in screenshot tests) and
  `LocalNavBarClearance` (room for the floating bar).

### `data/`: repositories over Room and DataStore

- Repositories behind interfaces: `ClimbRepository`, `PlaceRepository`, `BodyRepository`,
  `TemplateRepository`, `ExerciseRepository`, `SessionRepository`, `NoteRepository`,
  `RecordRepository`, `DatasetRepository` and `PlaylistRepository`. Each has an `Offline…`
  implementation over Room, bound in `di/DataModule.kt`. `CatalogueRepository` reads the separate
  catalogue database.
  Tests use the fakes in `app/src/test/.../data/Fakes.kt` or the real ones on in-memory Room.
- `model/` holds the domain types (`Climb`, `Place`, `WorkoutTemplate`, `Session`, `GradeScale`,
  and so on). `local/` holds Room entities, DAOs and the database. Repositories map between them.
- Also here:
  - `prefs/`: settings in DataStore.
  - `dashboard/`: the Home layout as JSON in DataStore.
  - `backup/`: the backup file format and import/export.
  - `images/`: copies photos and videos into app storage.
  - `diagnostics/`: on-device crash reports.
  - `seed/`: starter library and demo data.

### `di/` and `work/`

Hilt modules: `DatabaseModule` (how each database is built), `AndroidModule` (application scope,
content resolver, crash reports), `ClockModule` (an injectable `java.time.Clock`, so time is
testable) and `RepositoryModule` (bindings). `work/` holds WorkManager workers. The Hilt worker
factory is set up in `CruxApplication`.

## Two databases: yours and the demo

[`CruxDatabases`](../../app/src/main/java/com/hardtekpt/crux/data/local/CruxDatabases.kt) holds two
Room databases with the same schema:

| Database | File | Used when | On a schema change without a migration |
| --- | --- | --- | --- |
| The climber's own | `crux-user.db` | Normally | Opening fails loudly. The file is copied to `files/db-backups/` before any migration. It is **never wiped**. |
| Demo | `crux.db` | *Settings → Demo mode* | Rebuilt and seeded again |

Repositories never hold a database. They read with `dbs.observe { db -> … }`, a flow that switches to
the other database when demo mode changes (`flatMapLatest`), and write with `dbs.current()`.

```mermaid
sequenceDiagram
    participant VM as ViewModel
    participant R as Repository
    participant D as CruxDatabases
    participant P as Preferences (demoMode)
    VM->>R: observeClimbs()
    R->>D: observe { db -> db.climbDao()… }
    P-->>D: demoMode = false → real
    D-->>VM: climbs from crux-user.db
    P-->>D: demoMode = true → demo
    D-->>VM: climbs from crux.db (re-subscribed)
```

## First-run setup

`MainActivity` asks [`FirstRun`](../../app/src/main/java/com/hardtekpt/crux/data/FirstRun.kt) once
whether to show setup: only when the `setup_done` preference is unset, demo mode is off and the
climber's own database has nothing in it (climbs, places, sessions, plans, exercises, body stats,
notes, datasets). Otherwise it marks setup done, so an install from before setup existed, or one
restored from a backup, never sees it. Until that's known the Activity draws only the background;
then it shows `ui/setup/SetupFlow` or `CruxApp`, so setup isn't a navigation route.

`SetupViewModel` keeps every choice in `SetupState` and saves nothing until Start: the units and
grade scales (the same preferences Settings writes), the place through `PlaceRepository.savePlace`
(and as the last place, so Log climb starts there), the weight and height through `BodyRepository`,
then demo mode last, so all of it lands in the climber's own database. **Restore a backup** shows
the app's `BackupScreen`; leaving it opens the app if the import put anything in the database.

## Navigation

Navigation Compose with type-safe `@Serializable` routes
([Routes.kt](../../app/src/main/java/com/hardtekpt/crux/ui/navigation/Routes.kt)), wired in
[`CruxApp.kt`](../../app/src/main/java/com/hardtekpt/crux/ui/CruxApp.kt). Each bottom-bar tab is a
nested graph (`HomeGraph`, `TrainGraph`, `JournalGraph`, `ProgressGraph`, `YouGraph`), so every tab
keeps its own back stack. Places, Settings and About live in the You graph. The floating bar's **+**
opens the quick-log sheet over any tab. Transitions are quiet fades, and a running session shows as
a pill above the bar.

## Live sessions

A session is a row in `sessions` with status `RUNNING`, `FINISHED` or `DISCARDED`. Starting from a
plan copies the plan's exercises and targets into `session_items`, so later edits to the plan don't
rewrite history. Each logged or skipped set is a `session_sets` row. Climbs logged while a session
runs carry its `sessionId`. Only one session runs at a time.

The session screen (`ui/session`) lists every exercise as an `ExerciseCard` (`SessionList.kt`) with a
table of sets. Values changed on a set not done yet are held in `SessionViewModel.pending` until the
set is ticked (`tick` logs them and starts the rest); a logged set is saved at once. `nextUp` picks
the set the dock's Done button logs, and `previous` gives each exercise's sets from the last
finished session for the *Last* column.

The interval and rest timers are computed from the clock (start time plus pause bookkeeping), not
ticked, so they stay right when the screen is off or the app is in the background.

The rest, the session's interval timer and the Interval timer page's run live in `LiveTimers`, an
app-wide singleton, so they outlive the session screen. `live/LiveService` is a foreground service
(type `specialUse`) that runs while a session or the Interval timer page's run does: it combines
the running session with `LiveTimers`, turns them into a `LiveCard` (`LiveCards`, pure and unit
tested) and posts it with `NotificationCompat.ProgressStyle`, a promoted Live Update on Android 16
and a plain progress bar before. It ends a rest that ran out and buzzes. `LiveActionReceiver`
handles the buttons (Set done, ±30 s, Skip rest, Pause/Resume, Stop); Log climb and tapping the
card go through `MainActivity`'s `OpenRequest`. `CruxApplication` starts the service when a
session starts.

Training progress (`ui/progress/TrainingProgress.kt`) is worked out, not stored: `trainingProgress`
takes the finished sessions, every exercise result (`RecordRepository.observeAll`: hand-logged and
session sets), the exercise library, the Week schedule and the days with climbs, and gives the last
8 weeks (planned, done, sessions, sets, minutes, new bests), 30-day figures and each exercise's best
per day. `TrainingProgressSource` combines the flows for Progress and Home. The Home widgets
`TRAINING` and `EXERCISE` are configurable: `DashboardWidget` keeps the chosen `TrainingStat` or
`exerciseId`, and the layout JSON carries them (older layouts read as before).

An exercise has one page, `ExerciseRecordsRoute` (`ui/you/ExercisePage.kt`): its record, chart,
setup, the plans that use it (from `TemplateRepository` and the week) and its days. Every exercise
list opens it; the editor (`ExerciseEditorRoute`) is behind its pencil. `IntervalTimerRoute` takes an
optional `exerciseId` to start from that exercise's timer. Launcher shortcuts are dynamic
(`LauncherShortcuts` in `MainActivity.kt`), so they point at whichever build is installed; they open
through the same `OpenRequest` path as the live notification.

## Boards, datasets and catalogues

- **Boards** (`data/model/Boards.kt`): a board section names its `BoardModel` (Kilter Original or
  Homewall with a size and holds, a MoonBoard year, or custom). The board angle is never a field: the
  Angle chip writes it as the log note's first line (`BoardAngleNote`).
- **Datasets** (`data/DatasetRepository.kt`, `ui/datasets/`): your own lists of climbs. A facility
  uses every dataset made for its board (`Dataset.fitsBoard`) and those linked by hand
  (`section_datasets`); `datasetsFor` combines them. Log climb's Where sheet ends with a Climb step
  (`LogClimbWhere.kt`) when the facility has any; `LogClimbViewModel.pickDatasetClimb` continues the
  climb of that name there, or fills the form, converting the grade with `convertGrade`. The climb
  keeps a copy (setter, `sourceDatasetClimbId`), never a dependency.
- **Playlists** (`data/Playlists.kt`): ordered items pointing at dataset or catalogue climbs. A
  session can climb one (`sessions.playlistId`/`playlistIndex`); `PlaylistGoes` logs + Fell and
  + Sent on the climb that's up.
- **Board catalogues** (`data/Catalogue.kt`, `data/CatalogueImporter.kt`): a Boardsesh snapshot
  is read with Android's SQLite and copied into the catalogue database (`crux-catalogue.db`,
  `CatalogueDatabase`), keyed by catalogue (`boardsesh:kilter:8:25`) and the board's climb uuid. The
  main database keeps a `datasets` row (`kind` `catalogue`) for the list; climbs and per-angle grades
  stay in the catalogue file, which is rebuilt rather than migrated and never backed up.
- **Hold diagrams** (`data/BoardGeometry.kt`, `ui/components/BoardDiagram.kt`): positions from the
  built-in `assets/board_holes.json` (regenerate with `scripts/board_holes.py`); a climb's holds are
  copied onto it when logged (`problems.holds`, `holdsBoard`).
- **Board lights** (`data/BoardLights.kt`, `data/BoardLink.kt`, `ui/components/BoardLightsUi.kt`):
  pairing through `CompanionDeviceManager` (no scanning, no location), the pairing kept on the section
  (never backed up), messages built in pure Kotlin and written over Bluetooth only when Light is
  tapped. `live/LivePlaylists.kt` gives the live notification Prev, Light and Next.

## Domain decisions worth knowing

- **Grades** are a scale (`GradeScale`: Font, V, French, YDS, or a local scale) plus an index, and
  they're **never converted**. Local grades also store the label or tape colour. Bests are per
  scale, and for local scales per place. The grade converter (`GradeConversion`) is a reference
  tool only.
- **Places → sections → areas.** A place has one or more sections (facilities: gym, crag or
  board), each with its own grade scales. Walls (areas) hang off sections.
- **Climbs and logs** (schema 21). A *climb* (a `problems` row, `Problem` in code) is what's tried:
  name, discipline, grade, and optionally a place, section and wall. A *log* (a `climbs` row,
  `Climb` in code) is one day's goes on it: `attempts`, `sends`, the `style` worked out from them,
  effort, notes, media, session. Every log has a climb: `ClimbLinks.linkUnlinked` gives a log
  without one the climb of the same name at the same place (and discipline), or a new climb, named
  from the grade and where it was when the log has no name. Editing a climb rewrites its logs'
  name, grade and place (`PlaceDao.syncLogs`); deleting the last log deletes the climb. A climb has
  no page of its own: opening it opens Log climb with `problemId`, which lists its other logs.
- **Projects are derived, not flagged**: a climb with goes and no send.
- **Nothing is hidden or removed automatically.** The wall reset is gone; `problems.retired` and
  `areas.resetEpochDay` are legacy columns, still backed up, but nothing reads them.
- **Units**: stored in kg and cm. Imperial only changes what's shown.
- **Media**: photos are scaled to 2048 px JPEGs in `files/area_images`, and videos are copied as
  they are. The database stores only file names.
- **Time**: business logic takes an injected `Clock`, and UI reads `LocalClock`, never
  `LocalDate.now()` directly.

## Backups

See [Backup format](backup-format.md). In short: a zip archive with a self-describing JSON file of
optional sections, and the photos and videos it names. Records refer to each other by name, never
database id. Records already on the phone are listed first, and the climber decides each one.

## Reliability

- The climber's database is copied before every migration ([`DatabaseSnapshots`](../../app/src/main/java/com/hardtekpt/crux/data/local/DatabaseSnapshots.kt)),
  inside Room's open-helper factory, off the main thread.
- Uncaught exceptions are written to `files/crashes/` ([`CrashReports`](../../app/src/main/java/com/hardtekpt/crux/data/diagnostics/CrashReports.kt))
  and handed on. Nothing leaves the device unless the climber shares a report.
- Android Auto Backup carries the climber's database and preferences only
  ([`data_extraction_rules.xml`](../../app/src/main/res/xml/data_extraction_rules.xml)).

See also: [Project structure](project-structure.md) · [Data model](data-model.md) ·
[UI and design system](ui-and-design-system.md) · [Testing](testing.md)
