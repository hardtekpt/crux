# Changelog

All notable changes to Crux. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and versions follow [Semantic Versioning](https://semver.org/). Each release also has short notes in
`fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`, which F-Droid shows.

## [Unreleased]

### Added

- **Change an exercise mid-session**: its ⋯ menu changes its sets, reps or time, load and rest for
  that session; your plan stays as it is. **Add set** adds one more set, and an exercise with
  nothing logged can be taken out.
- **Export as a spreadsheet**: save your climbs or your session sets as a `.csv` file from
  *Settings → Backups*, to open in Excel, Google Sheets or any spreadsheet app.

### Changed

- **A simpler live session**: every exercise in one list, each with a table of its sets showing
  last time's values. Tap a value to change it in place and tick the set. A dock at the bottom
  holds Done for the next set, the rest countdown and the interval timer; climbs, extra exercises
  and timers sit behind the **+** in the top bar.
- **Smoother Home and Journal**: Home builds only the widgets on screen as you scroll, and the
  Journal's search waits until you stop typing, then filters once.

### Fixed

- **Home crashed on open** with some widgets (a dev build after 0.4.0 only): full-width widgets
  lay out again as they did in 0.4.0.

## [0.4.0] - 2026-10-14

### Added

- **Session sets count as personal records**, and a set that beats your best shows **New best**
  in the session.
- **Automatic backups**: daily or weekly to a folder you choose, keeping the newest few; your own
  data only, never the demo.
- **Live notification** while a session runs: on Android 16 a Live Update with a segmented
  progress bar and a status-bar chip, on Android 8 to 15 a plain progress bar. It has a button for
  the next step (Set done, Log climb, or Pause and Stop on the interval timer), and a rest shows as
  a countdown with +30 s, −30 s and Skip rest. The Interval timer page gets the same card.
- **Weekly schedule**: *Train → Week* lays out Monday to Sunday. Tap a day to put plans and a
  climbing day on it; the week repeats. Days turn green as you finish them, and Home's Today's plan
  follows the week, with a strip of the week and what's next on a rest day. Saved in backups with
  your plans.
- **Log more goes from a session**: tap a climb in a live session to add goes to it.
- **Edit a finished session** from its summary: its day, start and finish time, how hard it felt
  and the note.
- **Rest after a climb**: set a rest in Settings (1 to 5 min) and logging a climb in a session
  starts it.
- **Exercise defaults on the exercise list**: each card shows its sets, reps or time, load and rest.
- **Interval timer page**, under *You*: a full-screen timer for the gym. Pick Tabata, Repeaters,
  Max hangs or 4×4s, or set your own; then the phase's colour fills the screen, the seconds are as
  big as they can be, and big buttons pause, skip and go back. Works on its side too.
- **More accent colours**: Magenta, Slate and Chalk, alongside Teal, Blue, Violet and Pink.

### Changed

- **Tighter privacy**: Crux asks only for approximate location (to centre the map), no longer
  sends anything to Android's cloud backup (your own backups and phone-to-phone transfer still
  work), and drops two permissions libraries asked for. PRIVACY.md lists every permission and why.
- **Faster and smoother**: Crux starts faster (it ships a baseline profile), screens work out
  their data off the main thread and fade in once it's ready instead of showing empty values,
  the Journal scrolls more smoothly, and photos are decoded once. Light animations show what
  changed: Journal entries slide into place, counts roll, tabs and the week's days blend to their
  new state, and the go pads press in.
- **Grades in one card** in Settings: bouldering and routes share a card.
- **Measurements** no longer has a + in its header; the Log button under the weight does the same.
- **Projects widget on Home** no longer has the **+1 go** button. Tapping a project on Home or Progress opens its log from today to add goes to, or a new log if you haven't tried it today. Progress and the problem page still have **+1 go**.
- **Tidier "More about you" list**: row titles such as "Interval timer" stay on one line; the grey text beside them shortens instead.
- **More room on Home**: no title bar; the dashboard starts with the date, and the edit button
  sits at its end.
- **No more board angle**: logging, places and walls no longer ask for an angle. Angles you logged
  are kept: each climb's is added to its note ("Board angle 40°"), and a wall's to its name.
- **A lighter Journal header**: search and filters sit in the top bar, the types are tabs, and
  today is the first day on the timeline, so the timeline starts right under the tabs. Swipe
  sideways to move between the tabs.

