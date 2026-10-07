# Development setup

## Prerequisites

- **JDK 21.** Android Studio's bundled JBR works. On Windows the `crux` script finds it on its own.
- **Android SDK** with platform 37 (minor 2) and recent build-tools. Android Studio installs these.
- **Android Studio**: a version that supports AGP 9.4 (*Help → Check for Updates*). Command-line
  builds need only the Gradle wrapper.
- For device tests: an **emulator**. The script creates `Crux_Pixel_9` on first use.

## Get the code

```sh
git clone https://github.com/hardtekpt/crux.git
cd crux
git switch dev        # all work happens on dev; main only moves on releases
```

If Gradle can't find the SDK, create `local.properties` with `sdk.dir=/path/to/Android/Sdk`. On
Windows, escape the colon: `sdk.dir=C\:/Users/you/AppData/Local/Android/Sdk`.

## Build and run

```sh
./gradlew assembleDebug                                 # debug APK (app id com.hardtekpt.crux.debug)
./gradlew spotlessCheck verifyRoborazziDebug lintDebug  # what CI checks
./gradlew spotlessApply                                 # fix formatting
./gradlew recordRoborazziDebug                          # re-record screenshots after an intended UI change
./gradlew connectedDebugAndroidTest                     # instrumented tests, on an emulator
```

The debug build installs next to a release build: its app id ends in `.debug`.

### The `crux` script (Windows)

[`scripts/crux.ps1`](../../scripts/crux.ps1), run as `.\crux`:

| Command | What it does |
| --- | --- |
| `.\crux run` | Builds the debug app, boots the emulator if needed, installs and opens it |
| `.\crux test` | ktlint, JVM unit and screenshot tests, Android Lint (no emulator) |
| `.\crux format` | Fixes ktlint formatting |
| `.\crux screenshots` | Re-records the screenshot goldens |
| `.\crux device-test` | Instrumented tests on the emulator |
| `.\crux check` | `test` and `device-test` |
| `.\crux emulator` / `.\crux stop` | Boot or shut down the emulator (`-Headless` for no window) |
| `.\crux build` | Debug APK only |

Add `-Device <ip:port>` to `run` to target a phone over wireless debugging (pair once with
`adb pair <ip:pairing-port> <code>`).

## Two rules that protect real data

- **Never run device tests on a phone that holds data you care about.** Connected tests uninstall
  the app first, and that wipes its data. `.\crux device-test` pins the emulator through
  `ANDROID_SERIAL`. If you run tests by hand with a phone attached, pass `-s emulator-5554`.
- **Never add `fallbackToDestructiveMigration` to the climber's database.** See
  [Database](database.md).

## Working with the demo data

Turn on *Settings → Demo mode* in the app to work with realistic data without touching your own.
Sample data comes from [`StarterDataSeeder`](../../app/src/main/java/com/hardtekpt/crux/data/seed/StarterDataSeeder.kt).
Bump its `SAMPLE_DATA_VERSION` when you change it, and the demo database refills on the next launch.

## Debugging aids

- **StrictMode** is on in debug builds (log only): disk or network work on the main thread, and
  leaked closeables, show in logcat under `StrictMode`.
- **Crash reports** are saved on the device (`files/crashes/`). Read them with
  `adb shell run-as com.hardtekpt.crux.debug ls files/crashes`, or share them from
  *Settings → Diagnostics*.
- **Pre-migration database copies** are kept in `files/db-backups/`.

Next: [Architecture](architecture.md) · [Project structure](project-structure.md) · [Testing](testing.md)
