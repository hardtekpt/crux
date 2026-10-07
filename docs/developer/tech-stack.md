# Tech stack

Crux is a single-module, offline-first Android app written entirely in Kotlin. Versions live in
[`gradle/libs.versions.toml`](../../gradle/libs.versions.toml); the ones below are current at the time of writing.

## Platform and build

| | Version | Notes |
| --- | --- | --- |
| Kotlin | 2.4 | Through AGP's built-in Kotlin support (no separate Kotlin Android plugin) |
| Android Gradle Plugin | 9.4 | |
| Gradle | 9.7 (wrapper) | Configuration cache and build cache on |
| JDK | 21 to build (CI: Temurin 21) | Bytecode targets Java 17 |
| compileSdk / targetSdk / minSdk | 37.2 / 36 / 28 | Android 9 and newer |
| KSP | 2.3 | For Hilt and Room |

## App libraries

| Area | Library | Why |
| --- | --- | --- |
| UI | Jetpack Compose (BOM 2026.09), Material 3, Material icons | The whole UI is Compose; no XML layouts |
| Navigation | Navigation Compose 2.10, type-safe `@Serializable` routes | Nested graph per tab |
| DI | Hilt 2.60, `hilt-navigation-compose`, `hilt-work` | ViewModels, repositories, workers |
| Database | Room 2.8 with KSP, exported schemas, auto-migrations | Two databases: the climber's own and demo |
| Preferences | DataStore Preferences 1.2 | Settings and the Home layout |
| Async | Kotlin coroutines and Flow 1.11 | Repositories expose `Flow`; ViewModels expose `StateFlow` |
| Serialization | kotlinx.serialization 1.11 | Backup files, dashboard layout, routes |
| Background work | WorkManager 2.12 | Wired through Hilt for upcoming reminders |
| Maps | osmdroid 6.1 (OpenStreetMap) | No account or API key; Android's `Geocoder` for search |
| Licences screen | AboutLibraries 15.2 | Library list generated at build time, offline |
| Charts | Compose `Canvas` | Custom, no chart library |
| Fonts | Archivo and JetBrains Mono (bundled variable fonts) | |

There are no proprietary dependencies, no Google Play Services, no analytics and no crash-reporting
SDK. See [F-Droid](fdroid.md).

## Testing

| | Library |
| --- | --- |
| JVM unit tests | JUnit 4, kotlinx-coroutines-test, Turbine |
| Android on the JVM | Robolectric 4.17 (in-memory Room, Compose UI tests, native graphics) |
| Screenshot tests | Roborazzi 1.76 |
| Instrumented tests | AndroidX Test, Compose UI test, Espresso, Hilt testing, Room testing |
| Coverage | Kover 0.9 |

## Code quality and CI

| | Tool |
| --- | --- |
| Formatting | Spotless 8.10 with ktlint 1.8 (`android_studio` style, `.editorconfig`) |
| Static analysis | Android Lint with a baseline (new issues fail the build) |
| CI | GitHub Actions: CI, Device tests (emulator), Reproducible build, Release |
| Dependencies | Dependabot (weekly, grouped, into `dev`) |
| Secrets scanning | GitHub secret scanning (public repo) |

See [Testing](testing.md) and [CI/CD](ci-cd.md).
