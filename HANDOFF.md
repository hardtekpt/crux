# Crux — handoff

## Goal
Crux is a native Android app for climbers: plan workouts (custom exercises and session plans),
keep a climbing journal (climbs at saved places, walls and problems, projects, photos and
video), and track the climber (weight, body stats, circumferences, personal records, notes,
consistency). Built for Boss's Pixel 8a, with an emulator for testing.

## Where things are
- Repo: `C:\Users\ffvd\Projects\crux` (git, branch `main`, no remote). Commit messages end with
  `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>`.
- Dev script: `.\crux.cmd run` (emulator `Crux_Pixel_9`), `.\crux.cmd run -Device 192.168.1.128:<port>`
  (phone over wireless debugging; the port changes, last one was **42117** — ask Boss when
  `adb connect` is refused), `.\crux.cmd test`, `.\crux.cmd device-test` (emulator only).
- Gradle from a shell needs `JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'`.
- **Never run device tests on the phone**: connected tests uninstall the app (wipes data).
  Device tests also uninstall the app from the emulator; reinstall afterwards.
- Design system artifact: https://claude.ai/artifact/7T76aAwmEZHfY339ZaLZ4Q
  Input kit preview: https://claude.ai/artifact/XGd4dKtVPqvnSBhZerD7Ea

## Process (current)
Test runs and installs go through two dedicated project threads, relayed by the coordinator
session (`send_message` to `@parent`): **Tests** and **Phone and emulator**. Commit before any
handoff (the runners use the shared checkout or a clean clone of the commit). Ask for device
tests first and the install only after they finish (tests uninstall the app from the emulator).

## Stack
Kotlin 2.4, AGP 9.4 (built-in Kotlin), Compose (BOM 2026.09), Material 3, Navigation Compose
(type-safe, nested graph per tab), Hilt, Room 2.8 with exported schemas and auto-migrations
(now **schema 13**), DataStore prefs, kotlinx.serialization, osmdroid (OpenStreetMap, no key).
Two databases: the climber's own and a demo one (Settings → Demo mode).

## What's done (latest first)
- `b5f1d55` **Profile page** (You tab): climber card (tape strip, days/climbs this year, hardest
  boulder/route), consistency grid (GitHub-style, weekly streaks), personal records per exercise
  (log a result; per-exercise history; PR = heaviest load, then reps, or longest hold), measurements
  (weight + trend, body stats, new circumferences: forearm, bicep, chest, waist, thigh), notes
  (new table; editor with pin/delete; "Add a note" in the Log menu now works), preferences summary
  → Settings. Notes and records are in backups. **Waiting on**: device test run, then install and
  screenshots of the You tab (Demo mode on) for a visual check before reporting to Boss.
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
- Grades are stored as scale + index (+ label for local scales) and never converted.
- Places: one local scale per place; climbs copy a problem's grade but stay editable.
- Projects are derived (no flag): a non-retired problem with goes and no send.
- Retired = taken down: hidden from pickers/projects, history kept. Wall Reset retires its problems.
- Images/videos are copied into app storage (`filesDir/area_images`); photos are scaled to 2048 px
  and included in backups as base64; **videos are not backed up** (too large).
- Map is OpenStreetMap (no account/key). Google Maps tiles would need a Maps API key from Boss.
- Imperial is display-only; data stays kg/cm. Added load on exercises stays in kg.
- Personal records are entered by hand until a session logger exists.

## What's left / next steps
1. Confirm `b5f1d55` (device tests, install, screenshots), fix anything visual, then report the
   profile page to Boss with a short layout description so they can steer.
2. Session logger ("Start a workout" in the Log menu is still disabled): run a plan, log sets;
   then derive exercise PRs from logged sets and add sessions to the consistency grid and backups.
3. Optional ideas offered to Boss, not yet asked for: manual "project" flag on problems; swipe on
   the Places type filters; Google Maps tiles (needs a key); video backup; add the input-kit
   components to the design system artifact (step 5 of that plan).
4. Housekeeping: `MainActivityTest` dashboard drag test was flaky before `33bdbc3`; keep an eye on it.
