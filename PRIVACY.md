# Privacy

Crux has no account, no ads, no analytics and no tracking. The developer receives nothing from the
app.

## What stays on your phone

Everything you enter stays in the app's private storage on your phone: climbs, places, plans,
records, measurements, notes, photos, videos and settings. Nothing is uploaded.

## When Crux uses the network

Only for these, and only when you use them:

- **Map tiles.** Showing a place on the map loads tiles from OpenStreetMap's tile servers
  (`tile.openstreetmap.org`). Those servers see your IP address and which map area you looked at.
  See the [OpenStreetMap Foundation's privacy policy](https://osmfoundation.org/wiki/Privacy_Policy).
- **Address search.** Searching for an address, or showing the address of a pin, uses your phone's
  built-in geocoder. On most Android phones that is Google's service, which receives the text or
  coordinates you looked up.
- **Open in Maps** hands the place's coordinates to your maps app. What happens next is up to that app.

## Location

Crux asks for your **approximate** location only when you tap *My location* on the map, to centre
the map on you. It never asks for precise location. It isn't stored unless you save that point as
a place's location, and it's never sent anywhere by Crux.

## Backups

- **Crux's own backups** (*Settings → Backups*) are files saved wherever you choose, by hand or
  automatically (daily or weekly, into one folder you pick; Crux can reach only that folder). They
  contain your data, including photos and videos, so keep them somewhere you trust. Automatic
  backups hold only your own data, never the demo set.
- **Android's cloud backup** (your Google account) gets nothing from Crux: it's opted out.
- **Phone-to-phone transfer** (setting up a new phone from the old one, which stays between your
  devices) brings Crux's database, settings, photos and videos, on Android 12 and later.

## Crash reports

If Crux crashes, a report (app version, Android version, device model and the error) is saved on
your phone. It's sent only if you share it yourself from *Settings → Diagnostics*.

## Notifications

While a session or the interval timer runs, Crux shows a live notification: the exercise, set,
rest or timer, and the climbs you've logged. It's shown on the lock screen too, so you can follow
a rest without unlocking. It's built on the phone and nothing is sent. Android 13 and later ask
before Crux may show it.

## Permissions

| Permission | Why |
|---|---|
| Internet | Map tiles and address search, only when you use the map |
| Approximate location | Centring the map on you, only when you tap *My location* |
| Notifications | The live notification of a session or timer |
| Live Updates (promoted notifications) | Showing that notification as a Live Update with a status bar chip (Android 16) |
| Foreground service (special use) | Keeping the session's clock, rests and timer running with the screen off |
| Vibrate | The buzz at the end of a rest or interval |
| Run at start-up, keep awake | Android's job scheduler, so automatic backups keep their schedule after a restart |
| Nearby devices (Bluetooth connect) | Lighting a climb's holds on a Kilter or MoonBoard you paired, only when you tap Light. Android's own pairing picker finds the board, so Crux never scans and never uses location for it; nothing is sent anywhere else |

Crux doesn't ask for precise location, contacts, storage, camera or microphone access. Photos and
videos come through the system pickers and camera apps, which hand over only what you choose.
