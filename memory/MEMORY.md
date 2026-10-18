# Memory

<!-- NOT loaded automatically. Reached by the "Picking up where the last session
     left off" row in CLAUDE.md. Keep it lean — it is read in full whenever it is
     reached. Deep knowledge goes in topic files; this is the index.

     (Claude Code auto-loads a different file with the same name, the user-level
     one under ~/.claude/projects/<slug>/memory/. This one is the committed, shared
     project memory.)

     END-OF-SESSION CURATION (/curate):
     1. Review memory/gotcha-log.md for recurring patterns — promote them here or to topic files
     2. Retire entries below that are now encoded in the documents, the check script or CLAUDE.md
     3. Update "Current State" to reflect what changed -->

## Topic Files

| File | When to load | Key insight |
|------|-------------|-------------|
| `memory/gotcha-log.md` | Stuck or debugging | Problem-fix archive. Empty at adoption (2026-10-18); the Mechanized table already lists what `scripts/check-design.sh` catches. |

## Current State

- **Design phase, no code.** Three specifications are consistent with each other: FRD v0.4, BLE protocol document v0.2 (protocol version 1.0), song format document v0.1 (format version 1.0). `bash scripts/check-design.sh` passes. FRD v0.4 (section 8.5, Appendix A) is staged, not yet committed.
- **App distribution decided at requirement level:** QR code with the store link is required; offline Android-to-Android sharing of the app's own package is a SHOULD and needs a build decision (universal APK or bundled splits, FRD Q11); Google Play's own "share apps" must not be relied on because Google is withdrawing it; iOS has no offline route.
- **Nothing is implemented.** `android/` is empty. No architecture document, no UI flows, no test plan yet; the FRD's section 19 lists them as documents still to write.
- **Open questions are where the work is.** Each design document ends with a numbered open-question table. The ones that gate implementation: FRD Q1/Q4 (group size, song format choice — Q4 is now answered by song-format.md and should be closed in the FRD), protocol Q2/Q3 (advertising-set counts and battery on real phones, needs a field test), Q10 (delegate upload in 1.0 or later).
- **Repository hygiene landed 2026-10-18:** agent-ready scaffold, README, contributing guide, code of conduct, licence files, changelog. The owner commits; nothing was committed by an agent.

## Recently Promoted

<!-- Gotchas that proved their value and were promoted from the gotcha log.
     Retire entries from this section as soon as they appear in their destination.
     Format: "if [situation], then [what to do] — promoted from gotcha-log YYYY-MM-DD" -->

## Key File Paths

<!-- Supplements the project file's list with paths discovered during work -->

| Path | Why it matters |
|------|----------------|
| `design/functional-requirements.md` section 1.3 | ID and priority conventions every other document relies on |
| `design/functional-requirements.md` section 20 | Requirement summary by phase; derived from the body, update it when adding or re-tiering a requirement |
| `design/ble-protocol.md` section 7 | The broadcast carousel: the part most likely to change after field tests |
| `design/ble-protocol.md` section 16 | Protocol-to-requirement traceability; add a row for every new FR-BLE/FR-GATH/FR-LEAD requirement |
| `design/song-format.md` section 8–9 | Canonical form, content hash and line list that the protocol hashes against |
| `changelog.md` | `[Unreleased]` gets a line for every notable change |

## Active Decisions

<!-- One-liners about architectural choices. No docs/adr/ yet: each decision below is
     recorded in the document named. Create docs/adr/ and an index when one of these is
     re-litigated or when the first implementation decision is made. -->

- **Broadcast first, connections only upstream and for repair** — leader-to-singer data goes out as a repeated advertising carousel; GATT is for registration, requests, delegate commands and recovery of missed frames. Rationale and options in `design/ble-protocol.md` section 4 (2026-10-18).
- **Songs matched by content hash, not library revision** — two library revisions that did not touch a song still match; `design/ble-protocol.md` section 5.
- **Signed state with the gathering ID as key fingerprint** — Ed25519 key per gathering; every broadcast document is bound to the signed state; `design/ble-protocol.md` sections 5, 9.4, 13.
- **Delegates act through the leader's device** — the leader stays the single signer and authority; `design/functional-requirements.md` section 8.4, `design/ble-protocol.md` section 9.8.
- **One gathering per BLE range, enforced at start only** — FRD FR-GATH-16/17 and assumption 9.
- **Song files are a ChordPro profile** — `design/song-format.md` section 2; FRD open question Q4 should be marked answered.
- **Lyrics never live in this repository** — copyright stays with the external libraries; `license.md`, CLAUDE.md hard constraints.
