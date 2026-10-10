# Changelog

All notable changes to Crux. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and versions follow [Semantic Versioning](https://semver.org/). Each release also has short notes in
`fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`, which F-Droid shows.

## [Unreleased]

### Added

- **Compact timer.** The rest and interval timer band can shrink to one slim line, and stays that
  way until you open it up again.
- **Consistency widget** for Home: the grid of climbing days from the You page, with your week
  streak and days in the last 30.
- **Everything in backups.** Session history (every set, and the climbs logged in it), your
  settings and Home layout, and climb videos now go into backups too. Backups are now `.zip` files;
  the `.json` backups you already have still import.
- **Photos and videos switches** for backups, so you can leave them out to keep the file small.
- **Days on the wall page**, from the Home widget: this week, month and year, your week streak,
  and a month calendar of your climbing days. Tap a day to see its journal.
- **+1 go** on projects in Home and Progress: one tap logs an attempt on the problem today, with
  Undo.

### Changed

- **Latest best** on Home is your newest hardest send: the latest send harder than all before it,
  in any style, so an easier flash no longer replaces a harder redpoint.
- **Log a go on a tried problem** starts as an attempt; Flash and Onsight aren't offered for it.
- **A session's goes on the same problem** show as one entry with the goes added up, in the live
  session, the Journal and the session summary.
- **Backups page.** Export, the photo and video switches and import moved from the Settings list to
  their own page, *Settings → Backups*.
- **Import asks about duplicates.** When something in a backup is already in Crux, you see both and
  choose: skip it, replace it with the backup's, or keep both. One answer can cover the rest of a
  section. Replacing a place brings in the walls and problems it's missing.

### Fixed

- **Goes on a project add up.** Logging or editing a climb on a problem you've tried before shows
  this time's goes, the earlier ones and the total; a redpoint no longer has to be two goes that
  day.
- **The Journal's day tally** counts goes on one problem in a session once, like the session card.
- **A problem opened from Progress or Home** keeps that tab lit, not You.
- **Backups keep the place name you logged**, and a place renamed since no longer makes its climbs
  import twice.
- **Log climb remembers where you were.** A new climb starts at the last climb's place, facility
  (gym, board) and wall, instead of going back to the place's first facility with no wall.

## [0.2.0] - 2026-10-07

### Added

- **Live sessions.** Start one of your plans, or a climbing day without a plan, from the Log menu,
  a plan's page or the *Today's plan* widget. Log each set against its target, with a rest timer
  (+30 s, Skip, beeps at the end). Add exercises along the way, log climbs inside the session, and
  finish with how it felt and a note. Leaving keeps the session running behind a pill above the
  bottom bar.
- **Interval timer** for interval exercises, with preparation, work, rest, repeats, cycles and rest
  between cycles. It logs each finished cycle as a set and stays right with the screen off. A
  free timer has Tabata and hangboard-repeater presets.
- **Session summaries** in the Journal: length, effort, every set against the plan, the climbs and
  the note.
- **Exercise defaults.** Every exercise has the sets, reps or time, rest and load that plans and
  sessions start it at. Interval exercises set up their timer here.
- **Places with several kinds of climbing**, as named facilities (say *Main gym* and *Kilter
  board*), each graded in its own scales.
- **Place page**: a banner, labelled numbers, facility tiles to filter walls, and *Log here*.
- **Body outline figures** on Measurements and Circumferences, with reach, weight and body fat.
- **Added load in pounds** under Imperial.
- **Crash reports**, saved on the phone and shared only if you choose (*Settings → Diagnostics*).
- **About** screen: version, licence, source code and the open-source libraries Crux uses.

### Changed

- Crux is now **free and open-source software** under the GNU GPL v3 or later.
- Home's *Projects* and *Recent climbs* widgets are calmer flat lists.
- Android's own backup now carries your database and settings, but not the demo data, and leaves
  photos and videos to phone-to-phone transfer (cloud backup has a 25 MB limit).

### Fixed

- **Your data is never wiped by an update.** Before an update changes how data is stored, Crux
  copies the database on your phone, and a missing upgrade step now stops the app instead of
  starting over.
- Demo data refills itself when the sample data changes.
- Two body-stat values entered at the same moment now always show the newer one.

### Under the hood

- Screenshot tests of the main screens in both themes; instrumented tests on an emulator before
  every release; a launch check of the minified release build; reproducible builds.
- ktlint, Android Lint and coverage in CI, and Dependabot.
- Unused Health Connect and Vico libraries removed.

## [0.1.0] - 2026-10-06

The first release.

### Added

- **Train**: an exercise library (reps, time, with or without load, intervals) and session plans
  in blocks, with targets set on touch-friendly wheels.
- **Journal**: one timeline of climbs, training results and notes, with search and filters (type,
  period, sends, places, tags).
- **Log climb**: grade strip, style, attempts, effort 1–10, day strip and calendar, name, notes,
  a photo and a video. Pick the place, wall and problem in three steps.
- **Places**: gyms, crags and boards with walls (with a photo or map image), problems with tape
  colours, local grade scales (numbers or colour tapes), favourites, a map pin with address
  search (OpenStreetMap), and automatic projects.
- **Progress**: hardest sends per grade scale and style, grade pyramids, sends per week and by
  style, open projects.
- **You**: a climber card, a consistency grid with weekly streaks, personal records per exercise,
  weight with trend, body stats with ape index, circumferences (left and right), notes with tags
  and pins, and a grade converter (French, YDS, UIAA, British, Ewbank, Font, V).
- **Home**: an editable dashboard of widgets (add, remove, resize, drag).
- **Grades**: Font, V, French and YDS, chosen per discipline and never converted.
- **Backups**: export and import per section, photos included; importing only adds.
- **Demo mode** with a separate sample data set.
- **Units**: metric or imperial.
- **Design**: a dark-first design system with a light theme, a floating tab bar with Log built in,
  and quiet transitions.

[Unreleased]: https://github.com/hardtekpt/crux/compare/v0.2.0...dev
[0.2.0]: https://github.com/hardtekpt/crux/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/hardtekpt/crux/releases/tag/v0.1.0
