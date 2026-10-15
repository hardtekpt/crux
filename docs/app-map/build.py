#!/usr/bin/env python3
"""Builds the app map page from manifest.json and the raw screenshots.

    python docs/app-map/build.py

Reads docs/app-map/raw/<page id>.png (made by capture.py), writes docs/app-map/dist/shots/<id>.webp
and docs/app-map/dist/index.html (template.html with the manifest's data filled in).
Needs Pillow (pip install pillow). See docs/developer/app-map.md.
"""
import datetime
import json
import os
import re
import subprocess
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..'))
RAW = os.path.join(HERE, 'raw')
DIST = os.path.join(HERE, 'dist')
CARD_W = 216
SHOT_W = 540


def main():
    manifest = json.load(open(os.path.join(HERE, 'manifest.json'), encoding='utf-8'))
    pages, lanes = manifest['pages'], manifest['lanes']
    ids = [p['id'] for p in pages]
    problems = []
    if len(set(ids)) != len(ids):
        problems.append('duplicate page ids')
    lane_ids = {l['id'] for l in lanes}
    seen = {}
    for p in pages:
        if p['lane'] not in lane_ids:
            problems.append('%s: unknown lane %s' % (p['id'], p['lane']))
        slot = (p['lane'], p['col'], p['row'])
        if slot in seen:
            problems.append('%s and %s share the slot %s' % (p['id'], seen[slot], slot))
        seen[slot] = p['id']
    for e in manifest['edges']:
        for end in e[:2]:
            if end not in ids:
                problems.append('edge %s -> %s: unknown page %s' % (e[0], e[1], end))
    os.makedirs(os.path.join(DIST, 'shots'), exist_ok=True)
    out_pages = []
    for p in pages:
        src = os.path.join(RAW, p['id'] + '.png')
        dst = os.path.join(DIST, 'shots', p['id'] + '.webp')
        if os.path.exists(src):
            im = Image.open(src).convert('RGB')
            im = im.resize((SHOT_W, round(SHOT_W * im.height / im.width)), Image.LANCZOS)
            im.save(dst, 'WEBP', quality=80, method=6)
        elif not os.path.exists(dst):
            problems.append('%s: no screenshot (run capture.py page %s)' % (p['id'], p['id']))
            continue
        h = Image.open(dst).height
        out_pages.append({'id': p['id'], 'title': p['title'], 'kind': p['kind'], 'lane': p['lane'], 'col': p['col'], 'row': p['row'],
                          'desc': p['desc'], 'ih': round(CARD_W * h / SHOT_W)})
    if problems:
        print('\n'.join('  ' + x for x in problems))
        sys.exit(1)
    # shots of pages that were removed from the manifest go too
    for f in os.listdir(os.path.join(DIST, 'shots')):
        if f[:-5] not in ids:
            os.remove(os.path.join(DIST, 'shots', f))
    version = re.search(r'VERSION_NAME=(\S+)', open(os.path.join(ROOT, 'version.properties')).read()).group(1)
    try:
        commit = subprocess.run(['git', 'rev-parse', '--short', 'HEAD'], cwd=ROOT, capture_output=True, text=True).stdout.strip()
    except OSError:
        commit = ''
    meta_path = os.path.join(RAW, 'meta.json')
    meta = json.load(open(meta_path)) if os.path.exists(meta_path) else {}
    data = {'lanes': lanes, 'pages': out_pages, 'edges': manifest['edges'],
            'meta': {'version': version, 'commit': meta.get('commit', commit), 'captured': meta.get('captured', datetime.date.today().strftime('%d %b %Y'))}}
    html = open(os.path.join(HERE, 'template.html'), encoding='utf-8').read().replace('/*APPMAP_DATA*/ null', json.dumps(data, ensure_ascii=False))
    open(os.path.join(DIST, 'index.html'), 'w', encoding='utf-8').write(html)
    print('built %d pages, %d arrows -> docs/app-map/dist/index.html' % (len(out_pages), len(manifest['edges'])))


if __name__ == '__main__':
    main()
