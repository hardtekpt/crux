# Crux — handoff

## Goal
Crux is a native Android app for climbers: plan workouts (custom exercises and session plans),
keep a climbing journal (climbs at saved places, walls and problems, projects, photos and
video), and track the climber (weight, body stats, circumferences, personal records, notes,
consistency). Built for Boss's Pixel 8a, with an emulator for testing.

## Where things are
- Repo: `C:\Users\ffvd\Projects\crux` (git, remote `hardtekpt/crux`; work on `dev`, release by merging to `main` — see CLAUDE.md). Commit messages end with
  `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>`.
- Dev script: `.\crux.cmd run` (emulator `Crux_Pixel_9`, debug build), `.\crux.cmd test`,
  `.\crux.cmd device-test` (emulator only).
- **The phone runs the `internal` build** (release-like: R8, baseline profile, not debuggable; same
  app id and key as debug, so data stays). Install it with `./gradlew :app:assembleInternal` and
  `adb install -r app/build/outputs/apk/internal/app-internal.apk` to `192.168.1.128:<port>` over
  wireless debugging. The phone's address changes between `192.168.1.128` and `192.168.1.242`, and the
  port changes every time wireless debugging restarts: ask Boss for it when `adb connect` is refused. Never install `app-debug.apk` on the phone. Because it isn't debuggable, `run-as`
  backups don't work: ask Boss to export from Settings → Backups before a risky install.
- Gradle from a shell needs `JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'`.
- **Never run device tests on the phone**: connected tests uninstall the app (wipes data).
  Device tests also uninstall the app from the emulator; reinstall afterwards. The emulator stays
  on the debug build.
- Design system artifact: https://claude.ai/artifact/7T76aAwmEZHfY339ZaLZ4Q
  Input kit preview: https://claude.ai/artifact/XGd4dKtVPqvnSBhZerD7Ea

## Process (current)
- The main app thread builds features, runs `spotlessCheck verifyRoborazziDebug lintDebug` and the
  device tests on `emulator-5554` (`ANDROID_SERIAL`), commits on `dev`, pushes, and installs the
  `internal` build on the emulator and the phone.
- The **Crux quick fixes** thread takes small doc, test and app map pieces, relayed by the
  coordinator session (`send_message` to `@parent`). It owns `docs/app-map/` and captures on the
  second emulator (`emulator-5556`); report page changes to it so the map stays complete.
- The shared checkout is used by several sessions at once: pull before starting, and commit with
  explicit paths when someone else has work in progress.
- **Before installing a build that changes the database on the phone, Boss makes a backup**
  (Settings → Backups). Never merge or push `main` without an explicit release ask.

## Stack
Kotlin 2.4, AGP 9.4 (built-in Kotlin), Compose (BOM 2026.09), Material 3, Navigation Compose
(type-safe, nested graph per tab), Hilt, Room 2.8 with exported schemas and auto-migrations
(now **schema 31**), DataStore prefs, kotlinx.serialization, osmdroid (OpenStreetMap, no key).
Two databases: the climber's own and a demo one (Settings → Demo mode), plus a separate catalogue
database (`crux-catalogue.db`) for imported board catalogues, which is never backed up.

## What's done (latest first)

