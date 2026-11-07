# Proposal: the app against the shared test libraries (FDS O-04, O-11)

| Field | Value |
|---|---|
| Proposal | Settle the 22 cases where the app's verdict or codes differ from the library manager's shared test libraries, one case on the format 1.1 path, and the ten readings of FDS open item O-11. For each one: whether the app changes, or the FDS and the `expected.json` change. |
| Date | 2026-11-06 |
| Status | **Approved by the owner 2026-11-06, in full** (section 7). The app's part and the song format's part are done. The FDS and the test libraries are for the manager's repository. |
| Asked by | The library manager's session, 2026-11-06 (manager version 0.1.0, FDS draft 0.7): add the 56 cases to the app's package tests, run them, and list every difference with a recommendation. |
| Affects | App: `PackageImport.kt`, `LibraryImport` (empty library), the tests. `design/song-format.md` 11, 12.2 and A003. The manager's `design/FDS.md` 3.6, 6.2 to 6.8, APP-01 and APP-23, and `test-libraries/` (`expected.json`, README, checker). Later, `design/test-plan.md` 3.2. |
| Requirements | FR-LIB-05, FR-LIB-07, FR-LIB-08 |

## 1. At a glance

| § | What differs | Cases | Recommendation | Who changes |
|---|---|---|---|---|
| 3.1 | The ZIP reader refuses the file before the app knows it is a package, so it reports the A-code | 6 | Accept the Archive column's code for the reader's checks | FDS 6.2, `expected.json` |
| 3.2 | Two top-level folders are not a package (APP-02): A001, not PKG-05 | 1 | The APP-02 reading (M-20) | FDS, `expected.json` |
| 3.3 | Song errors keep the profile's S-codes | 12 | Map them to SNG codes, as the app already does for PKG and SNG-18 | **App** |
| 3.4 | The app reports only the first refusing rule | 2 | Report the first; any one of `app.codes` passes | FDS APP-01, checker |
| 3.5 | An empty set is refused (VIEW-02) | 1 | Accept it, and don't offer it as a setlist | **App**, FDS APP-23 |
| 3.6 | An empty library is refused (L008) | 1 | Accept it, as the FDS says | **App** |
| 3.7 | Format 1.1: a song refused for S004 also counts for S005 in `release.py`, not in the app | 1 | The app's reading | Song format 11, `release.py`, `expected.json` |
| 4 | O-11 (a) to (j) | — | Seven readings agree with the app. (f), (g) and (i) need a change | See the table in section 4 |

## 2. What was run

- **The cases.** All 56 cases (`.zbl` and `.expected.json`) from `Zangbundel-bibliotheek-manager/test-libraries/` at `3d7624a` (version 0.1.0) were copied into `android/core/testing/src/main/resources/conformance/packages/`. The six cases the app already had were byte-identical to the manager's. `index.txt` lists all 56, and each SHA-256 is checked.
  - The cases are copied rather than read from the manager's repository, as for the app's existing vectors. CI has no checkout of the manager.
- **The test.** `PackageTest` runs each case through `LibraryOpen.open`, the app's import, and compares:
  - the verdict;
  - the FDS codes;
  - the song-format warnings;
  - `signed` and the fingerprint.

  It also runs `ArchiveImport.open`, the format 1.1 path, against `format_1_1`. A snapshot is counted as unsigned.
- **Result, 2026-11-06.**
  - App: 34 of 56 agree, and 22 differ.
  - Format 1.1: 55 agree, and 1 differs.
  - The test lists these 23 cases by name, with the section of this proposal that covers each. It fails when a new difference appears, and when a listed one disappears.
  - `./gradlew :core:songformat:test` passes with this list. The details of each run are written to `core/songformat/build/reports/package-vectors-app.md` and `package-vectors-format-1-1.md`.
- **The manager's point 3** (found by reading the code) is confirmed in every item. The six container cases of 3.1 are new.

## 3. The differences

### 3.1 The ZIP reader refuses before the format is known (6 cases)

| Case | Expected | App |
|---|---|---|
| `zip-truncated` | PKG-01 | A001 not a ZIP file |
| `zip-ratio` | PKG-02 | A003, 249,055 bytes from 6,653 |
| `path-traversal` | PKG-03 | A006 |
| `symlink` | PKG-03 | A005 |
| `entry-encrypted` | PKG-04 | A004 |
| `names-case` | PKG-07, STR-01 | A006 occurs twice |

