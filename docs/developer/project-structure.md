# Project structure

```
crux/
├── app/
│   ├── build.gradle.kts            App build: SDKs, signing, lint, Kover, AboutLibraries, deps
│   ├── lint-baseline.xml           Existing lint findings (new ones fail the build)
│   ├── schemas/…/CruxDatabase/     Exported Room schema per version (4 … 20), committed
│   └── src/
│       ├── main/
│       │   ├── java/com/hardtekpt/crux/   Kotlin sources (below)
│       │   └── res/                Fonts, launcher icon, backup rules, file-provider paths
│       ├── test/                   JVM + Robolectric tests, screenshot tests
│       │   ├── resources/backups/  A real v0.1.0 backup, for compatibility tests
│       │   └── screenshots/        Screenshot goldens (light themes, Settings)
│       └── androidTest/            Instrumented tests (Hilt runner, migrations, DAOs, app flows)
├── docs/                           User guide and developer docs (you are here)
├── fastlane/metadata/android/      Store listing: texts, changelogs, icon, screenshots
├── gradle/libs.versions.toml       Every dependency and plugin version
├── scripts/
│   ├── crux.ps1                    Windows dev script (run, test, format, emulator…)
│   └── release-smoke.sh            Launch check of the release APK (CI)
├── .github/
│   ├── workflows/                  ci, device-tests, reproducible, release
│   ├── ISSUE_TEMPLATE/, PULL_REQUEST_TEMPLATE.md, dependabot.yml
├── version.properties              The app version (VERSION_NAME)
├── CHANGELOG.md, CONTRIBUTING.md, PRIVACY.md, SECURITY.md, CODE_OF_CONDUCT.md, LICENSE
└── CLAUDE.md, HANDOFF.md           Working notes for AI-assisted development
```

## Kotlin sources

```
com.hardtekpt.crux
├── CruxApplication.kt      Hilt app; installs crash reports, StrictMode (debug), seeds demo data
├── MainActivity.kt         Single activity; edge-to-edge; hosts CruxApp
├── data/
│   ├── *Repository.kt      Climb, Place, Body, Template, Exercise, Session, Note, Record
│   ├── model/              Domain types: Climbing (grades, styles), Training, Places, GradeConversion
│   ├── local/              Room: entities, DAOs, CruxDatabase (+ migration specs), CruxDatabases,
│   │                       DatabaseSnapshots
│   ├── prefs/              UserPreferencesRepository (DataStore)
│   ├── dashboard/          Home widgets, sizes, layout repository
│   ├── backup/             Backup file DTOs, export/import
│   ├── images/             AreaImageStore: copy, scale and store photos/videos
│   ├── diagnostics/        CrashReports
│   └── seed/               StarterDataSeeder (starter library + demo data), StarterData
├── di/DataModule.kt        Hilt modules: databases, DataStore, clock, crash reports, bindings
├── ui/
│   ├── CruxApp.kt          Scaffold, floating nav bar, NavHost with a graph per tab
│   ├── navigation/         Routes, floating bar, transitions, in-page tab swipe
│   ├── theme/              Colours, typography, spacing/sizes/shapes, CruxTheme
│   ├── components/         Buttons, cards, list rows, inputs; input/ = the touch input kit
│   ├── charts/             Canvas charts and chart cards
│   ├── home/ train/ journal/ session/ progress/ you/ places/ settings/ quicklog/
│   ├── timer/              The standalone, full-screen interval timer
│   ├── Format.kt           Date and number formatting
│   └── Units.kt            Metric/imperial display, LocalUnits, LocalGradeScales, LocalClock
└── work/                   WorkManager workers
```

## Where to start reading

| To understand… | Read |
| --- | --- |
| How the app is wired | `ui/CruxApp.kt`, `di/DataModule.kt` |
| Data flow for one screen | `ui/home/HomeViewModel.kt` → `data/ClimbRepository.kt` → `data/local/ClimbDao.kt` |
| Places and grades | `data/model/Climbing.kt`, `data/model/Places.kt`, `data/PlaceRepository.kt` |
| Live sessions | `data/SessionRepository.kt`, `ui/session/SessionScreen.kt`, `ui/session/IntervalTimer.kt` |
| The standalone timer | `ui/timer/IntervalTimerScreen.kt` (reuses `IntervalRun` and the session's timer pieces) |
| Migrations | `data/local/CruxDatabase.kt`, [Database](database.md) |
| Tests | [Testing](testing.md) |
