# UI and design system

The whole UI is Jetpack Compose with Material 3, themed by Crux's own tokens. Crux is dark-first:
dark is the default theme, and light and system are options.

## Theme

[`ui/theme/`](../../app/src/main/java/com/hardtekpt/crux/ui/theme):

| Token set | Where | What |
| --- | --- | --- |
| Colours | `Color.kt` | Material colour schemes for dark and light, plus `CruxColors`: success (sent), route tape, chart series, timer phases, the board accent and the climber card's stripe |
| Typography | `Type.kt` | **Archivo** (variable, 400–800) for text, **JetBrains Mono** for numbers, labels and "code" style (`CruxTheme.type.code`) |
| Spacing | `Dimens.kt` → `CruxSpace` | `s0`…`s16`: 0, 4, 8, 12, 16, 20, 24, 32, 40, 48, 64 dp |
| Sizes | `CruxSize` | `touchTarget` (48 dp), border widths, and so on |
| Shapes | `CruxShape`, `CruxShapes` | Rounded corners for cards, rows and buttons |

**Accents.** The climber picks an accent (`Accent` in `data/prefs`: Teal, Blue, Violet, Magenta, Pink, Slate, Chalk), and
`CruxTheme(darkTheme, accent)` builds the scheme with `cruxColorScheme`. An accent sets the
primary roles and tints the surfaces and `outlineVariant` slightly towards itself; everything else
(text, outline, secondary amber, success, danger, tape, charts, timer phases) is the same in every
accent. Teal is the brand palette. The others are teal's tones turned to their hue in OKLCH, at
the same lightness and the same share of the chroma that hue allows (Slate a third of it); Chalk
(white on dark, charcoal on light) is picked by hand. Keep neighbouring accents far enough apart to
tell at a glance in both modes: an Indigo was too close to Blue in dark, and a Sky to Teal in light. `AccentContrastTest` checks
every accent in both modes. Don't add an amber, green or red accent: those colours already mean a
go, a send and danger. `CruxTheme.isDark` says which mode is in use.

Use them through `CruxTheme.space`, `CruxTheme.size`, `CruxTheme.type`, `CruxTheme.colors` and
`MaterialTheme`. Don't hard-code colours or spacing in screens.

Text is in `sp`, so it follows the phone's font size. Keep it from clipping when the font is large:
let containers that hold text grow (`heightIn(min = …)`, `widthIn(min = …)` rather than a fixed
size), give single-line labels `maxLines` with an ellipsis, or let a short word shrink to fit
(`autoSize`). The `*_text_largest` screenshots show the main screens at 1.69× text.

**Text size.** The climber's text size (`TextSize` in `data/prefs`) multiplies the phone's font
scale. `MainActivity.attachBaseContext` applies it as a configuration override, and the Activity
recreates itself when the setting changes. It can't be a `LocalDensity` override in `CruxTheme`:
dialogs, bottom sheets and menus get windows of their own, which read the configuration and not
the composition's density.

## Components

[`ui/components/`](../../app/src/main/java/com/hardtekpt/crux/ui/components):

- **Structure**: `CruxTopAppBar`, `CruxCard` (bordered surface), `CruxListRow` (leading, two
  lines, trailing), `Eyebrow` (small mono section label), `EmptyState`, `GradeBadge`.
- **Actions**: `CruxButton` (filled, outlined, text; with an icon), `CruxFab`,
  `CruxSegmentedButtons`, `CruxFilterChip`.
- **Inputs**: `CruxTextField`, `CruxStepper`, `CruxValueStepper`.

### Sheets, figures and undo

- **`CruxSheet`** (`components/Sheets.kt`) is every bottom sheet: handle, colour, corners, insets and a
  title row. Its one rule for actions: a sheet that commits something passes `confirm` (a full-width
  filled button pinned at the bottom); a sheet that only picks passes nothing and closes on a tap;
  a step inside a flow (Where) passes `action`, a text button at the top right. Never a filled button
  in the content as well. **`ActionSheet`** is the menu form: the thing's name as a mono eyebrow,
  then `CruxListRow`s. Don't call `ModalBottomSheet` directly.
- **`StatStrip`** (`components/Stats.kt`) is figures inside a card or sheet: no border or fill of
  their own, so there are no cards inside cards. A `notable` figure turns amber. Standalone figures
  on the screen's ground are `StatTile`s.
- **`ExercisePickerSheet`** is the one exercise picker, for plans and sessions.
- **`ClimbGrade`** leads every list of climbs (datasets, catalogues, the Climb step); a climb you
  logged uses `GradeBadge`. **`SettingRow`** is a setting with its value (label left, value in the
  accent with ▾ right) in any form or sheet.
- **Values are set in sheets**, not dialogs: a ruler, chips or a field go in a `CruxSheet` with its
  confirm at the bottom. Dialogs are for confirmations and errors (and a step stacked on a sheet).
- **Add** is a tonal `CruxButton` with **+** beside the eyebrow or title of the list it adds to;
  filled is for the one thing a screen exists to finish.
- **Haptics**: `rememberCruxHaptics()` (`input/Feedback.kt`) has `confirm` (set done, sent, save),
  `tick` (a fall, a step) and `reject` (a refused save); they follow the system setting.
