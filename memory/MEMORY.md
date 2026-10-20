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
| `memory/gotcha-log.md` | Stuck or debugging | Problem-fix archive. Five entries since 2026-10-19 (PlantUML on Java 8, diagram layout, parallel sessions, GUI wishes against FRD MUSTs); the Mechanized table lists what `scripts/check-design.sh` catches. |
| `memory/project_session_2026_10_19.md` | Wondering how the GUI design, the setlist format or the library release came about | Dated record of the 2026-10-19 sessions: the ask, the thread list, what was decided and left open. |

## Current State

- **Design phase, no code.** Specifications consistent with each other on 2026-10-20: FRD v0.10, BLE protocol document v0.12 (protocol version 1.0), song format document v0.4 (format version 1.1 with setlists, archive version 1.1), GUI design v0.8 (`design/guiDesign.md`, decisions D-01 to D-80), security/privacy assessment v0.3 (SP-16). `bash scripts/check-design.sh` passes.
- **Languages decided 2026-10-20** (`design/guiDesign.md` section 8, D-75 to D-80): app texts and song texts are separate layers; Dutch is primary, English the fallback (`values/` English, `values-nl/` Dutch); generated section labels follow the app language; library names are not translated; no other languages and no right-to-left for now (FRD Q8 closed); the delegate role is *assistent*, its code the *assistentcode*; a glossary of fixed terms is D-79.
- **Joining by QR code is the trust anchor against fake gatherings** (FR-GATH-19 to 23, BLE 6.5): the code carries the leader's public key. The link host is GitHub Pages from this repository, live since 2026-10-18 at `https://pe1mew.github.io/Zangbundel/j/` (Q13 resolved). App Links verification waits for the Play app-signing key (RUNBOOK Step 5c). Local check: `.claude/launch.json` config `docs-pages`.
- **Secure by design, privacy by design (users under 18) is an architecture principle since 2026-10-18** (FRD 2.6). The first assessment found two High items that were fixed at requirement level: default gathering names derived from the device name (SP-01) and Android auto-backup copying gathering data to the cloud (SP-05). SP-16 (2026-10-19) assessed opt-in campfire statistics and recorded the owner's decision: share-only through the platform share sheet, no server, so "nothing reaches the developer" stays true. Re-run the assessment when the architecture document is written and before each release.
- **GUI design exists (2026-10-19, `design/guiDesign.md` v0.7, D-01 to D-74).** Two screen families (configuration screens with one background photograph, gathering screens white on black); home screen with join prominent and start discreet; leader and singer modes; the singer lyric view as one page of the songs received this session, three round buttons, songlist sheet with likes; D-48 "the phone is not the attraction" ranks above later decisions and holds features back; leader mode with libraries, colour-coded songbook, setlists, the leader's page with *Nu* separators, the slide-open leader bar with live statistics, helper-code delegates, closing bar. Assets in `design/gui/` (backdrop photograph with children seen from behind, portrait and landscape WebP, flowchart). FRD section 19 still names the document `ui-flows.md` (guiDesign Q5, not yet fixed in the FRD).
- **BLE sequence diagrams for the technical design** in `design/ble/`: seven scenarios (start, join, push, repair, leave, end, helper-code delegate) as `.puml`, `.svg`, `.png`; render with the PlantUML jar from `C:\Users\drasv\github\greenhouse-Controller\design\plantuml.jar` on Java 8 with `-charset UTF-8`, one run per format (`-tsvg`, then `-tpng`).
- **Decided 2026-10-19 at requirement level:** no library download instruction in phase 1 (FR-LEAD-30, 31 withdrawn, FR-LEAD-32 reworded; BLE `download` document reserved); delegates appointed with a four-digit helper code, no singer list (FR-LEAD-40 amended, BLE Control op 9); explicit leave (BLE Control op 10); no hand-over in phase 1 (FRD Q5 closed); join code and QR-only fixed per gathering. Singers see only the songs the leader pushed (D-47); pushed songs are deleted when the gathering ends and the setlist stays as a memory (D-49).
- **Setlists are format 1.1** (song-format section 13): `.zbs` files with `{title}` and `{meta: id}`, one `library-id/song-id` per line, `? title` for title-only entries, `setlists/` in repositories and archives, `setlists` manifest field. Unresolved entries are shown with their reason and skipped at the fire, never silently (D-74).
- **Library repository `C:\Users\drasv\github\Zangbundel-bibliotheek`** (`github.com/pe1mew/Zangbundel-bibliotheek`): eight Dutch songs, validator `scripts/validate.py`, release tool `scripts/release.py`, release 2026.10.0 published as `.zbl` with `releases.json`; four songs have an untraceable author and are marked `onbekend` / `proprietary` with a public credits-and-corrections notice. Staged there on 2026-10-19, not committed: setlist support in validator and release tool, example setlist, `format_version` 1.1.
- **Play app entry exists (2026-10-18): app id 4974489515379733805, package `nl.pe1mew.zangbundel.app`.** All App content declarations, category, contact details and the privacy URL are saved; nl-NL listing texts are a draft. Left before the first upload: store graphics, en-US translation, the upload keystore (owner runs keytool, RUNBOOK Step 6). The dashboard counts 9 of 11 setup tasks done; the listing task completes only with graphics. Everything from the first bundle on waits for the Android project (architecture document first).
- **App icon decided 2026-10-18:** proposal 02 "Kring van zangers" (five singers around an orange songbook with Bluetooth ripples, night-blue background). Master and launcher layers in `design/icon/`, Play PNG in `docs/store-listing/graphics/`. Rasterising SVG here works through the browser pane: serve the repo with the `repo-root` launch config, draw the SVG on a canvas from an HTML page on the same origin, save the data URL; the SVG document itself has no HTML canvas. A feature graphic (1024 × 500, same artwork plus name and tagline) is rendered too. Still to upload on Play: the icon and the feature graphic; screenshots need the app. Pulling a rendered PNG out of the browser pane: run a tiny Python server with a POST handler on the repo (scratchpad `save-server.py` pattern), serve the HTML page from the same origin, and POST the canvas blob; returning base64 through the tool result is too large.
- **Play Console quirks learned:** checkboxes and radios set by script do not reach the form model, use real clicks; the browser coordinate frame is 800 wide while the page is 880 wide (scale 0.909); the "Tags" dialog markup is always in the DOM, so `[role=dialog]` is not a reliable open-dialog test; a stale ref clicks the wrong spot after the layout shifts, recompute from a fresh screenshot.
- **Library distribution unit is the `.zbl` release archive** (song-format section 12): the app may never need a git client (song-format Q13).
- **App distribution decided at requirement level:** QR code with the store link is required; offline Android-to-Android sharing of the app's own package is a SHOULD and needs a build decision (universal APK or bundled splits, FRD Q11); Google Play's own "share apps" must not be relied on because Google is withdrawing it; iOS has no offline route.
- **Nothing is implemented.** `android/` is empty. No architecture document and no test plan yet; the FRD's section 19 lists them as documents still to write. The GUI design stands in for `ui-flows.md`.
- **Open questions are where the work is.** Each design document ends with a numbered open-question table. The ones that gate implementation: FRD Q1/Q4 (group size, song format choice — Q4 is answered by song-format.md and should be closed in the FRD), protocol Q2/Q3 (advertising-set counts and battery on real phones, needs a field test with a logging test build, also wanted for the statistics decision), Q10 (delegate upload in 1.0 or later); guiDesign Q13 (magnifier mode, to be prototyped), Q20 (follow-the-leader at song level only in phase 1), and the signing decision before the first Play upload (`docs/FDROID-RUNBOOK.md` Step 3).
- **Repository hygiene landed 2026-10-18:** agent-ready scaffold, README, contributing guide, code of conduct, licence files, changelog. The owner commits; nothing was committed by an agent.