Since 0.4.0 (all on `dev`, not released yet; see CHANGELOG *Unreleased*):
- **Board integrations** (plan: https://claude.ai/artifact/MFD5uiGt6EB7SgMN1y3Hkv):
  - **Board lights** (schema 31, `91a042c`): pair a Kilter or MoonBoard from its facility in Edit
    place (`CompanionDeviceManager`, Nearby devices only, no location), and tap **Light** on a
    catalogue climb, in Log climb or on the session playlist card; the playlist's live notification
    has Prev, Light and Next. Nothing lights by itself (Boss's choice). `BoardLights` encodes Aurora v2/v3
    and the MoonBoard text command (unit-tested); `BoardLink` owns the Bluetooth link. **Not yet tried
    on real boards**: Boss will test on a Kilter Original and a MoonBoard.
  - **Hold diagrams** (schema 30, `a00d84c`): `BoardDiagram` draws a climb's holds from
    `assets/board_holes.json` (positions, sizes and LED numbers, made by `scripts/board_holes.py`
    from Boardsesh's hardware catalogue; built in by Boss's choice). Logging copies holds onto the climb.
  - **Board catalogues** (schema 29, `2e281ae`): import a Boardsesh snapshot (`.db`/`.db.gz`) for a
    Kilter Original/Homewall (kept to your size) or any MoonBoard; browse at each angle, log, add to
    playlists, pick in the Climb step. Climbs live in the catalogue database; backups keep only which
    catalogue climb yours came from (by the board's uuid).
  - **Climb step and playlists** (schema 28, `37aae2b`): datasets link to facilities (same board
    automatically, others by hand); Log climb's Where sheet ends with a Climb step, a linked name row
    and a grade hint; playlists with a session card (+ Fell, + Sent, Prev, Next).
  - **Datasets** (schema 27, `c35e9f4`): your own climb lists under You → Datasets, CSV and
    `.crux-dataset.json` import and export, in backups.
  - **Boards on facilities** (schema 26, `2d30bc7`): board model and size, and the Angle chip that
    writes the angle as the log note's first line.
- **One page per exercise**, Also change the plan, Places in the Journal, a My gym widget and
  launcher shortcuts (`0699482`).
- **Training progress**: a Training tab in Progress, exercise charts, Training and Exercise widgets.
- **A simpler live session**: one list of exercise cards, a dock for Done, rests and timers, the rail.
- **App map** (`docs/app-map/`, https://claude.ai/artifact/KGk3CtYtKBdnFbf6dfopov): a screenshot of
  every screen, sheet and dialog with arrows; CI's `check.py` fails when one is missing.
- **CSV export**, a faster Home and Journal, and the release benchmarks.

Released in 0.4.0 and before:
- **Live notifications** (`crux.live`): while a session runs, a Live Update on Android 16 (segmented
  bar, status-bar chip) or a plain progress notification on 8 to 15. Plan sessions (Set done, then the
  rest countdown with +30 s, −30 s, Skip), climbing days (Log climb), and the interval timer (Pause,
  Resume, Stop), which the Interval timer page shares through `LiveTimers`.
- **Automatic backups**: daily or weekly to a folder chosen with the system picker, newest 3, 5 or
  10 kept, own data only (never the demo); Backups page has the settings and *Back up now*.
- **Weekly schedule** (schema 25): a Week tab on Train, and Home's Today's plan follows it.
- **Performance**: an `internal` build type, a baseline profile (`baselineprofile` module), data
  built off the main thread, a smoother Journal and lighter animations.
- **Sessions**: run a plan or a climbing day, log sets, rests, interval timers, climbs; finished
  sessions can be edited; sets done in sessions count toward personal records (read-only there).
  **Backups** now include sessions, the schedule, settings and videos, and ask about duplicates.
- **Profile rework + Journal timeline** (schema 14): You tab = climber card, consistency, records,
  compact weight tracker (sparkline + Log), and a menu list → Measurements (body stats, weight chart,
  weigh-ins), Circumferences (left/right for forearm, bicep, thigh; chest, waist), Notes, Places
  (moved out of Journal; place/problem pages now live in the You graph), Settings. Notes have an
  optional tag (chips in the editor, filter on Notes). Journal is one timeline (climbs per place,
  training results, sessions, notes) with All/Climbs/Training/Notes filters.
  `FOREARM`/`BICEP`/`THIGH` mean the left side.
- `b5f1d55` **Profile page** (You tab): climber card (tape strip, days/climbs this year, hardest
  boulder/route), consistency grid (GitHub-style, weekly streaks), personal records per exercise
  (log a result; per-exercise history; PR = heaviest load, then reps, or longest hold), measurements
  (weight + trend, body stats, new circumferences: forearm, bicep, chest, waist, thigh), notes
  (new table; editor with pin/delete; "Add a note" in the Log menu now works), preferences summary
  → Settings. Notes and records are in backups.
- `33bdbc3` Fixed a real dashboard bug: a removed widget's old position could swallow a drag.
  The dashboard drag device test should now pass reliably (to be confirmed by the Tests thread).
- Places: custom place cards (banner from wall photo / map / type art; climbs, walls, open
  projects), Crag/Gym/Board filters, favourites (star in editor; quick picks on Log climb),
  map location (osmdroid picker, address search via the phone's geocoder, Open in Maps), local
  grade scales per place (numbers or colour tapes; never converted; bests kept per place),
  walls with optional photo/map image, problems, projects (automatic: tried, not sent).
- Log climb: Where as one summary line opening a stepped sheet (place → wall → problem), grade
  strip, day strip, effort 1–10, photo and video attachments (climb_media table), edit/delete.
- Touch input kit: wheels (number, duration, load), rulers for body stats, grade/day strips,
  hold-to-repeat steppers, effort scale, tap-to-type everywhere; Metric/Imperial setting.
- Train: exercise library and session plans (incl. Tabata/intervals) with inline wheel rows.
- Home: editable dashboard (add/remove/resize/drag widgets, incl. Projects widget).
- Progress: hardest sends per scale, charts, open projects.
- Backup export/import per section; demo mode; dark-first theme; plain-fade navigation; swipe
  between in-page tabs (Journal Climbs/Places, Train Plans/Exercises — not between main tabs).

## Key decisions
- **The climber's database is never wiped.** Only the demo DB keeps the destructive-migration
  fallback. Before a migration, the real DB is copied to `files/db-backups/crux-user-v<old>.db`
  (newest 3 kept). Every schema bump needs an auto-migration, its exported JSON (checked by
  `SchemaExportTest`) and passing `MigrationTest` (which migrates every old schema to the latest).
- Android Auto Backup carries the real DB and preferences only (`res/xml/data_extraction_rules.xml`);
  photos/videos go in phone-to-phone transfer and Crux's own export, the demo DB is left out.
- Grades are stored as scale + index (+ label for local scales) and never converted.
- Places: one local scale per place; climbs copy a problem's grade but stay editable.
- Projects are derived (no flag): a non-retired problem with goes and no send.
- Retired = taken down: hidden from pickers/projects, history kept. Wall Reset retires its problems.
- Images/videos are copied into app storage (`filesDir/area_images`); photos are scaled to 2048 px.
  Backups include photos and videos by default, each with its own switch on the Backups page.
- Map is OpenStreetMap (no account/key). Google Maps tiles would need a Maps API key from Boss.
- Imperial is display-only; data stays kg/cm. Added load on exercises stays in kg.
- Personal records are results logged by hand plus sets done in sessions (merged on read; session
  sets have negative ids and can only be changed in their session).

- **Board data**: Crux never bundles climb catalogues or board images and never logs into board
  accounts; catalogues come from files Boss downloads. Only hole positions and LED numbers are
  built in. Board angle is the log note's first line, never a field.
- A climb picked from a dataset or catalogue is a normal Crux climb: name, grade, setter and holds
  are copied; deleting the dataset or catalogue never touches logs.

## What's left / next steps
1. **Install tier 7 and board lights on the phone** (needs the new debugging port), then Boss's
   hardware checks on a Kilter Original and a MoonBoard: pair, Test lights, light a climb, the
   MoonBoard *Check a hold*, and Prev/Light/Next from the notification. Expect fixes on the
   MoonBoard side (less proven).
2. **Kilter history import** (the last board plan item): old ascents from an Aurora JSON export or a
   BoardLib CSV as normal Crux logs. Needs a sample file from Boss.
3. **Release 0.5.0** when Boss asks: everything above is unreleased (`version.properties` is 0.4.0).
4. Roadmap (https://claude.ai/artifact/H57FewvRiW8Sryeci5vg7G): first run setup, accessibility
   pass, goals, Android home screen widgets; later boards (Tension and other Aurora boards), setting
   holds on your own board climbs, custom boards from a photo, smart playlists. Training cycles are
   parked by Boss.
5. Housekeeping: keep the app map complete (report page changes to the quick fixes thread).