- **Deletes**: something simple (a log, a note, a result) is deleted at once and offered to
  `UndoCenter` (`ui/UndoCenter.kt`) with a restore and an optional commit; the app shell shows the
  Undo snackbar above the nav bar. Repositories expose `delete…Undoable`, which returns the restore.
  Deletes that cascade (places, plans, datasets) keep a confirm dialog that says what goes with them.
- **Selection**: long-press a row to pick it; `SelectionBar` replaces the top bar with "N selected",
  back clears, and the batch action (Delete, with one Undo) sits on the right. Datasets, Notes and the
  Journal use it.
- **Text styles**: screens use `MaterialTheme.typography` or `CruxTheme.type` (also as top-level
  `CruxMicro`, `CruxMicroSmall`, `CruxAxis`, `CruxDayNumber`, `CruxSectionTitle` and the dock's
  `CruxDockNumber`, `CruxTimerHuge`, `CruxPhaseLabel`), not their own `TextStyle(...)`. The rest left
  in screens are sized to fit at run time (hold diagrams, the big timer, wheels and rulers).
- **Amber** (`secondary`) is for bests, streak highlights and cautions only. Rests are `timerRest`.

### The touch input kit (`components/input/`)

Built for logging with one thumb, often with chalky fingers. Everything can also be typed: tap the
value.

| Control | For |
| --- | --- |
| `NumberWheel`, `DurationWheel`, `LoadWheel` | Sets, reps, seconds, added load (hold the steppers to repeat) |
| Ruler | Body stats (height, wingspan, circumferences); settles on a tick |
| `GradeStrip` | Picking a grade; scrolls the scale |
| `DayStrip` (+ calendar) | Picking the day of a climb; today at the right |
| `EffortScale` | How hard it felt, 1–10 |

Wheels expose semantics actions, so TalkBack can set them directly.

## Navigation and motion

- A floating, icon-only bar with five tabs and the **+** Log button built in
  (`navigation/FloatingNavBar.kt`). Screens pad their content with `LocalNavBarClearance`.
- Page transitions are quiet fades, including predictive back.
- Pages with their own tabs (Train: Week / Plans / Exercises, the Journal's types) swipe sideways
  between them (`TabSwipe.kt`); the page follows the finger a little once the swipe is claimed.
  The main tabs don't swipe.
- **Tabs or segments**: up to three views of one page use `CruxSegmentedButtons` (Train, Progress,
  Datasets); four or more, or views that show counts, use underline tabs (the Journal).
- **Motion** lives in `components/Motion.kt`: `CruxMotion` durations (120 / 160 / 200 ms,
  decelerating, no overshoot (the nav bar's sliding highlight is the one spring, by choice), plus the page `enter()` / `exit()` curves that `Transitions.kt` uses), `revealWhen(ready)` to fade a list in once its data is loaded
  instead of showing empty values, `pressScale` for big tap targets and `AnimatedCount` for
  numbers that roll. **Reduce motion** (Settings, or Android's Remove animations) sets
  `CruxMotion.reduced` and `LocalReduceMotion`: page changes only fade, and the nav highlight
  glides without its overshoot. Lazy lists use `Modifier.animateItem()`; colours that change with state use
  `animateColorAsState`. Keep new motion within these: short, quiet, never in the way of a tap.
- **Performance**: view models build their state with `flowOn(Dispatchers.Default)` before
  `stateIn`, so only the result reaches the main thread. In lazy lists, give items keys and a
  `contentType`, and draw decorations like the Journal's rail (`drawBehind`) rather than
  measuring rows with `IntrinsicSize`. Stored photos go through `rememberLocalImage`, which
  caches decoded bitmaps. Home's dashboard is a `LazyColumn` of widget rows (`toRows()`) except while
  editing, where the drag-to-arrange grid lays out every widget. The Journal builds its timeline
  only when data changes, and waits 150 ms after the last keystroke before searching.

## Conventions

- **Copy**: plain, friendly British English ("colour", "centimetres"). Say "climber", not "user".
- **Units**: read `LocalUnits`; format with the helpers in `Units.kt`. Never store imperial.
- **Dates**: read "today" from `LocalClock`, never `LocalDate.now()` in a composable.
- **Test tags**: interactive elements and screens carry `testTag`s (`screen_Home`, `log_fab`,
  `save_climb`…), which the instrumented tests use.
- **Accessibility**: give icons that carry meaning a `contentDescription` (decorative ones `null`),
  and keep touch targets at least 48 dp (`CruxTheme.size.touchTarget`, or
  `minimumInteractiveComponentSize()` around a small chip). An on/off setting is a
  `CruxSwitchRow`, so the row is the control. A picture (chart, grid, diagram) gets a
  `contentDescription` that reads its values, not only its title; `TimeSeriesChart` and `BarChart`
  add theirs to the title you pass. A timer speaks through `Announce` (`session/SessionDock.kt`)
  when its phase changes, never every second. Nothing relies on colour alone. Read
  `rememberReduceMotion()` before adding motion that slides or bounces; page transitions check
  `CruxMotion.reduced`. The screenshot tests fail on a tappable with no label.