**The verdict is right every time: refused.** Only the codes differ.

`PackageImport.isPackage` answers false when `ZipReader` refuses the file. `LibraryOpen` then takes the archive path, which reports the reader's A-code. `pkgCode` would map these codes to PKG codes, but it runs only inside `PackageImport.open`, which these files never reach.

**Recommendation: change the FDS and the `expected.json`.** FDS 6.2 would get one sentence: when the ZIP reader refuses a file, its format is not yet known (APP-02 reads content), so the app reports the code of the *Archive* column. Each of these cases would get `app.archive_codes`, and the checker would accept those codes in place of `codes`.

Why:
- For PKG-01 there is no choice. A truncated ZIP has no names, so nobody can tell it is a package.
- For the other five, finding the manifest means trusting names from a central directory the reader has just refused as hostile (assessment SP-09, SP-39).
- The Archive column already says these are the same checks.
- The user is told the same thing either way: the file is damaged or unsafe.

*Alternative:* the app reads the names after A002 to A006 and maps the codes with `pkgCode`. PKG-01 would stay A001 even then.

### 3.2 Two top-level folders (O-11 b)

**`two-top-folders`:** the case expects PKG-05; the app reports A001, "no zangbundel.json at the root or in one top-level directory".

Under APP-02 this ZIP is not a package: there is no `manifest.json` in a single top-level folder. It is not an archive or a snapshot either, so it is rejected with M-20. The case's own note allows this reading.

**Recommendation: the APP-02 reading.** The case's `expected.json` would get `app.codes` empty and `app.archive_codes` `["A001"]`. The Editor keeps PKG-05, because it opens the folder that holds the manifest.

### 3.3 Song errors keep the profile's S-codes (12 cases, and part of `song-folder-invalid`)

| Case | Expected | App | Detail the app gives |
|---|---|---|---|
| `song-not-utf8` | SNG-01 | S001 | |
| `song-no-title` | SNG-02 | S003 | `title` |
| `song-directive-twice` | SNG-03 | S006 | |
| `song-directive-malformed` | SNG-04 | S011 | |
| `song-environment-open` | SNG-06 | S007 | |
| `song-no-lyrics` | SNG-09 | S010 | |
| `song-no-language` | SNG-11 | S003 | `language` |
| `song-id-differs` | SNG-12 | S004 | |
| `song-chorus-early` | SNG-13 | S008 | |
| `song-new-song` | SNG-14 | S009 | |
| `song-value-invalid` | SNG-15 | S012 | |

`song-too-large` already agrees: `PackageImport` reports SNG-18 itself, before the song is parsed.

**Recommendation: change the app.** For a package, `PackageImport` would map the profile's song errors to the FDS codes, from the *Profile* column of FDS 6.6, as `pkgCode` does for the ZIP reader:

| Profile code | FDS code |
|---|---|
| S001 | SNG-01 |
| S002 | SNG-18 |
| S003 | SNG-02 (title), SNG-11 (language), SNG-12 (id), by the field |
| S004 | SNG-12 |
| S006 | SNG-03 |
| S007 | SNG-06 |
| S008 | SNG-13 |
| S009 | SNG-14 |
| S010 | SNG-09 |
| S011 | SNG-04 |
| S012 | SNG-15 |

`ReportText` would show the profile's existing message for each SNG code, so no new strings are needed in any language.

Why the app changes:
- FDS 6.2 says a package reports FDS codes.
- The app already does that for PKG codes and SNG-18.
- With one set of codes, the shared tests mean something, and the app and the Editor name the same rule.

*Alternative:* the `expected.json` accepts the profile codes as well.

### 3.4 The first refusing rule (O-11 a; `license-missing`, `song-folder-invalid`)

| Case | Expected | App |
|---|---|---|
| `license-missing` | MAN-07, VIEW-02 | MAN-07 |
| `song-folder-invalid` | STR-10, SNG-12 | STR-10 |

In both cases the app reports the code that the case's note names for an app that stops at the first rule. `names-case` (3.1) is the same kind of case.

