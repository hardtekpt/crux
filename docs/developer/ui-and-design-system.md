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
- Pages with their own tabs (Train: Plans / Exercises) swipe sideways between them
  (`TabSwipe.kt`). The main tabs don't swipe.

## Conventions

- **Copy**: plain, friendly British English ("colour", "centimetres"). Say "climber", not "user".
- **Units**: read `LocalUnits`; format with the helpers in `Units.kt`. Never store imperial.
- **Dates**: read "today" from `LocalClock`, never `LocalDate.now()` in a composable.
- **Test tags**: interactive elements and screens carry `testTag`s (`screen_Home`, `log_fab`,
  `save_climb`…), which the instrumented tests use.
- **Accessibility**: give icons that carry meaning a `contentDescription` (decorative ones `null`),
  and keep touch targets at least 48 dp (`CruxTheme.size.touchTarget`).
