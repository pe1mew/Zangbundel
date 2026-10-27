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
| `memory/gotcha-log.md` | Stuck or debugging | Problem-fix archive. 24 entries since 2026-10-19: PlantUML, parallel sessions, GUI wishes against FRD MUSTs, the Android build and CI, ADB and Git Bash on Windows, spec size limits, line endings and `zipfile` in test vectors, uncalibrated simulations, shell heredocs, JVM test names, Compose name shadowing and scopes. The Mechanized table lists what `scripts/check-design.sh` catches and one proposed check. |
| `memory/key-paths.md` | Looking for a file and what it holds | The project file's Key Paths table, moved there verbatim on 2026-10-25 to keep `CLAUDE.md` under about 15k characters. |
| `memory/implementation-history.md` | Wondering what exactly is built, what is not, or how the app and the protocol got to their state | Current State bullets moved there verbatim on 2026-10-27: the Android project, reception measurements, the implementation state module by module, join protection, songs ahead to the static setlist, first use, the first campfire. Dated; newer state is in the index. |
| `memory/tooling.md` | Rendering diagrams or images, or working in the Play Console | How-tos moved verbatim from the index on 2026-10-27: PlantUML rendering, the app icon and rasterising SVG (headless Edge, the browser pane), Play Console quirks. |
| `memory/project_session_2026_10_26b.md` | Wondering how the app's screens, songs ahead, the static setlist, the toolbar or FRD v0.14 to v0.18 came about | Dated record of 2026-10-26 (`db5fa4d` to `37168f3`): threads with commits, the owner's decisions, the measurements. |
| `memory/project_session_2026_10_26.md` | Wondering how the erasure code, GATT repair, protocol v0.18 to v0.25, the reception bar or `core/songformat` came about | Dated record of 2026-10-25 (continued) to 2026-10-26: threads with commits, the owner's decisions, the measurements. |
| `memory/project_session_2026_10_25.md` | Wondering how the Android project, the radio check or protocol v0.14 to v0.17 came about | Dated record of 2026-10-22 to 2026-10-25: the ask, the thread list with commits, the owner's decisions. |
| `memory/project_session_2026_10_19.md` | Wondering how the GUI design, the setlist format or the library release came about | Dated record of the 2026-10-19 sessions: the ask, the thread list, what was decided and left open. |

## Current State