**Recommendation: the app reports the first refusing rule, and any one of `app.codes` passes.** APP-01 would say that the app refuses with the first rule it meets and names that rule. The checker would accept one code if it is one of `app.codes`. No order is fixed, because the order in which an implementation meets the rules is its own.

Why:
- A package is accepted whole or not at all. The person importing it cannot repair it, and one reason is enough to tell them.
- Stopping early means less of an untrusted file is processed (SP-39).
- The Editor, where someone can repair the library, lists every finding.

### 3.5 An empty set (SET-03, O-11 h; `set-empty`)

**What the app does today:** it refuses `set-empty` with VIEW-02, "not a readable setlist". The view's `set-2.zbs` has no songs, which the setlist parser refuses as SL005 (song format 13). `LibraryImport` then drops it, so the view has one setlist fewer than the manifest has sets.

**What the FDS says:** SET-03 is a Warning, and the Editor saves empty sets. An app that refuses them breaks APP-01's premise: an Error means damage or a change outside the Editor.

**Recommendation: change the app.**
- An empty set is accepted.
- Its view file is checked by content: the title, the id `set-<n>`, and no songs.
- It is not offered as a setlist, because it holds nothing to sing. This is also what the format 1.1 path does with it (SL005).
- FDS APP-23 would say that an empty set is not offered.

*Alternative:* the app offers the set as an empty setlist. That would touch the setlist screens.

The app compares a view setlist by content, not by bytes, so the form of the empty-set file in O-11 (h) does not matter to it. The case's reading, the canonical form without a trailing empty line, is fine.

### 3.6 An empty library (STR-02; `no-songs`)

**What the app does today:** it refuses `no-songs` with L008, the format 1.1 library error.

**What the FDS says:** STR-02 is a Warning, and the Editor saves an empty library.

**Recommendation: change the app.** For a package, the app accepts an empty library and leaves out L008. The library screen then shows no songs. The format 1.1 path keeps L008 and its confirmation, unchanged. This follows the same reasoning as 3.5: the app does not refuse what the Editor saves.

*Alternative:* the app asks the format 1.1 path's confirmation for packages too. Under APP-05 that question could come on top of another one (M-73), so the FDS would have to say which one comes first.

### 3.7 Format 1.1: S004 and S005 (`song-two-files`)

This case concerns the app and the library repository's `release.py`, under the song format. The FDS is not involved.

The song folder `lorem-ipsum` holds `lorem-ipsum-oud.cho`, which has the id `lorem-ipsum`, next to `lorem-ipsum.cho`.

| Implementation | What it reports | Result |
|---|---|---|
| The app | S004 for the copy (id differs from the file name) | The copy is skipped; `lorem-ipsum.cho` imports |
| `release.py` | S004 for the copy, then S005 for both files (duplicate id), then SL101 | The first in path order is the copy, which S004 has already refused, so neither song imports. The set's entry is not found (SL101) |

**Recommendation: the app's reading.** A file refused for another error is not "another song in this library" (S005). Song format 11 would say so. The case's `format_1_1.codes` would become `A102, S004`, and `release.py` would follow. The good song survives, which is what the song format means by skipping bad songs and keeping the rest.

The song format is this repository's specification, so its change is mine to make after approval. The library repository then pins the new conformance reference (test plan 3.2).

## 4. The O-11 readings

