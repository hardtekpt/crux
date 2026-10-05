# Crux

Native Android app for planning climbing workouts, journaling climbs and tracking progress.

## Stack

Kotlin, Jetpack Compose with Material 3, Navigation Compose (type-safe routes), Hilt, Room,
DataStore, WorkManager, Coroutines/Flow with MVVM, Vico for charts and Health Connect for body
metrics. Versions live in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

Build: AGP 9.4, Gradle 9.7, Kotlin 2.4, compileSdk 37.2, minSdk 28. Opening the project in
Android Studio needs a version that supports AGP 9.4 (Help > Check for Updates); command-line
builds work with the wrapper alone.

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

Add `-Headless` to boot the emulator without a window (handy for test-only runs). The script
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
