# App map

The app map is a reference of every screen, sheet and dialog in Crux, with a screenshot of each and arrows for
what leads where. It is one pannable page, grouped by tab (Home, the Log button, Train, the live session,
Journal, Progress, You), and it is meant to stay in step with the app.

Published page: https://claude.ai/artifact/KGk3CtYtKBdnFbf6dfopov (the same link is updated in place).

It is made from files in [`docs/app-map/`](../app-map/):

| File | What it is |
|---|---|
| `manifest.json` | The source of truth: lanes, pages (title, description, where it sits, the steps that reach it), arrows. |
| `capture.py` | Drives the debug app on an emulator and saves one screenshot per page, following each page's steps. |
| `build.py` | Turns the manifest and screenshots into `dist/index.html` and `dist/shots/*.webp`. |
| `check.py` | Fails when a route, sheet or dialog in the code is not on the map. Run by CI. |
| `template.html` | The page itself (canvas, arrows, details panel). |
| `dist/` | The built page and its screenshots. Committed, so the map can be published from the repo. |
| `raw/` | Full-size screenshots from the last capture. Not committed. |

## When you change the UI

Any change that adds, removes, renames or reshapes a screen, sheet or dialog:

1. Edit `manifest.json`: add or change the page (and its `covers` entry), its arrows, and its description.
2. Re-capture the pages that changed (below).
3. `python docs/app-map/build.py`, then commit `manifest.json` and `dist/`.
4. Publish `docs/app-map/dist/index.html` (with its `shots/`) to the same artifact link.

CI runs `python docs/app-map/check.py`. It looks at the code, not at screenshots: it fails when a navigation route
(`ui/navigation/Routes.kt`), a composable named `*Sheet` or `*Dialog`, or an `AlertDialog` / `ModalBottomSheet`
opened from inside a screen has no page in the manifest, and when the manifest names something that is gone.
A pure helper that is not a screen can go under `"ignore"` with a reason.

## Capturing

Use a spare emulator (default `emulator-5556`, set `CRUX_MAP_SERIAL` to change it) so a test run on the main one is
not disturbed. The map is captured from a debug build with Demo mode on, so every page has something to show.

```bash
python docs/app-map/capture.py setup --apk app/build/outputs/apk/debug/app-debug.apk --commit abc1234   # install, clear data, Demo mode on, add a photo and video to the gallery
python docs/app-map/capture.py all                                                        # every page
python docs/app-map/capture.py page session-live plan-detail                              # only these
python docs/app-map/capture.py steps "tab:You" "tap:Settings"                             # try steps out, prints what is on screen
python docs/app-map/capture.py missing                                                    # pages with no screenshot yet
```

`--commit` is the commit the APK was built from; it is shown in the map's header. Build the APK from a clean clone of `dev` so
work in progress in your checkout does not leak into the screenshots.

Each page starts from a fresh launch of the app on Home, runs its `steps`, then saves a screenshot. Its `cleanup`
steps run afterwards, so a started session does not leak into the next page. Pages that change data (a started
session) must clean up after themselves; pages that only open and close screens need nothing.

| Step | Does |
|---|---|
| `tab:Journal` | Taps a tab in the bottom bar (or the Log button). |
| `tap:Label` | Taps the element whose text or description is exactly `Label`. `tap:Label#1` takes the second match. `tapc:` matches part of the text. |
| `xy:540,1200` | Taps a point. `long:x,y` presses and holds. |
| `scroll:down`, `scroll:up` | Swipes once. `scroll:down*3` swipes three times. `scrollto:Label` scrolls until `Label` is on screen. |
| `back` | The system back button (`back*2` twice). |
| `wait:2` | Waits seconds, for a timer to run. |
| `type:text`, `key:KEYCODE_ENTER` | Types text, presses a key. |
| `expect:Label` | Stops the capture with an error if `Label` is not on screen. |
| `shade`, `unshade` | Opens or closes the notification shade. `crop:x1,y1,x2,y2` crops the next screenshot. |
| `rotate:1` | Rotates the screen (`0` upright, `1` landscape). |
| `launch` | Restarts the app. |
| `shot` | Takes the screenshot now (otherwise it is taken after the last step). |

The screen is 1080 by 2400. Labels are what the Android accessibility tree shows (`adb shell uiautomator dump`
shows them too). `uiautomator` refuses while something animates, so steps are retried; a page that animates all the
time (a running timer) is best reached with `wait:` and fixed taps.

## Building and publishing

```bash
pip install pillow
python docs/app-map/build.py
```

This writes `docs/app-map/dist/index.html` and `dist/shots/`. Publish the page with the Artifact tool, passing the
`dist/shots/*.webp` files as supporting files and the existing link as `url`, so the link does not change.

## Adding a page

In `manifest.json`, under `pages`:

```json
{
  "id": "session-live",
  "title": "Live session",
  "kind": "page",             // page, sheet, mode or notification
  "lane": "session",          // one of the lanes
  "col": 1, "row": 0,         // its place in the lane
  "desc": "What it is, in a sentence or two, for a climber.",
  "covers": ["SessionRoute"], // route and sheet/dialog names from the code, checked by check.py
  "steps": ["tab:Train", "tap:Plans"],
  "cleanup": []
}
```

Arrows go under `edges` as `["from-id", "to-id", "label", "nav"]`, where the last item is `nav` (a tap that goes to
another screen), `sheet` (a sheet or dialog opens over it) or `tab` (a tab or scroll on the same screen).
