# Getting started

Climb Logger is a training planner and climbing journal for Android 9 and newer. Everything you enter stays
on your phone. There's no account and no sign-in.

## Install

1. On your phone, open the [latest release](https://github.com/hardtekpt/crux/releases/latest)
   and download `crux-vX.Y.Z.apk`.
2. Open the file. If Android asks, allow installing apps from your browser or file manager.
3. Open **Climb Logger**.

**Updating:** download the newer APK and install it over the old one. Your data stays. Every
release is signed with the same key, and Android refuses an update signed with a different one.
To check a download yourself, see [Verifying a download](#verifying-a-download).

An F-Droid listing is planned. Once it's there, F-Droid will handle updates for you.

## First-run setup

The first time Climb Logger opens on a phone, it offers a short setup. Every step can be skipped and
keeps the defaults, and none of it asks for a permission.

- **Welcome**: **Set up** walks through the four steps below. **Restore a backup** opens
  *Backups* to import one; once something is restored, the app opens and setup is done.
  **Skip setup** keeps every default and opens Home.
- **1. Units and grades**: Metric or Imperial, and a scale for boulders (Font or V) and routes
  (French or YDS), each with a sample. These are the same settings as *You › Settings*.
- **2. Where do you climb?** (optional): the place you go most and what's there: Gym, Crag or
  Board. For a board, pick Kilter Original, MoonBoard or another board. A MoonBoard starts as the
  2016 set and a Kilter has no size yet; change either in *Edit place*. Log climb starts at this
  place.
- **3. Weight and height** (optional): set them on the rulers, in the units you chose. They become
  your first weigh-in and your height. Arm span and the rest are under *You › Measurements*.
- **4. How do you want to start?**: your own log, or demo data to look around (Demo mode, which you
  turn off in Settings). A summary shows what you chose. Nothing is saved until you tap **Start**
  (or **Open demo**).

Setup shows once. A phone that already has your data, or one in demo mode, never sees it.

## Your first five minutes

1. **Try it with sample data.** Go to *You* (the person icon) → *Settings* (the gear) → *Demo mode*.
   Climb Logger switches to a separate set of sample climbs, places, plans and weigh-ins. Your own data is
   kept apart and comes back when you turn demo mode off.
2. **Pick your grades.** In *Settings → Grades*, choose a scale for bouldering (Font or V) and one
   for routes (French or YDS). New climbs are logged in these. Grades you've already logged are
   never converted. See [Grades](grades.md).
3. **Pick your units.** In *Settings → Units*, choose Metric or Imperial. Only the display changes;
   switching back and forth loses nothing.
4. **Log something.** Tap the round **+** button at the bottom right. That's the Log menu, and it's
   on every tab.

## Finding your way around

The floating bar at the bottom has five tabs and the **+** Log button:

| Tab | What's there | Guide |
| --- | --- | --- |
| **Home** | A dashboard you arrange: today's plan, this week, latest best, bodyweight, projects, recent climbs, charts | [Home](home.md) |
| **Train** | Your exercise library and session plans | [Training](training.md) |
| **Journal** | One timeline of climbs, sessions, training results and notes | [Journal](journal.md) |
| **Progress** | Hardest sends per grade scale, grade pyramids, charts, open projects | [Progress](progress.md) |
| **You** | Climber card, consistency, personal records, weight, measurements, notes, places, grade converter, interval timer, settings | [You](you.md) |

The **+** Log menu has four entries:

- **Log climb**: a send or an attempt, with its grade and style. See [Logging a climb](journal.md#logging-a-climb).
- **Log weight**: today's bodyweight.
- **Start a session**: run one of your plans, or a climbing day without one. See [Sessions](sessions.md).
- **Add a note**: a thought, a niggle, or beta to remember.

Inside pages that have their own tabs (*Train*: Plans / Exercises), swipe sideways to switch.
Swipe back, or use the back arrow, to leave a page.

## Shortcuts

Long-press Climb Logger's icon on your home screen for **Log climb**, **Session** (back into the running
session, or the choice of what to start), **Log weight** and **Timer**. You can drag one onto your
home screen as its own icon.

## Where your data lives

Everything is stored on your phone, in Climb Logger's private storage. Make a backup now and then from
*Settings → Backups*; the file is yours to keep wherever you like. See
[Settings and your data](settings-and-data.md).

## Verifying a download

Each release lists the APK's SHA-256 next to it (`crux-vX.Y.Z.apk.sha256`). The signing
certificate's SHA-256 is always:

```
a2:8e:87:81:7c:00:0b:9f:3c:8d:d2:42:98:03:60:5e:2d:78:f7:6e:af:9c:7c:63:56:67:6b:fe:2b:3c:cc:36
```

To check it, run `apksigner verify --print-certs crux-vX.Y.Z.apk` (from the Android SDK build-tools)
and compare the "certificate SHA-256 digest" with the value above.
