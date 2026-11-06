# Library packages: first run in the app – 2026-11-06

The package reader, the decision table and opening a library from another app (FRD v0.26 FR-LIB-08, FR-LIB-09, FR-LIB-18; architecture A-24, A-26; guiDesign D-87, D-88; assessment SP-37, SP-39), against the library manager's shared test libraries (FDS O-04) and on a phone. **Result: the automated checks pass; on the A52 every package question, a refusal, the change note and a real Share from the file manager pass; D-09 per messaging channel is not run.**

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

## Second run on the A52: every question, a refusal and a real Share

Variants of the shared test library, built with the library manager's own generator (`make_test_libraries.py`), so their rules are exactly those of the vectors: version 2 with the same key; versions 3 and 4 with a second key (fingerprint `10BA-682C-8AD1-3513`); a different library with the same title; and the 1.1 view alone, without manifest, as an unsigned copy. They were opened in turn by Open (VIEW) with a read grant, starting from version 1 installed.

| Step | Pass criterion (guiDesign D-87, D-88; FDS §8.3) | Result |
|---|---|---|
| Version 2, same key | Updates without a question | Pass: licence, then *Zangbundel testbibliotheek bijgewerkt* |
| Version 1 again | M-74 | Pass: *Dit is een oudere versie van de bibliotheek dan die je hebt: versie 1 in plaats van 2. Toch vervangen?*; *Annuleren* kept version 2 |
| Version 3, other key | M-70, no version sentence (newer), fingerprints in monospace | Pass: both names and fingerprints, monospace on the screenshot; *Toch importeren*, licence, *bijgewerkt*; the row then names the second key |
| Other library, same title | M-73 with three buttons | Pass: *Apart toevoegen*, *Vervangen*, *Annuleren* |
| The view alone (unsigned copy, APP-06) | M-71 naming the current signer, no version sentence | Pass: *…ondertekend door Tweede testsleutel (niet voor echt gebruik) (10BA-682C-8AD1-3513), maar deze update is niet ondertekend. Toch importeren?* |
| `view-mismatch.zbl` (shared vector) | Refused, the sentence names the file (D-88) | Pass: *Het bestand “LICENSE” past niet bij de bibliotheek. Sla de bibliotheek opnieuw op in de editor.*, with `LICENSE · VIEW-02 · …` below |
| Version 4 | Its change note at the top of the report (D-87, APP-24) | Pass after a fix (it was missing in the first build): *Bij versie 4: Vierde versie: de notitie verschijnt na het bijwerken.* |
| **Share from Samsung's *Mijn bestanden*** (a real SEND with ClipData and read grant) | The share sheet offers Zangbundel; the import starts | Pass: Zangbundel is listed under *Meer*; the file arrived and the import asked M-73. *Apart toevoegen* installed the second library next to the first. |

The test files were deleted from the A52's Downloads afterwards; the test libraries stay in the debug build only, which is a separate app from the installed Zangbundel.

## Not run

- D-09 per channel: WhatsApp, Signal, Telegram, Discord, Gmail, a browser download and Quick Share. These need the owner's accounts.
- The Huawei.
