# Session record 2026-11-07 to 2026-11-15

Dated record, not a status board. Current state is in `memory/MEMORY.md`.

## Commits

From `02bb9fd` to `b3b66a5`: FDS draft 0.8 readings (song format v0.24), indented lines and setlist meta (v0.25), package tag labels (v0.26 to v0.28, SP-40), the SNG-10 corpus cases, the numbered-verse-labels proposal (library issue #5, awaiting decision), **0.5.0** (`6bcf20c`), **0.5.1** (`435c0de`), the README status (`b3b66a5`). The asks before 2026-11-12 are in those commits; this record holds the later ones.

## The asks, from 2026-11-12, and their threads

| Ask (the owner's words) | Thread |
|---|---|
| "is it possible to run the app on this laptop for demonstration?" (no changes) | Closed. Yes, in an Android emulator, which is not installed; the owner said "Not now". |
| "0.5.0 is on closed test but repo release is 0.4.0" | Closed. 0.5.0 had no tag and no GitHub release, and its G-09 had failed (SP-41). Replaced by 0.5.1. |
| "Yes, fix it" (the chorus crash) | Closed in `435c0de`: S013 above 32 768 lines, checked before copying. The app side is the owner's decision; the number and code wait for the library session (`design/proposals/2026-11-12-chorus-repeat-bound.md`). |
| "bump to 0.5.1 and write the release notes" | Closed in `435c0de`: versionCode 2026111201, notes in three listings and `fastlane/`. |
| "stage it when fuzzing is done" | Closed. The G-09 run found a second crash (`libraryOpen`, SP-42), fixed; the rerun of `libraryOpen` and `archive` passed an hour each (ended 2026-11-13 01:10). The owner committed. |
| "provide release notes" (Play's form) | Closed: nl-NL, de-DE, en-GB (the en-US text). |
| "collect my changes" | Closed: the owner had none. |
| "update the repo root" | Closed in `b3b66a5`: README status of 15 November 2026. |

## The owner's decisions

- 0.5.0 gets no tag; 0.5.1 replaces it on Play's internal and closed tracks (reported by the owner, 2026-11-15).
- The S013 bound in the app (2026-11-12).

## Left open, the owner's

- Tag `v0.5.1`, then G-07, G-08 and the GitHub release with the app-key APK (RUNBOOK Step 9.7); not on the remote on 2026-11-15.
- Push `wiki/` (the manuals say 0.5.1).
- Pass the chorus proposal to the library session: song format 5.5, 7.2, 11.3; `validate.py`; the manager.
- The German reviewer on 0.5.0's and 0.5.1's German notes; G-04 and D-10's capture.
- Test plan 3.4 and the `fuzz.sh` header say a finding lands under `src/fuzz/resources/`; Jazzer writes it to the module root (gotcha-log 2026-11-12).
