# Conformance corpus

Test vectors shared between this app and the library repository's validator (architecture A-21, A-22):
song files with their canonical bytes, hash, line list and report codes; setlists; archives good and bad
(one per A-code); protocol documents and frames with their bytes; sealed payloads opened with known keys.

Layout. `songs/index.txt`, `archives/index.txt`, `setlists/index.txt` and `libraries/index.txt` list the vectors by name (setlists resolve against `setlists/library-state.json`), because a test reading them from a jar cannot list a directory. Archive vectors are `<name>.zbl` with `<name>.expected.json` (verdict, codes, and the SHA-256 of the vector itself, so a changed file is noticed):

```
conformance/
  songs/<name>.cho          input
  songs/<name>.expected.json canonical hash, line list, report codes
  setlists/, archives/, protocol/
  libraries/<name>.json      a zangbundel.json
  libraries/<name>.expected.json verdict and codes for the app version it names, SHA-256 of the vector
```

The library repository runs the songs, setlists, libraries and archives vectors against its Python validator and release tool with `scripts/conformance.py`, at a commit of this repository pinned in its validation workflow (`CONFORMANCE_REF`). A vector added here is checked there once that pin moves; a disagreement is a bug in one implementation or in the specification and is fixed before either side releases (test plan 3.2).

Example songs are original texts released under CC0 (song-format section 13); no copyrighted lyrics.
