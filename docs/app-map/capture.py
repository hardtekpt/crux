#!/usr/bin/env python3
"""Drives the Crux debug app on an emulator and saves one screenshot per page of the app map.

    python docs/app-map/capture.py setup --apk app/build/outputs/apk/debug/app-debug.apk
    python docs/app-map/capture.py all            # every page in manifest.json
    python docs/app-map/capture.py page home-top  # one page
    python docs/app-map/capture.py steps "tab:You" "tap:Settings" "shot"   # try steps out

Use a spare emulator (default emulator-5556) so a test run on the main one is not disturbed.
The steps for each page live in manifest.json; see docs/developer/app-map.md for the step syntax.
Needs only Python 3 and adb. Screenshots go to docs/app-map/raw/ (not committed).
"""
import argparse
import json
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
RAW = os.path.join(HERE, 'raw')
PKG = 'com.hardtekpt.crux.debug'
SDK = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or os.path.expanduser('~/AppData/Local/Android/Sdk')
ADB = os.path.join(SDK, 'platform-tools', 'adb.exe' if os.name == 'nt' else 'adb')
SER = os.environ.get('CRUX_MAP_SERIAL', 'emulator-5556')
NAV_Y = 2050  # the bottom bar is below this, on a 2400 px tall screen


def adb(*args, binary=False, timeout=60):
    r = subprocess.run([ADB, '-s', SER, *args], capture_output=True, timeout=timeout)
    return r.stdout if binary else r.stdout.decode('utf-8', 'replace')


def size():
    m = re.search(r'(\d+)x(\d+)', adb('shell', 'wm', 'size'))
    return int(m.group(1)), int(m.group(2))


W, H = 1080, 2400


