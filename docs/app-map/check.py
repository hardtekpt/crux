#!/usr/bin/env python3
"""Fails when the app map is missing a screen, sheet or dialog that the app code has.

    python docs/app-map/check.py

Every navigation route (ui/navigation/Routes.kt) and every composable named *Sheet or *Dialog must be
listed under "covers" of a page in manifest.json, or under "ignore" with a reason. Run by CI.
See docs/developer/app-map.md for what to do when it fails.
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.environ.get('CRUX_SRC') or os.path.abspath(os.path.join(HERE, '..', '..', 'app', 'src', 'main', 'java'))


def inline_dialogs(text):
    """Dialogs and sheets opened from inside a screen: named "Screen/dialog: first title text" (or a number)."""
    found = set()
    lines = text.splitlines()
    func = None
    count = {}
    for i, line in enumerate(lines):
        m = re.match(r'\s*(?:(?:private|internal|public)\s+)?fun\s+(?:<[^>]+>\s*)?(\w+)\s*\(', line)
        if m:
            func = m.group(1)
        if func and re.search(r'(?<![\w.])(?:AlertDialog|ModalBottomSheet|DatePickerDialog|Dialog)\(', line) and not re.match(r'\s*(?:private |internal )?fun ', line):
            own = 'ModalBottomSheet(' in line if func.endswith('Sheet') else func.endswith('Dialog')
            if own:
                continue  # the sheet or dialog is counted by its own name
            title = None
            for nxt in lines[i + 1:i + 30]:
                t = re.search(r'Text\(\s*"([^"$]{3,40})"', nxt)
                if t:
                    title = t.group(1)
                    break
            count[func] = count.get(func, 0) + 1
            found.add('%s/dialog:%s' % (func, title or count[func]))
    return found


def main():
    manifest = json.load(open(os.path.join(HERE, 'manifest.json'), encoding='utf-8'))
    covered = {c for p in manifest['pages'] for c in p.get('covers', [])}
    ignored = set(manifest.get('ignore', {}))
    routes, widgets = set(), set()
    for dirpath, _, files in os.walk(SRC):
        for f in files:
            if not f.endswith('.kt'):
                continue
            text = open(os.path.join(dirpath, f), encoding='utf-8').read()
            if f == 'Routes.kt':
                routes |= {m for m in re.findall(r'data (?:class|object) (\w+Route)\b', text)}
            for m in re.finditer(r'@Composable\s+(?:(?:private|internal|public)\s+)?fun\s+(?:<[^>]+>\s*)?(\w*(?:Sheet|Dialog))\s*\(', text):
                widgets.add(m.group(1))
            widgets |= inline_dialogs(text)
    known = covered | ignored
    missing = sorted((routes | widgets) - known)
    stale = sorted(c for c in covered if c not in routes | widgets)
    stale_ignore = sorted(i for i in ignored if i not in routes | widgets)
    ok = True
    if missing:
        ok = False
        print('These screens, sheets and dialogs are in the app but not on the app map:')
        for m in missing:
            print('  ' + m)
        print('Add a page for each to docs/app-map/manifest.json ("covers": [...]), capture it, and rebuild.')
        print('Or, for a pure helper that is not a screen of its own, list it under "ignore" with a reason.')
    if stale or stale_ignore:
        ok = False
        print('These names are in manifest.json but no longer in the app code (renamed or removed):')
        for m in stale + stale_ignore:
            print('  ' + m)
    if ok:
        print('app map covers %d routes and %d sheets and dialogs' % (len(routes), len(widgets)))
    sys.exit(0 if ok else 1)


if __name__ == '__main__':
    main()
