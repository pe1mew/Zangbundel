# Conformance corpus

Test vectors shared between this app and the library repository's validator (architecture A-21, A-22):
song files with their canonical bytes, hash, line list and report codes; setlists; archives good and bad
(one per A-code); protocol documents and frames with their bytes; sealed payloads opened with known keys.

Layout. `songs/index.txt`, `archives/index.txt` and `setlists/index.txt` list the vectors by name (setlists resolve against `setlists/library-state.json`), because a test reading them from a jar cannot list a directory. Archive vectors are `<name>.zbl` with `<name>.expected.json` (verdict, codes, and the SHA-256 of the vector itself, so a changed file is noticed):

```
conformance/
  songs/<name>.cho          input
  songs/<name>.expected.json canonical hash, line list, report codes
  setlists/, archives/, protocol/
```

Example songs are original texts released under CC0 (song-format section 13); no copyrighted lyrics.
