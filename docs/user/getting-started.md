# Getting started

Crux is a training planner and climbing journal for Android 9 and newer. Everything you enter stays
on your phone. There's no account and no sign-in.

## Install

1. On your phone, open the [latest release](https://github.com/hardtekpt/crux/releases/latest)
   and download `crux-vX.Y.Z.apk`.
2. Open the file. If Android asks, allow installing apps from your browser or file manager.
3. Open **Crux**.

**Updating:** download the newer APK and install it over the old one. Your data stays. Every
release is signed with the same key, and Android refuses an update signed with a different one.
To check a download yourself, see [Verifying a download](#verifying-a-download).

An F-Droid listing is planned. Once it's there, F-Droid will handle updates for you.

## Your first five minutes

1. **Try it with sample data.** Go to *You* (the person icon) → *Settings* (the gear) → *Demo mode*.
   Crux switches to a separate set of sample climbs, places, plans and weigh-ins. Your own data is
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
| **You** | Climber card, consistency, personal records, weight, measurements, notes, places, grade converter, settings | [You](you.md) |

The **+** Log menu has four entries:

- **Log climb**: a send or an attempt, with its grade and style. See [Logging a climb](journal.md#logging-a-climb).
- **Log weight**: today's bodyweight.
- **Start a session**: run one of your plans, or a climbing day without one. See [Sessions](sessions.md).
- **Add a note**: a thought, a niggle, or beta to remember.

Inside pages that have their own tabs (*Train*: Plans / Exercises), swipe sideways to switch.
Swipe back, or use the back arrow, to leave a page.

## Where your data lives

Everything is stored on your phone, in Crux's private storage. Make a backup now and then from
*Settings → Backup*; the file is yours to keep wherever you like. See
[Settings and your data](settings-and-data.md).

## Verifying a download

Each release lists the APK's SHA-256 next to it (`crux-vX.Y.Z.apk.sha256`). The signing
certificate's SHA-256 is always:

```
a2:8e:87:81:7c:00:0b:9f:3c:8d:d2:42:98:03:60:5e:2d:78:f7:6e:af:9c:7c:63:56:67:6b:fe:2b:3c:cc:36
```

To check it, run `apksigner verify --print-certs crux-vX.Y.Z.apk` (from the Android SDK build-tools)
and compare the "certificate SHA-256 digest" with the value above.
