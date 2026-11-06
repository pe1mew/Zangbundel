# Library packages: first run in the app – 2026-11-06

The package reader, the decision table and opening a library from another app (FRD v0.26 FR-LIB-08, FR-LIB-09, FR-LIB-18; architecture A-24, A-26; guiDesign D-87, D-88; assessment SP-37, SP-39), against the library manager's shared test libraries (FDS O-04) and on a phone. **Result: the automated checks pass; D-02 passes in part on the A52; D-09 not run.**

| Field | Value |
|---|---|
| Build | debug build of the working tree, versionName `0.3.0`, package `nl.pe1mew.zangbundel.app.debug` |
| Vectors | `android/core/testing/src/main/resources/conformance/packages/`: the six test libraries of `Zangbundel-bibliotheek-manager/test-libraries/` (valid-signed, valid-unsigned, view-missing, view-mismatch, signature-invalid, key-wrapped), copied unchanged with their `expected.json` |
| Phone | Samsung Galaxy A52, Android 14, with the default library 2026.11.0 installed |

## Automated

| Check | Result |
|---|---|
| `PackageTest` (core/songformat, 6 tests) | Pass. Every vector gives the app's verdict, FDS codes, song-format warnings (W013 for valid-unsigned only), signing status and fingerprint (`21FE-31DF-A154-A261`, verified with the JDK's Ed25519). Every vector's view is accepted as a snapshot with A102 by the format 1.1 path, which never reads the manifest. Manifest rules MAN-02, MAN-03, MAN-07, MAN-08 and SET-02. W013 once for a release archive and a snapshot, none for a refused archive. The raw key's fingerprint, and the wrapped key's `06E3-FD8F-DA29-BB60`. |
| `PackageDecisionTest` (app, 5 tests) | Pass. A new package installs without a question and is remembered with its key and version. The same version asks M-72; the unsigned one asks M-71 with the version sentence. The package's view without its manifest, an unsigned copy, asks M-71 without the sentence and on acceptance leaves the memory "unsigned" (APP-06). A different library with the same title asks M-73, and *Vervangen* removes the other. A refused package asks nothing. |
| `./gradlew check assembleDebug` | Pass: every unit test and lint, the new intent filters included. Database version 6, migration 5 to 6 generated. |
| Fuzzing, `scripts/fuzz.sh 60` | `packageManifest` and `libraryOpen`, 60 s each: no findings. |

## On the A52 (D-02, in part)

| Step | Result |
|---|---|
| `valid-signed.zbl` handed to Zangbundel by Open (VIEW, `content://media/…`, read permission granted) | The library screen opened at the licence step: *Zangbundel testbibliotheek*, 4 songs, CC0-1.0. No question, as for an unknown library. |
| *Accepteren* | *Zangbundel testbibliotheek toegevoegd*, *Geïmporteerd: 4 · Overgeslagen: 0 · Waarschuwingen: 0* |
| The row (D-87) | *Versie 1, 2026-11-06*; *Ondertekend door RFC 8032 test key (not for real use) · 21FE-31DF-A154-A261*; *Beheer: Zangbundel-project · github.com/pe1mew/Zangbundel-bibliotheek-manager*; *Licentie: CC0-1.0*; *Bij versie 1: Eerste versie van de testbibliotheek.*; *Verwijderen* only. The default library's row: *Niet ondertekend*, *Licentie: CC-BY-NC-4.0*, *Bijwerken* kept. |
| The same file again | M-72: *Je hebt versie 1 van deze bibliotheek al. Vervangen door deze kopie?* *Annuleren* left the library as it was. |
| A Share (SEND) whose URI the app may not read | Before the fix the app crashed (`SecurityException` in `readPicked`). After it the import is refused with *Niet toegevoegd* (A001); the app stays up. The test itself sent no read grant: `am` grants the intent's data, not `EXTRA_STREAM`, which a real sharing app also puts in its ClipData. |

## Not run

- D-09, Share and Open from WhatsApp, Signal, Telegram, Discord, Gmail, a browser download and Quick Share.
- A Share with a working grant, which needs one of those apps.
- The Huawei, and the other D-02 steps for a package: refused packages on the phone, M-70, M-73 and M-74.
- The test library and `valid-signed.zbl` in Downloads are left on the A52.
