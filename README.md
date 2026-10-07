# Crux

Native Android app for planning climbing workouts, journaling climbs and tracking progress.

## Stack

Kotlin, Jetpack Compose with Material 3, Navigation Compose (type-safe routes), Hilt, Room,
DataStore, WorkManager, Coroutines/Flow with MVVM, osmdroid (OpenStreetMap) for maps and
kotlinx.serialization for backups. Charts are drawn with Compose Canvas. Versions live in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

Build: AGP 9.4, Gradle 9.7, Kotlin 2.4, compileSdk 37.2, minSdk 28. Opening the project in
Android Studio needs a version that supports AGP 9.4 (Help > Check for Updates); command-line
builds work with the wrapper alone.

## What's in the MVP

All five tabs read and write the local Room database:

- **Home**: today's plan, this week, latest best, bodyweight and recent climbs. The Log button opens
  the quick log sheet.
- **Train**: starter workout templates and a read-only detail of their blocks and targets.
- **Journal**: climbs grouped by day and place, plus the Log climb form.
- **Progress**: hardest send per discipline and per style, derived from the journal.
- **You**: weight log with 30-day change, and height.

Grades are stored as a scale plus an index (Font for boulders, French for routes). Starter templates
are seeded on every install; debug builds also seed a few weeks of sample climbs and weigh-ins.
Schema changes migrate the climber's data (Room auto-migrations, tested from every old schema);
the database is copied to `files/db-backups/` before each migration and is never wiped.

## Day-to-day commands

From the repo root on Windows:

| Command | What it does |
| --- | --- |
| `.\crux run` | Builds the debug app, boots the `Crux_Pixel_9` emulator if needed, installs and opens the app |
| `.\crux test` | JVM unit tests and Robolectric Compose UI tests (no emulator) |
| `.\crux device-test` | Instrumented Compose, Hilt and Room tests on the emulator |
| `.\crux check` | Both test suites |
| `.\crux emulator` / `.\crux stop` | Boot or shut down the emulator |
| `.\crux build` | Debug APK only |

Add `-Device <ip:port>` to `run` or `device-test` to target a phone on wireless debugging instead
(pair it once with `adb pair <ip:pairing-port> <code>`). Add `-Headless` to boot the emulator without a window (handy for test-only runs). The script
finds Android Studio's bundled JDK and the SDK in `%LOCALAPPDATA%\Android\Sdk` automatically,
and creates the emulator on first use.

## Layout

```
app/src/main/java/com/hardtekpt/crux/
  data/        Room database, DAOs, repositories, DataStore preferences
  di/          Hilt modules
  ui/          App shell, navigation, theme (placeholder until the design system lands), screens
  work/        WorkManager workers
app/src/test/         JVM + Robolectric tests
app/src/androidTest/  On-device tests (Hilt test runner, in-memory Room)
app/schemas/          Exported Room schemas, committed for migrations
```
