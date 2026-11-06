#!/usr/bin/env python3
"""Prototype for design/proposals/2026-11-06-backward-compatible-packages.md, section 5.

Builds a library package that also carries a format 1.1 view, laid out as section 3 of the
proposal describes, from one song of the conformance corpus:

    Troop 12/
      manifest.json, manifest.sig      the package (FDS); the signature is a placeholder
      zangbundel.json, LICENSE         the 1.1 view, generated from the manifest
      songs/vader-jacob/vader-jacob.cho
      setlists/set-1.zbs               the manifest's set as a 1.1 setlist

Check it with the 1.1 import rules of the library repository's release tool:

    python design/proposals/2026-11-06-dual-package-prototype.py troop12.zbl
    python <library repository>/scripts/release.py verify --info troop12.zbl

Standard library only. Run from the repository root.
"""

import hashlib
import json
import sys
import zipfile

CORPUS = 'android/core/testing/src/main/resources/conformance'
LIB_ID = '3f6c2a1e-8b4d-4c1a-9e2f-7d5b0a6c9e11'


def main(out):
    with open('%s/songs/vader-jacob.cho' % CORPUS, 'rb') as fh:
        song = fh.read()
    view = {
        'format': 'zangbundel-library', 'format_version': '1.1', 'id': LIB_ID,
        'name': 'Troop 12 Campfire Songs', 'description': 'Songs for camps and campfires.',
        'maintainer': {'name': 'Troop 12', 'email': 'songs@example.org'},
        'license': {'id': 'CC0-1.0', 'file': 'LICENSE', 'summary': 'Free to use.'},
        'min_app_version': '0.1.0',
    }
    files = {
        'zangbundel.json': (json.dumps(view, indent=2) + '\n').encode('utf-8'),
        'LICENSE': b'Free to use.\n',
        'songs/vader-jacob/vader-jacob.cho': song,
        'setlists/set-1.zbs': ('{title: Zomerkamp}\n{meta: id set-1}\n\n%s/vader-jacob\n' % LIB_ID).encode('utf-8'),
    }
    manifest = {
        'formatVersion': 1, 'libraryId': LIB_ID, 'version': 4, 'title': view['name'],
        'description': view['description'],
        'created': '2026-03-14T18:22:05Z', 'modified': '2026-11-04T09:41:30Z',
        'license': {'id': 'CC0-1.0', 'summary': 'Free to use.', 'text': 'Free to use.'},
        'maintainer': view['maintainer'],
        'sets': [{'name': 'Zomerkamp', 'songs': ['vader-jacob']}],
        'files': [{'path': p, 'size': len(b), 'sha256': hashlib.sha256(b).hexdigest()}
                  for p, b in sorted(files.items())],
    }
    files['manifest.json'] = (json.dumps(manifest, indent=2) + '\n').encode('utf-8')
    files['manifest.sig'] = b'\0' * 64   # placeholder: a 1.1 app never reads it
    with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as zf:
        for path in sorted(files):
            zf.writestr('Troop 12/' + path, files[path])
    print('wrote', out)


if __name__ == '__main__':
    main(sys.argv[1] if len(sys.argv) > 1 else 'troop12.zbl')