def dump(retries=4):
    """The visible nodes as dicts. uiautomator refuses while something animates, so it is retried."""
    for _ in range(retries):
        adb('shell', 'rm', '-f', '/sdcard/u.xml')
        adb('shell', 'uiautomator', 'dump', '/sdcard/u.xml')
        xml = adb('shell', 'cat', '/sdcard/u.xml')
        if '<?xml' in xml:
            try:
                root = ET.fromstring(xml[xml.index('<?xml'):])
                break
            except ET.ParseError:
                pass  # read while the file was still being written
        time.sleep(0.6)
    else:
        return []
    nodes = []
    for n in root.iter('node'):
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
        nodes.append({'text': n.get('text') or '', 'desc': n.get('content-desc') or '', 'cls': n.get('class') or '',
                      'box': (x1, y1, x2, y2), 'cx': (x1 + x2) // 2, 'cy': (y1 + y2) // 2})
    return nodes


def labels(nodes=None):
    return [n['text'] or n['desc'] for n in (nodes or dump()) if n['text'] or n['desc']]


def find(label, nodes=None, contains=False, index=0, bottom=None):
    low = label.lower()
    hits = []
    for n in nodes or dump():
        for t in (n['text'], n['desc']):
            t = t.lower()
            if t and ((low in t) if contains else (t == low)):
                if bottom is None or (n['cy'] > NAV_Y) == bottom:
                    hits.append(n)
                    break
    return hits[index] if -len(hits) <= index < len(hits) else None


def tap_xy(x, y, wait=1.0):
    adb('shell', 'input', 'tap', str(int(x)), str(int(y)))
    time.sleep(wait)


def swipe(x1, y1, x2, y2, ms=350, wait=0.9):
    adb('shell', 'input', 'swipe', str(x1), str(y1), str(x2), str(y2), str(ms))
    time.sleep(wait)


def launch():
    adb('shell', 'am', 'force-stop', PKG)
    for pkg in ('com.android.documentsui', 'com.google.android.documentsui'):
        adb('shell', 'am', 'force-stop', pkg)  # a file picker left open by the last page
    adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')  # closes the launcher's shortcut popup
    adb('shell', 'input', 'keyevent', 'KEYCODE_HOME')
    adb('shell', 'monkey', '-p', PKG, '-c', 'android.intent.category.LAUNCHER', '1')
    time.sleep(2.0)
    for _ in range(30):  # until the first screen is drawn
        if find('Home', bottom=True):
            break
        time.sleep(1.0)
    time.sleep(2.0)


def shot(path, crop=None):
    data = adb('exec-out', 'screencap', '-p', binary=True)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if crop:
        from PIL import Image
        import io
        Image.open(io.BytesIO(data)).crop(crop).save(path)
    else:
        open(path, 'wb').write(data)
    print('  saved', os.path.relpath(path, HERE), len(data) // 1024, 'KB')


class Fail(Exception):
    pass


def run_steps(steps, page_id=None):
    """Runs a list of step strings. Returns once they are done; takes the page's screenshot at "shot" or at the end."""
    crop = None
    shot_taken = False
    import datetime
    for raw in steps:
        raw = raw.replace('{today}', datetime.date.today().isoformat())
        step, _, arg = raw.partition(':')
        count = 1
        m = re.match(r'^(.*)\*(\d+)$', arg)
        if m and step in ('scroll', 'back', 'wait'):
            arg, count = m.group(1), int(m.group(2))
        if step == 'tab':
            n = find(arg, bottom=True)
            if not n:
                raise Fail('tab not found: ' + arg)
            tap_xy(n['cx'], n['cy'], 1.4)
        elif step in ('tap', 'tapc'):
            idx = 0
            if '#' in arg:
                arg, _, i = arg.rpartition('#')
                idx = int(i)
            n = find(arg, contains=(step == 'tapc'), index=idx, bottom=False) or find(arg, contains=(step == 'tapc'), index=idx)
            if not n:
                raise Fail('not found: ' + arg + '  (visible: ' + ' | '.join(labels())[:600] + ')')
            tap_xy(n['cx'], n['cy'], 1.3)
        elif step == 'xy':
            x, y = arg.split(',')
            tap_xy(int(x), int(y), 1.2)
        elif step == 'longtap':
            n = find(arg, bottom=False) or find(arg)
            if not n:
                raise Fail('not found: ' + arg)
            adb('shell', 'input', 'swipe', str(n['cx']), str(n['cy']), str(n['cx']), str(n['cy']), '1000')
            time.sleep(1.2)
        elif step == 'home':
            adb('shell', 'input', 'keyevent', 'KEYCODE_HOME')
            time.sleep(1.2)
        elif step == 'switch':
            n = find(arg, bottom=False) or find(arg)
            if not n:
                raise Fail('switch not found: ' + arg)
            tap_xy(W - 130, n['cy'], 1.5)
        elif step == 'long':
            x, y = arg.split(',')
            adb('shell', 'input', 'swipe', x, y, x, y, '900')
            time.sleep(1.0)
        elif step == 'scroll':
            for _ in range(count):
                swipe(540, 1800, 540, 700) if arg == 'down' else swipe(540, 700, 540, 1800)
        elif step == 'swipe':
            x1, y1, x2, y2 = (int(v) for v in arg.split(','))
            swipe(x1, y1, x2, y2)
        elif step == 'back':
            for _ in range(count):
                adb('shell', 'input', 'keyevent', '4')
                time.sleep(1.0)
        elif step == 'key':
            adb('shell', 'input', 'keyevent', arg)
            time.sleep(0.6)
        elif step == 'type':
            adb('shell', 'input', 'text', arg.replace(' ', '%s'))
            time.sleep(0.6)
        elif step == 'wait':
            time.sleep(float(arg) * count)
        elif step == 'expect':
            if not find(arg, contains=True):
                raise Fail('expected on screen: ' + arg + '  (visible: ' + ' | '.join(labels())[:600] + ')')
        elif step == 'scrollto':
            for _ in range(8):
                if find(arg, contains=True, bottom=False):
                    break
                swipe(540, 1800, 540, 800)
            else:
                raise Fail('could not scroll to: ' + arg)
        elif step == 'crop':
            crop = tuple(int(v) for v in arg.split(','))
        elif step == 'shade':
            adb('shell', 'cmd', 'statusbar', 'expand-notifications')
            time.sleep(1.5)
        elif step == 'unshade':
            adb('shell', 'cmd', 'statusbar', 'collapse')
            time.sleep(0.8)
        elif step == 'rotate':
            adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0')
            adb('shell', 'settings', 'put', 'system', 'user_rotation', arg)
            time.sleep(1.5)
        elif step == 'shell':
            adb('shell', *arg.split(' '))
            time.sleep(0.5)
        elif step == 'launch':
            launch()
        elif step == 'discard':
            # Restart the app, open the running session from its pill and throw it away, so it does not end up in the demo data.
            launch()
            pill = find(arg, contains=True)
            if pill:
                tap_xy(pill['cx'], pill['cy'], 1.5)
                run_steps(['tap:Finish', 'tap:Discard session', 'tap:Discard'])
        elif step == 'shot':
            shot(os.path.join(RAW, page_id + '.png'), crop)
            shot_taken = True
        else:
            raise Fail('unknown step: ' + raw)
    if page_id and not shot_taken:
        shot(os.path.join(RAW, page_id + '.png'), crop)


def fixtures():
    """A photo and a short video in the emulator's gallery, for the pages that attach media."""
    from PIL import Image, ImageDraw
    env = dict(os.environ, MSYS_NO_PATHCONV='1')
    tmp = os.path.join(RAW, '_fixture.png')
    os.makedirs(RAW, exist_ok=True)
    # A plain picture of a bouldering wall: a panel with a few coloured holds.
    im = Image.new('RGB', (1200, 1600), (58, 64, 66))
    d = ImageDraw.Draw(im)
    for i, (x, y, c) in enumerate([(300, 1300, (240, 190, 70)), (600, 1050, (240, 190, 70)), (820, 820, (240, 190, 70)), (500, 560, (240, 190, 70)),
                                  (250, 900, (90, 160, 240)), (900, 1250, (90, 160, 240)), (700, 300, (230, 90, 90)), (350, 400, (230, 90, 90))]):
        d.ellipse((x - 70, y - 55, x + 70, y + 55), fill=c, outline=(20, 24, 24), width=6)
    im.save(tmp)
    subprocess.run([ADB, '-s', SER, 'push', tmp, '/sdcard/Pictures/wall-photo.png'], capture_output=True, env=env)
    os.remove(tmp)
    # Two files for the dataset import pages: a small list of climbs, and a file that is not one.
    csv = os.path.join(RAW, 'new-climbs.csv')
    rows = ['name,grade,type,setter,note', 'Green jug ladder,5+,Boulder,Rita,warm-up', 'Orange arete,6B,Boulder,,', 'Slab of dreams,6A+,Boulder,Rita,smear the feet']
    open(csv, 'w', encoding='utf-8', newline='').write((chr(13) + chr(10)).join(rows) + chr(13) + chr(10))
    subprocess.run([ADB, '-s', SER, 'push', csv, '/sdcard/Download/new-climbs.csv'], capture_output=True, env=env)
    open(csv, 'wb').write(b'\x00\x01\x02not a climb list')
    subprocess.run([ADB, '-s', SER, 'push', csv, '/sdcard/Download/broken.csv'], capture_output=True, env=env)
    os.remove(csv)
    # A tiny board catalogue in Boardsesh's snapshot shape, for the catalogue import pages.
    import sqlite3
    db_path = os.path.join(RAW, 'kilter-boardsesh.db')
    if os.path.exists(db_path):
        os.remove(db_path)
    con = sqlite3.connect(db_path)
    con.execute("CREATE TABLE board_climbs (uuid TEXT PRIMARY KEY, board_type TEXT, layout_id INTEGER, setter_username TEXT, name TEXT, description TEXT, "
                "angle INTEGER, frames TEXT, is_draft INTEGER, is_listed INTEGER, is_hidden INTEGER, created_at TEXT, compatible_size_ids TEXT) WITHOUT ROWID")
    con.execute("CREATE TABLE board_climb_stats (board_type TEXT NOT NULL, climb_uuid TEXT NOT NULL, angle INTEGER NOT NULL, display_difficulty REAL, "
                "benchmark_difficulty REAL, ascensionist_count INTEGER, difficulty_average REAL, quality_average REAL, PRIMARY KEY (board_type, climb_uuid, angle)) WITHOUT ROWID")
    # Hold placements that exist on a Kilter Original 12x12, so the hold diagram has something to draw.
    import json as _json
    import random
    points = _json.load(open(os.path.join(HERE, '..', '..', 'app', 'src', 'main', 'assets', 'board_holes.json'), encoding='utf-8'))['kilter:1']['points']
    inside = sorted(((int(k), xy) for k, xy in points.items() if 0 <= xy[0] <= 144 and 0 <= xy[1] <= 156), key=lambda t: t[1][1])

    def route(seed):
        rnd = random.Random(seed)
        low, mid, high = inside[:90], inside[90:-90], inside[-90:]
        picks = [(h, 12) for h in rnd.sample(low[40:], 2)] + [(h, 13) for h in rnd.sample(mid, 5)] + [(h, 14) for h in rnd.sample(high, 2)] + [(h, 15) for h in rnd.sample(low, 4)]
        return ''.join('p%dr%d' % (h[0], role) for h, role in picks)

    names = ['Roof to pinch', 'Big crimps', 'Slopey traverse', 'Corner dyno', 'Pocket ladder', 'Moon arete', 'Warm-up jugs', 'Pinch power', 'Heel hook cave', 'Slab dance', 'Compression squeeze', 'Finger roulette']
    for i, name in enumerate(names):
        con.execute("INSERT INTO board_climbs VALUES (?, 'kilter', 1, ?, ?, '', 40, ?, 0, 1, 0, '2025-01-01', '[10,27,28]')", ('U%02d' % i, ['ana', 'rui', 'kim'][i % 3], name, route(i)))
        for angle, delta in ((40, 0.0), (30, -1.5), (45, 1.2)):
            con.execute("INSERT INTO board_climb_stats VALUES ('kilter', ?, ?, ?, ?, ?, ?, ?)", ('U%02d' % i, angle, 14.0 + i * 0.8 + delta, 14.0 + i * 0.8 if i % 4 == 0 and angle == 40 else None, 40 + i * 25, 14.0 + i * 0.8 + delta, 2.4 + (i % 5) * 0.2))
    con.commit()
    con.close()
    subprocess.run([ADB, '-s', SER, 'push', db_path, '/sdcard/Download/kilter-boardsesh.db'], capture_output=True, env=env)
    os.remove(db_path)
    launch()  # a few seconds of the app itself for the video
    subprocess.run([ADB, '-s', SER, 'shell', 'screenrecord --time-limit 3 /sdcard/Movies/climb-video.mp4'], capture_output=True, env=env)
    for f in ('Pictures/wall-photo.png', 'Movies/climb-video.mp4'):
        adb('shell', 'am', 'broadcast', '-a', 'android.intent.action.MEDIA_SCANNER_SCAN_FILE', '-d', 'file:///sdcard/' + f)


def setup(apk, commit=None):
    global W, H
    import datetime
    os.makedirs(RAW, exist_ok=True)
    json.dump({'commit': commit or subprocess.run(['git', 'rev-parse', '--short', 'HEAD'], cwd=HERE, capture_output=True, text=True).stdout.strip(),
               'captured': datetime.date.today().strftime('%d %b %Y')}, open(os.path.join(RAW, 'meta.json'), 'w'))
    if apk:
        print(adb('install', '-r', apk, timeout=300).strip())
    for k in ('window_animation_scale', 'transition_animation_scale', 'animator_duration_scale'):
        adb('shell', 'settings', 'put', 'global', k, '0')
    adb('shell', 'cmd', 'uimode', 'night', 'yes')
    adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0')
    adb('shell', 'settings', 'put', 'system', 'user_rotation', '0')
    adb('shell', 'pm', 'clear', PKG)
    adb('shell', 'pm', 'grant', PKG, 'android.permission.POST_NOTIFICATIONS')
    W, H = size()
    fixtures()
    launch()
    # Demo mode gives every page something to show.
    run_steps(['tab:You', 'tap:Settings', 'scrollto:Demo mode'])
    n = find('Demo mode')
    tap_xy(W - 130, n['cy'], 2.0)
    run_steps(['expect:Showing sample climbs', 'back', 'tab:Home'])
    print('ready; demo mode is on')


def main():
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest='cmd', required=True)
    s = sub.add_parser('setup')
    s.add_argument('--apk')
    s.add_argument('--commit', help='the commit the apk was built from (default: HEAD)')
    sub.add_parser('all')
    p = sub.add_parser('page')
    p.add_argument('ids', nargs='+')
    t = sub.add_parser('steps')
    t.add_argument('steps', nargs='+')
    sub.add_parser('missing')
    a = ap.parse_args()
    if a.cmd == 'setup':
        setup(a.apk, a.commit)
        return
    if a.cmd == 'steps':
        run_steps(a.steps, 'try' if 'shot' in a.steps else None) if False else run_steps([s for s in a.steps])
        print(' | '.join(labels())[:1500])
        return
    manifest = json.load(open(os.path.join(HERE, 'manifest.json'), encoding='utf-8'))
    pages = {p['id']: p for p in manifest['pages']}
    ids = a.ids if a.cmd == 'page' else list(pages)
    if a.cmd == 'missing':
        ids = [i for i in pages if not os.path.exists(os.path.join(RAW, i + '.png'))]
    failed = []
    for i in ids:
        p = pages[i]
        print(i)
        try:
            launch()
            run_steps(p['steps'], i)
            run_steps(p.get('cleanup', []))
        except Fail as e:
            print('  FAILED:', e)
            failed.append(i)
    if failed:
        print('\nfailed:', ' '.join(failed))
        sys.exit(1)


if __name__ == '__main__':
    main()