### Fixed

- **Live notification bar**: long interval timers showed one plain bar; the bar now keeps its
  coloured segments within what Android draws (ten segments, four marks).
- **Board climbs on the place page**: a facility with no walls, like a Moonboard, showed 0 climbs.
  Each climb now belongs to a facility, so counts and the facility filter include climbs on no
  wall, and logging more goes on a climb keeps it at its facility instead of the place's first.
- Climbs with the same name at two facilities of a place stay separate climbs.
- **A climb's name, grade and place are the climb's**: changing them while logging or editing a log
  changes the climb, and all its logs follow. Editing where a log was no longer splits it off into
  another climb. Logs saved at another facility than their climb's (like Moonboard climbs logged
  under the gym) move to the climb's facility; nothing is deleted.


## [0.3.0] - 2026-10-10

### Added

- **Accent colour.** Pick teal, blue, violet or pink in Settings → Appearance. It colours buttons,
  selected options and highlights, in dark and light.
- **Text size.** Make the text in Crux smaller or larger in Settings → Appearance, on top of your
  phone's own font size.
- **Compact timer.** The rest and interval timer band can shrink to one slim line, and stays that
  way until you open it up again.
- **Consistency widget** for Home: the grid of climbing days from the You page, with your week
  streak and days in the last 30.
- **Everything in backups.** Session history (every set, and the climbs logged in it), your
  settings and Home layout, and climb videos now go into backups too. Backups are now `.zip` files;
  the `.json` backups you already have still import.
- **Photos and videos switches** for backups, so you can leave them out to keep the file small.
- **Climbs this week page**, from the Home widget: the week's climbs, sends, days and hardest send,
  then every climb by day. The arrows go back through earlier weeks.
- **Days on the wall page**, from the Home widget: this week, month and year, your week streak,
  and a month calendar of your climbing days. Tap a day to see its journal.
- **+1 go** on projects in Home and Progress: one tap logs a fall on the climb today, with Undo.

### Changed

- **Climbs instead of problems.** Every climb you log is a climb you can come back to: logging
  goes on it again adds another log. Climbs are always named, by you or by Crux from the grade
  and wall, need no place, and any climb you've tried and not sent is a project. Saved problems,
  and the climbs you've already logged, become climbs when you update; same-named climbs at a
  place are merged.
- **Log climb, simpler.** Tap **+ Fell** and **+ Sent** for each go, in order; Crux works out
  attempt, flash, onsight (route, no beta) or redpoint. How hard it felt is five words. The day is
  a calendar button beside the place, amber when it isn't today. Name suggestions continue a
  climb; note, photo and video are chips until used.
- **No more wall resets.** Crux never hides or removes a climb on its own: climbs taken down by an
  earlier reset are back in your projects and suggestions.
- **Tapping a climb or project opens Log climb on it**, ready for more goes, with its earlier logs
  listed at the bottom. There's no separate climb page to edit any more.

- **The Bodyweight and Weight trend widgets** open Measurements instead of the You page.
- **Latest best** on Home is your newest hardest send: the latest send harder than all before it,
  in any style, so an easier flash no longer replaces a harder redpoint.
- **A session's logs on the same climb** show as one entry with the goes added up, in the live
  session, the Journal and the session summary.
- **Backups page.** Export, the photo and video switches and import moved from the Settings list to
  their own page, *Settings → Backups*.
- **Import asks about duplicates.** When something in a backup is already in Crux, you see both and
  choose: skip it, replace it with the backup's, or keep both. One answer can cover the rest of a
  section. Replacing a place brings in the walls and climbs it's missing.

### Fixed

- **Large text in Log climb**: with a big font size on your phone, the day button and the "how
  hard it felt" words no longer get cut off.
- **Goes on a climb add up across days**: the go pads count the climb's falls and sends in all, its
  earlier goes show before today's, and a send after earlier goes is a redpoint, whatever their day.
- **The Journal's day tally** counts a session's logs on one climb once, like the session card.
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

[Unreleased]: https://github.com/hardtekpt/crux/compare/v0.4.0...dev
[0.4.0]: https://github.com/hardtekpt/crux/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/hardtekpt/crux/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/hardtekpt/crux/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/hardtekpt/crux/releases/tag/v0.1.0
