# FAQ and troubleshooting

**Do I need an account?**
No. Crux has no account, server or sign-in. Everything stays on your phone.

**Does Crux work offline?**
Yes, everything except loading map tiles and searching addresses.

**Why didn't my old climbs change when I switched from Font to V?**
Grades are never converted. A climb keeps the scale it was logged in. See [Grades](grades.md).

**I switched on demo mode and my climbs are gone!**
They're safe. Demo mode shows a separate set of sample data. Turn it off in *Settings → Your data*
and your own data is back, untouched.

**Why isn't a climb showing in the name suggestions?**
Its wall was probably reset, which takes its climbs down. Climbs taken down are hidden from
suggestions and projects but stay in your history.

**How do I mark something as a project?**
You don't: a climb you've tried and not sent is a project automatically. See
[Places → Projects](places.md#projects).

**Why don't my session sets show up as personal records?**
Records come from results you log under *You → Personal records*. Sessions don't feed records yet.

**Are my videos backed up?**
Yes, with your photos, unless you switch them off under *Settings → Backups → Photos and videos*.
They can make the backup file large. See [Settings and your data](settings-and-data.md#backups).

**Can I import the same backup twice?**
Yes. Crux asks about everything that's already there: skip it, replace it, or keep both. Skip all
of it and nothing changes.

**An update says something about a database copy. What is that?**
Before an update changes how data is stored, Crux copies your database on the phone first, so
nothing can be lost to a failed upgrade. It's automatic.

**The app crashed.**
Sorry! Go to *Settings → Diagnostics → Crash reports*, tap *Share latest*, and paste it into a
[bug report](https://github.com/hardtekpt/crux/issues/new/choose). Nothing is sent unless you share
it.

**Android says the update "conflicts with an existing package".**
The APK you're installing wasn't signed with Crux's release key: it may be a build from somewhere
else. Only install releases from the
[releases page](https://github.com/hardtekpt/crux/releases), and check the signature (see
[Getting started](getting-started.md#verifying-a-download)).

**How do I report a bug or ask for a feature?**
Open an [issue](https://github.com/hardtekpt/crux/issues/new/choose). For security problems, see
[SECURITY.md](../../SECURITY.md).