- **The Android app (end of 2026-10-26):** nine modules on JDK 17, AndroidX held at the newest that compile against SDK 36 (`android/gradle/libs.versions.toml`; compileSdk 37 is the owner's call). The home screen, library, songbook, setlists, first use, settings, About, the campfire for leader and singer (start, join, push, songs ahead, leave, end, drawer, round buttons, songlist sheet, closing bar and summary, the singer's toolbar) and memories are built. Resume after a crash (D-67, BLE v0.39) is built and tested (`design/test-results/2026-10-27-resume.md`). The leader's sheet with setlist changes, *Nu zingen* and *Wis* (D-58, D-65, BLE v0.40) is built and tested (`design/test-results/2026-10-27-setlist-during-campfire.md`). Not built yet, among others: the update check on Wi-Fi (FR-LIB-13), line keeping in the songbook's reading view (D-42). Document versions (2026-10-27): FRD v0.18, BLE protocol v0.40, song format v0.12, GUI design v0.40 (D-01 to D-82), architecture v0.14, test plan v0.9, assessment v0.11 (SP-27). The full built and not-built lists, module by module, and how each part came about: `memory/implementation-history.md`.
- **Languages decided 2026-10-20** (`design/guiDesign.md` section 8, D-75 to D-80): app texts and song texts are separate layers; Dutch is primary, English the fallback (`values/` English, `values-nl/` Dutch); generated section labels follow the app language; library names are not translated; no other languages and no right-to-left for now (FRD Q8 closed); the delegate role is *assistent*, its code the *assistentcode*; a glossary of fixed terms is D-79.
- **Joining by QR code is the trust anchor against fake gatherings** (FR-GATH-19 to 23, BLE 6.5): the code carries the leader's public key. The link host is GitHub Pages from this repository, live since 2026-10-18 at `https://pe1mew.github.io/Zangbundel/j/` (Q13 resolved). App Links verification waits for the Play app-signing key (RUNBOOK Step 5c). Local check: `.claude/launch.json` config `docs-pages`.
- **Secure by design, privacy by design (users under 18) is an architecture principle since 2026-10-18** (FRD 2.6). The first assessment found two High items that were fixed at requirement level: default gathering names derived from the device name (SP-01) and Android auto-backup copying gathering data to the cloud (SP-05). SP-16 (2026-10-19) assessed opt-in campfire statistics and recorded the owner's decision: share-only through the platform share sheet, no server, so "nothing reaches the developer" stays true. Re-run the assessment when the architecture document is written and before each release.
- **BLE sequence diagrams** in `design/ble/`: seven scenarios (start, join, push, repair, leave, end, helper-code delegate); how to render them is in `memory/tooling.md`.
- **Decided 2026-10-19 at requirement level:** no library download instruction in phase 1 (FR-LEAD-30, 31 withdrawn, FR-LEAD-32 reworded; BLE `download` document reserved); delegates appointed with a four-digit helper code, no singer list (FR-LEAD-40 amended, BLE Control op 9); explicit leave (BLE Control op 10); no hand-over in phase 1 (FRD Q5 closed); join code and QR-only fixed per gathering. Singers see only the songs the leader pushed (D-47); pushed songs are deleted when the gathering ends and the setlist stays as a memory (D-49).
- **Setlists are format 1.1** (song-format section 13): `.zbs` files with `{title}` and `{meta: id}`, one `library-id/song-id` per line, `? title` for title-only entries, `setlists/` in repositories and archives, `setlists` manifest field. Unresolved entries are shown with their reason and skipped at the fire, never silently (D-74).
- **Library repository `C:\Users\drasv\github\Zangbundel-bibliotheek`** (`github.com/pe1mew/Zangbundel-bibliotheek`): eight Dutch songs, validator `scripts/validate.py`, release tool `scripts/release.py`, release 2026.10.0 published as `.zbl` with `releases.json`; four songs have an untraceable author and are marked `onbekend` / `proprietary` with a public credits-and-corrections notice. Setlist support in validator and release tool, the example setlist and `format_version` 1.1 are committed there (`6511413`). Release 2026.10.2 (`min_app_version` 0.1.0) is committed there (`35e9c07`); no tag `2026.10.2` was seen on 2026-10-26.
- **Play app entry exists (2026-10-18): app id 4974489515379733805, package `nl.pe1mew.zangbundel.app`.** All App content declarations, category, contact details and the privacy URL are saved; nl-NL listing texts are a draft. Left before the first upload: store graphics, en-US translation, the upload keystore (owner runs keytool, RUNBOOK Step 6). The dashboard counts 9 of 11 setup tasks done; the listing task completes only with graphics. Everything from the first bundle on waits for the Android project (architecture document first).
- **App icon decided 2026-10-22:** proposal 06 "Vuurboek in de kring", replacing proposal 02 of 2026-10-18. Master and launcher layers in `design/icon/`, Play PNG and the 1024 × 500 feature graphic in `docs/store-listing/graphics/`; neither is uploaded to Play yet, and screenshots need the app. Rasterising SVG: `memory/tooling.md`.
- **Library distribution unit is the `.zbl` release archive** (song-format section 12): the app may never need a git client (song-format Q13).
- **App distribution decided at requirement level:** QR code with the store link is required; offline Android-to-Android sharing of the app's own package is a SHOULD and needs a build decision (universal APK or bundled splits, FRD Q11); Google Play's own "share apps" must not be relied on because Google is withdrawing it; iOS has no offline route.
- **Test phones (ADB serials):** Samsung A52 `R58T11XBG5F` (Android 14; its extended set is not heard by other phones, D-05 runs 12–16), Samsung S23 `R5CW61V6TJJ` (Android 16, extended both ways, locks itself), Huawei Mate 20 Lite `HYFDU19311003605` (Android 10, legacy only, one advertising set), Samsung S9 `23dc74ce14047ece` (Android 10, no extended support), HTC One M7 (Android 5, too old). Radio check results in `design/test-results/2026-10-25-F-01.md`, two-phone results in `2026-10-25-D-05.md`. PC BLE counting: bleak in the venv `C:/Users/drasv/AppData/Local/Temp/zbble`.
- **Reception on the phones is the design's main risk** (protocol Q2, Q3, NFR-PERF-01, FRD Q14 open): receivers hear 13–40 % of advertising events. Erasure coding, GATT repair, songs ahead, the state lead-in and the static setlist brought the time from *Nu* to the state to about 1 s on the S23 and 2.6–3.9 s on the Huawei (mostly by repair); option B of `design/proposals/2026-10-26-state-after-push.md` (the beacon names the current song) is the way under 2 s, left for later. Field tests decide. Measurements and the path there: `memory/implementation-history.md`.
- **Phase 1 scope (2026-10-26, FRD v0.18):** requests, likes and delegates are phase 2. Stale singers settled as option B (BLE v0.38: no expiry, count labelled *Aangemeld*, swipe-away sends the leave notice, tested); songs not held ahead keep using GATT by design. The singer's toolbar (D-17) with follow the leader (D-70) and tap-to-scroll is built and tested (guiDesign v0.38, `design/test-results/2026-10-26-singer-toolbar.md`); not built: fit-to-width (D-28), the toolbar and song-info sheet in the songbook's reading view, day colours for the sheets. Resume after a crash (D-67) built and tested 2026-10-27; the owner's list of 2026-10-26 is done.
- **Joining:** from the list, with a join code, or QR-only (guiDesign v0.25 to v0.32, BLE v0.30); join code and QR live in the leader's drawer. The camera-app route (D-04) passes through the join page `docs/j/index.html`; the direct camera → app route waits for App Link verification (RUNBOOK Step 5c). Details and decoding a QR code from a screenshot: `memory/implementation-history.md`.
- **Open questions are where the work is.** Each design document ends with a numbered open-question table. The ones that gate implementation: FRD Q1/Q4 (group size, song format choice — Q4 is answered by song-format.md and should be closed in the FRD), protocol Q2/Q3 (advertising-set counts and battery on real phones; Q2 has a first two-phone sample, 16 sets and 1 set, and needs the survey of scout phones), Q10 (delegate upload in 1.0 or later); guiDesign Q13 (magnifier mode, to be prototyped), Q20 (follow-the-leader at song level only in phase 1), and, before the first Play upload, the upload keystore and the app signing key's SHA-256 (RUNBOOK Step 6). FRD section 19 still names the GUI document `ui-flows.md` (guiDesign Q5).

