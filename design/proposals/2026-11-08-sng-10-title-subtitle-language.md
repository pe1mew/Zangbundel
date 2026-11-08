# Decision: SNG-10 compares title, subtitle and language

| Field | Value |
|---|---|
| Change | FDS rule SNG-10 reports two songs only when they have the same title, subtitle and language, case-insensitively. A missing subtitle counts as the same subtitle. Before, the title alone was compared. |
| Date | 2026-11-08 |
| Status | **Decided by the owner, 2026-11-08** (FDS draft 0.12, D-46). |
| Written by | The library manager session, from the owner's review of the manager's first trial release. |
| Affects | The library manager's FDS (SNG-10, M-59, D-46), its engine and its test libraries. In the app: only the conformance corpus `packages/`. |

## 1. Why

The Drasva library holds *Alouette* three times: in English, French and Dutch, each with its own subtitle and song ID. The manager reported them as a duplicate title. The owner: three songs that share a title are no error.

Comparing title alone gave 11 groups in that library. Every one of them differs in language or subtitle, so the new rule gives none.

## 2. What changes for the app

- **Behaviour:** nothing. SNG-10 is a Warning, and the app does not show Warnings (FDS 6.1), so no import changes.
- **Rule (principle 2):** an app that checks SNG-10 internally compares title, subtitle and language, as the manager does.
- **Corpus:**
  - `song-title-duplicate` changed: `lorem-ipsum` now also has Dutch and no subtitle, as `vader-jacob`, so it is still a duplicate under the new rule. The app's verdict is unchanged: accepted, W013.
  - `song-title-other-language` added: the same title alone. Accepted, W013, and no SNG-10 in the Editor.
  - There are now 59 cases. Copy both `.zbl` files and their `expected.json` from `Zangbundel-bibliotheek-manager/test-libraries/` into `packages/`.
- **Song format:** no change. Its section 11 has no rule on repeated titles.