| | Reading | What the app does today | Recommendation | Who changes |
|---|---|---|---|---|
| (a) | Every refusing rule, or only the first? | The first | The first; see 3.4 | FDS APP-01, checker |
| (b) | Two top-level folders: PKG-05 or M-20? | Not a package, A001 | M-20; see 3.2 | FDS, `expected.json` |
| (c) | MAN-07 reported as itself | MAN-07 (`parseManifest`) | Agrees. The FDS text "in the app part of MAN-02" becomes "checked with MAN-02 and reported as MAN-07", as for MAN-08 | FDS MAN-07 |
| (d) | A second `.cho` is STR-06 only | STR-06. STR-08 applies only to files that are not `.cho` | Agrees. STR-08 is reworded to "a file that is not a `.cho` file" | FDS STR-08 |
| (e) | Song rules only for `songs/<id>/<id>.cho` | Agrees: the structure rules run first, so only those files reach the song checks | Agrees | FDS 6.6, one sentence |
| (f) | PKG-02 ratio: against the ZIP file size, or the compressed total? | The ZIP file size (`ZipReader`), as FDS 3.5 and the manager's checker | **The ZIP file size, everywhere.** It is a fact of the file; the compressed sizes are claims the file makes about itself in its central directory. Song format 12.2 and A003 say "compressed size", and `release.py` uses the compressed total: both would change. No case changes: `zip-ratio` exceeds both | Song format 12.2 and A003, `release.py` |
| (g) | The fixed fields of PF-41: VIEW-03 or VIEW-02? | VIEW-03 when the view fails L002 to L006. Any other departure from PF-41, fixed and unknown fields included, is VIEW-02 | **The app's reading.** VIEW-03 is defined by L002 to L006. A view with `min_app_version` `0.2.0` passes L005 in a new app, so under the cases' reading it would fail no rule at all. VIEW-02 would name the fixed fields, and the manager would add a case for this. No current case differs | FDS VIEW-02, a new case |
| (h) | PF-43 for an empty set | Compares by content, so either form passes | The canonical form, as the case has it; see 3.5 for the refusal | — |
| (i) | Ignored system files regardless of case? | Exact case only (`desktop.ini`), as the manager's checker | **Regardless of case.** On Windows `Desktop.ini` is the same file, and no library file may carry any of these names anyway. It is a one-line change in the app's `ignored()`, plus a sentence in FDS 3.6 and a case | **App**, FDS 3.6, a new case |
| (j) | SIG-03 skipped when `author.publicKey` fails MAN-02 | Agrees: the manifest is refused before the signature is read (`key-wrapped` passes) | Agrees | — |

## 5. FR-LIB-05

**No change is needed.** FRD v0.26 (2026-11-06) already made the maintainer contact required for a package again (FDS D-36), and its change-history row says so. The two other points of v0.24 still stand:
- the format version serves as the minimum app version;
- FR-LIB-07 reports bad songs only for release archives and snapshots, while a package is accepted whole or not at all.

The app requires the maintainer (MAN-08, M-75), and `maintainer-no-contact` agrees.

## 6. If approved

- **App:** 3.3, 3.5, 3.6 and (i). The cases they fix leave the test's list of known differences.
- **This repository's song format:** 3.7 and (f). Then `release.py` in the library repository.
- **The manager (FDS and test libraries):**
  - 3.1, 3.2, 3.4, (c), (d), (e), (g) and (i);
  - the cases for (g) and (i);
  - copying the changed cases back here.
- **Test plan 3.2:** the `packages/` row names the 56 cases.

## 7. The owner's decisions

**2026-11-06: "Approve all".** Every recommendation in sections 3 and 4 is taken as written.

What was done in this repository the same day:
- **App, 3.3.** `PackageImport.sngCode` maps the profile's song errors to SNG codes. `ReportText` gives each SNG code the profile's sentence.
- **App, 3.4.** A package reports only its first refusing rule. Before, a package with song errors reported all of them.
- **App, 3.5.** An empty set is accepted. Its view file is read with `SetlistParser`'s `allowEmpty`, and it is neither offered nor installed as a setlist.
- **App, 3.6.** An empty package is accepted, with `LibraryImport`'s `allowEmpty`. The format 1.1 path keeps L008.
- **App, (i).** System files are ignored regardless of case.
- **Song format v0.23:** S005 (3.7), and the ratio of 12.2 and A003 (f).
- **Test plan v0.21:** 3.2.
- **Tests.**
  - The app now gives 47 of the 56 cases their expected verdict and codes.
  - The 9 that still differ are 3.1 (six cases), 3.2 (one) and 3.4 (two). For each of them the app keeps its reading and the manager changes the `expected.json`.
  - On the format 1.1 path, `song-two-files` (3.7) differed until 2026-11-07. Then the manager corrected its `expected.json` to `A102, S004`, and the copy here agrees, so the format 1.1 path agrees on all 56.
  - New tests cover the empty set, the empty library, system files in any case and the SNG mapping.

Still to do elsewhere:
- **The manager:** the FDS changes and the `expected.json` changes of section 6, and the new cases for (g) and (i).
- **The library repository:** `release.py` for 3.7 and (f). *Done 2026-11-07* (`Zangbundel-bibliotheek` `784743f`). Its `CONFORMANCE_REF` pin moves to the app commit that carries the corrected `song-two-files.expected.json`.