## Recently Promoted

<!-- Gotchas that proved their value and were promoted from the gotcha log.
     Retire entries from this section as soon as they appear in their destination.
     Format: "if [situation], then [what to do] — promoted from gotcha-log YYYY-MM-DD" -->

- If an edit script for `bash -c` mixes single quotes, backticks and backslashes in a heredoc, or is a `python -c "…"` holding backticks, then write it to a file in the scratchpad with the Write tool and run the file: heredocs failed to parse four times, and backticks in a `python -c` were substituted silently - promoted from gotcha-log 2026-10-26.
- If a design document states a size limit together with its reason ("so it fits 512 bytes"), then build the worst case in a unit test before relying on the number: five such limits in the BLE protocol were false when first computed (extended frame, state body, op 8 chunk, join link, GATT DATA chunk and HEADER), two of them after this rule was written - promoted from gotcha-log 2026-10-25.
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
| `design/test-results/` | Result files `YYYY-MM-DD-<id>.md` (test plan section 8); first: `2026-10-25-F-01.md` |
| `android/core/protocol/src/main/kotlin/nl/pe1mew/zangbundel/core/protocol/` | `cbor/`, `frame/`, `fec/`, `payload/`, `beacon/`: the wire layer; `carousel/`, `engine/`, `gatt/`: what runs it |
| `android/core/songformat/src/main/kotlin/nl/pe1mew/zangbundel/core/songformat/` | `SongParser`, `LibraryFileParser`, `LibraryImport`, `ArchiveImport` with `ZipReader`, `Setlist*` |
| `android/core/testing/src/main/resources/conformance/` | Song, archive and setlist vectors with `index.txt`; byte-exact by `.gitattributes`; generators lived in the session scratchpad |
| `design/test-results/2026-10-25-S-01-06.md` | Simulation scenarios S-01 to S-06 and what each protocol version changed |
| `android/README.md` | Build commands, module table, `keystore.properties` layout |
| `android/gradle/libs.versions.toml` | Pinned versions; comment explains the SDK 36 ceiling on AndroidX |

## Active Decisions

<!-- One-liners about architectural choices. No docs/adr/ yet: each decision below is
     recorded in the document named. Create docs/adr/ and an index when one of these is
     re-litigated or when the first implementation decision is made. -->

- **Extended frames fit one manufacturer-data structure; oversized state bodies shed title, rev, pt** - protocol v0.14 and v0.17, decided 2026-10-25 from the radio check and from implementation (`design/test-results/2026-10-25-F-01.md`, protocol Q17 to Q20).
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
- **The app is free software and goes to F-Droid too** — code licence plain Apache 2.0 since 2026-10-20 (Commons Clause removed: OSI and FSF call it non-free, F-Droid takes only free software); no Google proprietary dependency, one build for every channel. App icon, launcher layers and store graphics are CC BY-SA 4.0 since 2026-10-20, so no F-Droid anti-feature applies to them. The background photograph is the owner's own (no consent needed, children not recognisable) and CC BY-SA 4.0 like the other app graphics. The library repository stays out of F-Droid-driven decisions and keeps the Commons Clause on its scripts. Signing decided 2026-10-20: the owner's own app signing key (`zangbundel-appsigning.jks`, its own key, not shared with another app) in every channel. `docs/FDROID-RUNBOOK.md`.
- **Google Play decisions fixed 2026-10-18** — application ID `nl.pe1mew.zangbundel.app`, min SDK 26 (advertising sets), target SDK 36, free, Music & Audio, target audience 13+ to stay outside the Families policy; personal developer account created after Nov 2023, so a 12-tester, 14-day closed test precedes production. `docs/RUNBOOK.md` Decisions table.
