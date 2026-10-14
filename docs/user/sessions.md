# Sessions

A session is a workout you log as you go: either one of your plans, set by set, or a climbing day
without a plan, where you log climbs.

<img src="../../fastlane/metadata/android/en-US/images/phoneScreenshots/6_session.png" width="300" alt="A live session showing the current exercise, its sets and a Done button">

## Starting a session

- From the **+** Log menu, choose **Start a session**, then pick a plan or *no plan* for a
  climbing day.
- From a plan's page, or the **Today's plan** widget on Home, tap **Start session**.

Crux starts the session at the last place you climbed, and you can change that inside the
session. Only one session runs at a time; starting another ends the first.

## While it runs

- The top bar shows the session's name, a quiet clock and **Finish**. Below it, a progress strip
  has one segment per exercise; tap a segment to jump to that exercise.
- **One exercise at a time**: its place in the plan, its goal as a sentence (for example
  "6 hangs of 10 s with +17.5 kg, 3 min rest"), and a row per set.
- **Logging a set**: the next set starts at the target, or at what your last set was. Change what
  went differently, then tap **Done**. *Skip this set* marks it skipped. Tap a logged set to change
  it or take it out.
- **Rest timer**: after a set, a band opens with the rest counting down, with **+30 s** and
  **Skip**. It beeps for the last three seconds and buzzes when the rest is over. After the last
  set, Crux moves on to the next exercise.
- **Interval timer**: interval exercises run a timer with preparation, work and rest phases, the
  repeats and cycles left, and pause, play and stop. Each finished cycle is logged as a set. You
  can also log cycles by hand.
- **Extras**: **+ Exercise** adds an exercise from your library, and **Timer** runs an interval
  timer of its own. Presets include classic Tabata (8 × 20 s on, 10 s off) and hangboard repeaters
  (6 × 7 s on, 3 s off, three cycles, 3 min between). Outside a session, *You → Interval timer* is
  a full-screen timer of its own: see [You](you.md#interval-timer).
- **Climbs**: **Log climb** inside a session adds the climb to the session and to your journal. The
  session shows your climbs, sends and hardest send so far. Several logs on the same climb show
  as one entry with the goes added up (a redpoint once one of them is a send), here, in the
  Journal and in the session's summary. Tap a climb in the session to log more goes on it. If
  **Rest after a climb** is set in Settings, logging a
  climb starts that rest in the band, so you know when to pull on again.

The arrows at the end of the band make it **compact**: one slim line with the number, what's left
and the controls, so more of the exercise shows. Tap them again for the large band. Crux remembers
which you chose.

The screen stays on while something counts down. Timers are worked out from the clock, so they stay
right if the screen turns off or you switch apps.

**Leaving** with the back arrow keeps the session running. A pill above the bottom bar shows its
name and clock; tap it to go back.

## Live notification

While a session runs, Crux keeps a live notification, so you can see where you are and act without
opening the app. On Android 16 it is a Live Update: a bar in segments and a chip in the status bar.
On Android 8 to 15 it is a plain notification with a progress bar. Tap the card to go back to the
session or timer; there is no Open button.

- **A session with a plan**: one segment per exercise, green once it's done. The title reads
  "Exercise · set N of M". **Set done** logs the next set as planned and starts its rest.
- **Resting**: the card turns into the countdown, in amber, with **+30 s**, **−30 s** and
  **Skip rest**. The time left leads the title ("1:24 · Rest") and fills the status bar chip. When the rest ends the phone buzzes, if *Timer sounds* is on in Settings.
- **The interval timer**: the time left in the current phase leads the title ("0:14 · Work") and
  fills the status bar chip; below it, the rep and cycle. The bar shows the current cycle rep by
  rep, teal for work and amber for rest. Finished
  cycles are green, and the rests between cycles are violet. **Pause** (then **Resume**) and
  **Stop** are on the card. The *You → Interval timer* page shows the same card.
- **A climbing day with no plan**: the time on the wall, with a mark for each climb (green for a
  send, grey for not yet). The title reads "N climbs · M sent" and **Log climb** is on the card.

On Android 13 and later, Crux asks for permission to show notifications the first time a session
starts. If you say no, you just don't get the notification; everything else works as before.

## Finishing

Tap **Finish**. Add how hard it felt (1–10) and a note, then save. Or throw the session away. Crux
asks first, and climbs you logged in it stay in the journal.

Finished sessions appear in the [Journal](journal.md) with their length. Tap one for its
summary: when, where and for how long, how it felt, what got done against the plan, every set of
every exercise, the climbs and your note. The **pencil** edits it: its day, when it started and
finished (a finish before the start means it ran past midnight), how hard it felt, and the note.

Session history is in backups, with every set, and the climbs logged in a session stay linked to it
when you restore.
