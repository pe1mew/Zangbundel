# Session record 2026-11-07 to 2026-11-15

Dated record, not a status board. Current state is in `memory/MEMORY.md`.

## Commits

From `02bb9fd` to `ed3f797`: FDS draft 0.8 readings (song format v0.24), indented lines and setlist meta (v0.25), package tag labels (v0.26 to v0.28, SP-40), the SNG-10 corpus cases, the numbered-verse-labels proposal (library issue #5, awaiting decision), **0.5.0** (`6bcf20c`), **0.5.1** (`435c0de`), the README status (`b3b66a5`), memory (`a8ab813`), test plan v0.22 (`6f08ba1`, `ac3fed8`), song format v0.29 (`1c890da`, the library session's), release checks after the tag (`09294d2`), the vector `chorus-label-most-recent` (`2f00b34`), SNG-19 and 60 package cases (`ed3f797`). The asks before 2026-11-12 are in those commits; this record holds the later ones.

## The asks, from 2026-11-12, and their threads

| Ask (the owner's words) | Thread |
|---|---|
| "is it possible to run the app on this laptop for demonstration?" (no changes) | Closed. Yes, in an Android emulator, which is not installed; the owner said "Not now". |
| "0.5.0 is on closed test but repo release is 0.4.0" | Closed. 0.5.0 had no tag and no GitHub release, and its G-09 had failed (SP-41). Replaced by 0.5.1. |
| "Yes, fix it" (the chorus crash) | Closed in `435c0de`: S013 above 32 768 lines, checked before copying. |
| "bump to 0.5.1 and write the release notes" | Closed in `435c0de`: versionCode 2026111201, notes in three listings and `fastlane/`. |
| "stage it when fuzzing is done" | Closed. The G-09 run found a second crash (`libraryOpen`, SP-42), fixed; the rerun of `libraryOpen` and `archive` passed an hour each (ended 2026-11-13 01:10). |
| "provide release notes" (Play's form) | Closed: nl-NL, de-DE, en-GB (the en-US text). |
| "collect my changes" | Closed: the owner had none. |
| "update the repo root" | Closed in `b3b66a5`: README status of 15 November 2026. |
| "fix the fuzz finding location in test plan and fuzz.sh", "fix the 14 targets count too" | Closed in `6f08ba1`, `ac3fed8`: `fuzz.sh` moves `crash-*` to the replayed inputs (tested with a dummy file); test plan v0.22 counts 16 targets. |
| "lets tag v0.5.1, G-07 and G-08, the GitHub release, pushing wiki/, and passing the chorus proposal to the library session" | Closed. The owner tagged `435c0de` and pushed the wiki (`88da4d3`) after the commands were given twice (gotcha-log 2026-11-15). G-07 passes (`2d88950d…` = CI run 43458929477); the owner signed; G-08 passes on the GitHub APK (`9c84cb01…`); release v0.5.1 published 14:30 UTC, Latest. The library session agreed S013 and wrote song format v0.29. |
| "add the chorus-label-most-recent vector" (the library session's request) | Closed in `2f00b34`: two choruses labelled R, `{chorus: R}` gives the most recent; the test fails on a wrong line list. |
| "Map it and copy" (S013 as SNG-19 in a package) | Closed in `ed3f797`: `PackageImport.sngCode` and `ReportText`; the manager's case `song-chorus-repeat-bound` (manager `42a7bd3`) copied, 60 cases, no new difference; test plan v0.23. The commit sent to the library session for its CI pin. |

## The owner's decisions

- 0.5.0 gets no tag; 0.5.1 replaces it on Play's internal and closed tracks (reported by the owner, 2026-11-15).
- The S013 bound in the app (2026-11-12); S013 reported as SNG-19 in packages (2026-11-15).

## Left open, the owner's

- G-08 on a Play install, D-01 to D-08 (RUNBOOK Step 9.4, 9.5).
- The German reviewer on 0.5.0's and 0.5.1's German notes; G-04 and D-10's capture.