## Recently Promoted

<!-- Gotchas that proved their value and were promoted from the gotcha log.
     Retire entries from this section as soon as they appear in their destination.
     Format: "if [situation], then [what to do] — promoted from gotcha-log YYYY-MM-DD" -->

- If an owner's wish for a screen or feature is about to become a design decision, then first grep the FRD for the area's requirements and read every MUST in it; when one forbids the wish, write the decision with an open question and let the owner choose between amending the FRD (withdraw or demote, version bump, dependants' headers) and changing the design — promoted from gotcha-log 2026-10-19 (three collisions in one day: FR-LEAD-30, FR-LEAD-40, FR-GEN-08).

## Key File Paths

<!-- Supplements the project file's list with paths discovered during work -->

| Path | Why it matters |
|------|----------------|
| `design/functional-requirements.md` section 1.3 | ID and priority conventions every other document relies on |
| `design/functional-requirements.md` section 20 | Requirement summary by phase; derived from the body, update it when adding or re-tiering a requirement |
| `design/ble-protocol.md` section 7 | The broadcast carousel: the part most likely to change after field tests |
| `design/ble-protocol.md` section 9.8 and 10.12 | Control commands (delegate ops 1–8, helper code op 9, leave op 10) and the delegate procedure |
| `design/ble-protocol.md` section 16 | Protocol-to-requirement traceability; add a row for every new FR-BLE/FR-GATH/FR-LEAD requirement |
| `design/song-format.md` section 8–9 | Canonical form, content hash and line list that the protocol hashes against |
| `design/song-format.md` section 12–13 | Release archive and setlists; the library repository's validator and release tool implement them |
| `design/guiDesign.md` | Numbered GUI decisions D-nn; section 2 (D-48) ranks above later decisions; open questions at the end |
| `design/gui/README.md` | Backdrop assets, rights and consent checklist, measurements, render command for the flowchart |
| `design/ble/README.md` | Sequence diagrams per scenario, render command, findings |
| `changelog.md` | `[Unreleased]` gets a line for every notable change |

## Active Decisions

<!-- One-liners about architectural choices. No docs/adr/ yet: each decision below is
     recorded in the document named. Create docs/adr/ and an index when one of these is
     re-litigated or when the first implementation decision is made. -->

- **Broadcast first, connections only upstream and for repair** — leader-to-singer data goes out as a repeated advertising carousel; GATT is for registration, requests, delegate commands and recovery of missed frames. Rationale and options in `design/ble-protocol.md` section 4 (2026-10-18).
- **Songs matched by content hash, not library revision** — two library revisions that did not touch a song still match; `design/ble-protocol.md` section 5.
- **Signed state with the gathering ID as key fingerprint** — Ed25519 key per gathering; every broadcast document is bound to the signed state; `design/ble-protocol.md` sections 5, 9.4, 13.
- **Delegates act through the leader's device, appointed with a helper code** — the leader stays the single signer and authority; no singer list; `design/functional-requirements.md` section 8.4 (FR-LEAD-40 as amended in v0.9), `design/ble-protocol.md` sections 9.8 and 10.12, `design/guiDesign.md` D-73 (2026-10-19).
- **One gathering per BLE range, enforced at start only** — FRD FR-GATH-16/17 and assumption 9.
- **Song files are a ChordPro profile** — `design/song-format.md` section 2; FRD open question Q4 should be marked answered.
- **Lyrics never live in this repository** — copyright stays with the external libraries; `license.md`, CLAUDE.md hard constraints.
- **Data safety hinges on end-to-end encryption, not on where data ends up** — Play counts any transmission off the device as collection unless it is end-to-end encrypted. Adopted 2026-10-18: FR-BLE-17 and BLE section 8.4 seal display name, requests, tokens and the delegate queue (libsodium sealed box, leader X25519 key in the signed state). The Play declaration is "no data collected, no data shared"; it stays true only if the implementation seals exactly those payloads (`docs/RUNBOOK.md` Step 4).
- **The app sends nothing to the developer; statistics are share-only** — opt-in campfire statistics stay on the leader's device and leave only through the platform share sheet; no server (2026-10-19, `design/guiDesign.md` D-60, assessment SP-16).
- **The phone is not the attraction** — singer-side features that invite fiddling are held back; the plainer way wins unless a reason is written next to the richer one (2026-10-19, `design/guiDesign.md` D-48).
- **Singers receive only what the leader pushes; no download instruction in phase 1** — FR-LEAD-30/31 withdrawn in FRD v0.9; `design/guiDesign.md` D-47, D-66; BLE `download` document reserved (2026-10-19).
- **The app is free software and goes to F-Droid too** — code licence plain Apache 2.0 since 2026-10-20 (Commons Clause removed: OSI and FSF call it non-free, F-Droid takes only free software); no Google proprietary dependency, one build for every channel. App icon, launcher layers and store graphics are CC BY-SA 4.0 since 2026-10-20, so no F-Droid anti-feature applies to them. The background photograph is the owner's own (no consent needed, children not recognisable) and CC BY-SA 4.0 like the other app graphics. The library repository stays out of F-Droid-driven decisions and keeps the Commons Clause on its scripts. Open: the signing decision before the first Play upload (owner's own key on Play plus a reproducible F-Droid build is recommended). `docs/FDROID-RUNBOOK.md`.
- **Google Play decisions fixed 2026-10-18** — application ID `nl.pe1mew.zangbundel.app`, min SDK 26 (advertising sets), target SDK 36, free, Music & Audio, target audience 13+ to stay outside the Families policy; personal developer account created after Nov 2023, so a 12-tester, 14-day closed test precedes production. `docs/RUNBOOK.md` Decisions table.
