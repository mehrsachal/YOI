#!/usr/bin/env python3
"""Writes docs/sbom.json (CycloneDX 1.5): every library that ships in the release APK, with its licence.

    ./gradlew -q :app:dependencies --configuration releaseRuntimeClasspath > deps.txt
    python3 tools/make_sbom.py deps.txt

Licences are read from each library's published POM (Google Maven and Maven Central), following parent POMs."""
import json, re, sys, urllib.request, xml.etree.ElementTree as ET
REPOS = ['https://dl.google.com/android/maven2', 'https://repo1.maven.org/maven2']
coords = set()
for line in open(sys.argv[1]):
    m = re.search(r'[+\\]--- ([\w.\-]+):([\w.\-]+):([\w.\-]+)?(?: -> ([\w.\-]+))?', line)
    if m and m.group(3 if not m.group(4) else 4): coords.add((m.group(1), m.group(2), m.group(4) or m.group(3)))
    m = re.search(r'[+\\]--- ([\w.\-]+):([\w.\-]+) -> ([\w.\-]+)', line)
    if m: coords.add((m.group(1), m.group(2), m.group(3)))
cache = {}
def pom(g, a, v):
    if (g, a, v) not in cache:
        cache[(g, a, v)] = None
        for r in REPOS:
            try:
                raw = urllib.request.urlopen(f"{r}/{g.replace('.', '/')}/{a}/{v}/{a}-{v}.pom", timeout=30).read()
                cache[(g, a, v)] = ET.fromstring(re.sub(rb'xmlns="[^"]+"', b'', raw, count=1)); break
            except Exception: pass
    return cache[(g, a, v)]
def licences(g, a, v, depth=0):
    p = pom(g, a, v)
    if p is None or depth > 4: return []
    found = [l.findtext('name') for l in p.findall('licenses/license') if l.findtext('name')]
    par = p.find('parent')
    return found or (licences(par.findtext('groupId'), par.findtext('artifactId'), par.findtext('version'), depth + 1) if par is not None else [])
comps = []
for g, a, v in sorted(coords):
    comps.append({'type': 'library', 'group': g, 'name': a, 'version': v, 'purl': f'pkg:maven/{g}/{a}@{v}',
                  'licenses': [{'license': {'name': n}} for n in licences(g, a, v)]})
bom = {'bomFormat': 'CycloneDX', 'specVersion': '1.5', 'version': 1,
       'metadata': {'component': {'type': 'application', 'group': 'app.pquest', 'name': 'P-Quest', 'version': '1.0.0'}},
       'components': comps + [{'type': 'file', 'name': 'Noto Nastaliq Urdu (bundled font)', 'licenses': [{'license': {'id': 'OFL-1.1'}}]}]}
json.dump(bom, open('docs/sbom.json', 'w'), indent=1)
print(len(comps), 'libraries')
