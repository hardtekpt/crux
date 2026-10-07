# Settings and your data

Open Settings from the gear on the **You** tab.

<img src="../../app/src/test/screenshots/settings_dark.png" width="300" alt="Settings: grades, appearance, units and your data">

## Settings

- **Grades**: the scale new climbs are logged in, per discipline. See [Grades](grades.md).
- **Appearance**: Dark (the default), Light, or System.
- **Units**: Metric (kilograms and centimetres) or Imperial (pounds, feet and inches). Everything is
  stored metric, and Imperial only changes what you see, so switching back and forth loses nothing.
- **Your data**: demo mode, and backups.
- **Diagnostics**: crash reports.
- **About**: version, licence, source code, and the open-source libraries Crux uses.

## Demo mode

Demo mode shows a separate set of sample data: places, climbs, plans, weigh-ins, body stats,
records and notes. It's a separate database. Anything you do in demo mode stays there, and your own data is
untouched until you turn demo mode off.

## Backups

*Settings → Backup* saves your data to a file you keep, and brings a backup back in.

- **Export**: choose sections (exercise list, plan list, journal, places, body stats, personal
  records, notes), then pick where to save the `.json` file: your phone, a cloud drive, anywhere
  your file picker offers.
- **Import**: pick a backup file. Crux shows what's in it, and you choose what to bring in.
  **Importing only adds**: things already there are skipped, and nothing is deleted. You can
  import the same file twice safely.

**Photos** (climb photos, wall images) are inside the backup file. **Videos are not**, because
they're too large. Session history isn't in backups yet.

Backups from older versions of Crux keep importing into newer ones. A backup from a *newer*
version than the app is refused, with a message to update the app first.

## Android's own backup

If Android backup is on for your phone, Crux's database and settings are included in it, so a
new phone set up from your Google account backup gets them back. Photos and videos are left out of
the cloud backup (it has a 25 MB limit) and travel only with a direct phone-to-phone transfer.
The demo data is never backed up.

## Your data is never wiped by an update

When an update changes how data is stored, Crux first saves a copy of your database on the phone,
then upgrades it. Updates never delete your data to start over.

## Crash reports

If Crux closes unexpectedly, a report is saved **on your phone only**: the app version, Android
version, phone model and the error. *Settings → Diagnostics → Crash reports* shows how many there
are and lets you **share the latest** (to paste into a bug report) or **clear** them. Nothing is
sent anywhere unless you share it yourself.

## Privacy

No account, no ads, no analytics, no tracking. The network is used only for map tiles and address
search, when you use them. See [PRIVACY.md](../../PRIVACY.md).
