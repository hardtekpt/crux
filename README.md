# Crux

[![CI](https://github.com/hardtekpt/crux/actions/workflows/ci.yml/badge.svg?branch=dev)](https://github.com/hardtekpt/crux/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/hardtekpt/crux)](https://github.com/hardtekpt/crux/releases/latest)
[![License: GPL v3+](https://img.shields.io/badge/license-GPL--3.0--or--later-blue)](LICENSE)

A free, open-source Android app for climbers: plan your training, keep a climbing journal and
track your progress. No account, no ads, no tracking. Your data stays on your phone.

## What it does

- **Home**: a dashboard you arrange yourself, with today's plan, this week, recent climbs,
  projects, bodyweight and progress charts.
- **Train**: your own exercise library and session plans, including Tabata and interval timing.
- **Journal**: one timeline of climbs, training results and notes. Log a climb at a saved place,
  wall and problem with its grade, style, effort, and a photo or video.
- **Places**: gyms, crags and boards with their walls and problems, a map location
  (OpenStreetMap), and the place's own grade scale or colour tapes. Projects are worked out for you:
  problems you've tried but not sent yet.
- **Progress**: hardest sends per grade scale, charts over time and open projects.
- **You**: a climber card, a consistency grid, personal records per exercise, weight, body stats,
  circumferences and notes.
- **Backups**: export everything (or chosen sections) to a JSON file and import it again. Photos
  are included; videos are not.
- **Demo mode**: explore with sample data, kept fully apart from your own.
- Metric or imperial units, Font/V/French/YDS grades (never converted), and a dark-first theme.

## Install

Crux needs Android 9 or newer.

1. Download `crux-vX.Y.Z.apk` from the [latest release](https://github.com/hardtekpt/crux/releases/latest).
2. Open it on your phone and allow installing from that source when Android asks.

Updates install over the old version and keep your data. Every release is signed with the same
key. Its certificate SHA-256 is:

```
a2:8e:87:81:7c:00:0b:9f:3c:8d:d2:42:98:03:60:5e:2d:78:f7:6e:af:9c:7c:63:56:67:6b:fe:2b:3c:cc:36
```

You can check a download against the `.sha256` file next to it, or with
`apksigner verify --print-certs crux-vX.Y.Z.apk`. An F-Droid listing is on the way.

## Privacy

Everything you enter is stored only on your phone. Crux uses the network for two things, both only
when you use them: map tiles from OpenStreetMap, and address search through your phone's geocoder.
Location is asked for only when you tap *My location* on the map. See [PRIVACY.md](PRIVACY.md).

## Building

Kotlin, Jetpack Compose with Material 3, Navigation Compose (type-safe routes), Hilt, Room,
DataStore, WorkManager, Coroutines/Flow with MVVM, osmdroid (OpenStreetMap) for maps and
kotlinx.serialization for backups. Charts are drawn with Compose Canvas. Versions live in
[`gradle/libs.versions.toml`](gradle/libs.versions.toml).

Build: AGP 9.4, Gradle 9.7, Kotlin 2.4, compileSdk 37.2, minSdk 28, JDK 21. To open the project in
Android Studio, you need a version that supports AGP 9.4. Command-line builds work with the
wrapper alone:

```sh
./gradlew assembleDebug                                 # debug APK
./gradlew spotlessCheck verifyRoborazziDebug lintDebug  # what CI checks (unit + screenshot tests)
./gradlew recordRoborazziDebug                          # re-record screenshots after a UI change
./gradlew spotlessApply                                 # fix formatting
./gradlew connectedDebugAndroidTest                     # on an emulator: uninstalls the app first!
```

On Windows, the `crux` script wraps the day-to-day loop:

| Command | What it does |
| --- | --- |
| `.\crux run` | Builds the debug app, boots the `Crux_Pixel_9` emulator if needed, installs and opens the app |
| `.\crux test` | ktlint, JVM unit and screenshot tests, Robolectric Compose UI tests and Android Lint (no emulator) |
| `.\crux format` | Fixes ktlint formatting |
| `.\crux screenshots` | Re-records the screenshot goldens after an intended UI change |
| `.\crux device-test` | Instrumented Compose, Hilt and Room tests on the emulator |
| `.\crux check` | Both test suites |
| `.\crux emulator` / `.\crux stop` | Boot or shut down the emulator |
| `.\crux build` | Debug APK only |

To use a phone on wireless debugging instead, add `-Device <ip:port>` to `run`. Pair the phone once
with `adb pair <ip:pairing-port> <code>`. Add `-Headless` to boot the emulator without a window.
The script finds Android Studio's bundled JDK and the SDK in `%LOCALAPPDATA%\Android\Sdk`, and it
creates the emulator the first time.

Never run device tests on a phone that holds data you care about. Connected tests uninstall the
app first, and that wipes its data.

## Layout

```
app/src/main/java/com/hardtekpt/crux/
  data/        Room database, DAOs, repositories, DataStore preferences, backups, crash reports
  di/          Hilt modules
  ui/          App shell, navigation, theme, screens
  work/        WorkManager workers
app/src/test/         JVM + Robolectric tests
app/src/androidTest/  On-device tests (Hilt test runner, in-memory Room, migrations)
app/schemas/          Exported Room schemas, committed for migrations
docs/                 Architecture and database notes
```

[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) explains how the pieces fit together.
[docs/DATABASE.md](docs/DATABASE.md) is the checklist for schema changes.

## Contributing

Bug reports, ideas and pull requests are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md) and the
[Code of Conduct](CODE_OF_CONDUCT.md). If Crux crashes, *Settings → Diagnostics → Crash reports*
lets you share the report to attach to an issue.

## License

Copyright (C) 2026 Francisco Santos

Crux is free software: you can redistribute it and/or modify it under the terms of the GNU General
Public License as published by the Free Software Foundation, either version 3 of the License, or
(at your option) any later version. It is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
PARTICULAR PURPOSE. See [LICENSE](LICENSE) for the full text.
