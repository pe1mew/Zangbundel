# Zangbundel – Test Plan

| Field | Value |
|---|---|
| Document | Test plan: automated tests, device tests, security and privacy checks, field tests, closed test, release gate |
| Project | Zangbundel – campfire songbook app |
| Document version | 0.5 (draft) |
| Date | 2026-10-26 |
| Author | Remko Welling |
| Status | Draft for review; no test has been run, because no code exists |
| Depends on | `design/functional-requirements.md` v0.14, `design/architecture.md` v0.4, `design/ble-protocol.md` v0.13, `design/song-format.md` v0.5, `design/guiDesign.md` v0.12, `design/security-privacy-assessment.md` v0.4, `docs/RUNBOOK.md` Steps 5, 7 and 9 |

| Version | Date | Change |
|---|---|---|
| 0.1 | 2026-10-22 | First draft. It covers: the test layers with their pass criteria; protocol simulation scenarios S-01 to S-10; fuzz targets and budgets; device smoke tests; security and privacy checks per release; field scenarios F-01 to F-11, with the measuring method; participants and consent, including the field-test build's consent text (assessment SP-22); the Play closed test; the release gate; how results are recorded; and a traceability table. |
| 0.2 | 2026-10-25 | Depends on FRD v0.12 (open question Q14 on NFR-PERF-01); the pass criteria of S-02 and F-02 follow Q14 once it is decided; nothing else changes. |
| 0.3 | 2026-10-25 | Fuzz target for the erasure-code decoder (section 3.4), from the approved proposal (`design/proposals/2026-10-25-erasure-coding.md`, ble-protocol v0.21); the frame-reassembly target covers coded frames. |
| 0.4 | 2026-10-25 | Depends on FRD v0.13 (FR-SING-05, the singer's reception bar); no other change. |
| 0.5 | 2026-10-26 | Depends on FRD v0.14 (Q3 resolved: the default library is `github.com/pe1mew/Zangbundel-bibliotheek`); no other change. |

---

## 1. Introduction

### 1.1 Purpose

The FRD names this document as the home of "field test scenarios (range, number of singers, battery), automated test strategy, parser fuzzing (NFR-SEC-04)". The architecture fixes the test layers (A-21, section 10). This plan says what is tested in each layer, how, with which pass criteria, and who may take part.

It is also where open questions are closed with measurements. Several questions need real phones at a real fire:
- ble-protocol Q2: advertising sets;
- ble-protocol Q3: battery;
- ble-protocol Q9: timers;
- ble-protocol Q11: the pre-start scan;
- ble-protocol Q13: scan-response load;
- architecture Q1: shared APK updates;
- architecture Q3: the singers' foreground service;
- architecture Q4: search speed;
- guiDesign Q10: fit-to-width.

### 1.2 Honesty rule

A result is a measurement with:
- a date;
- a build (`versionName`, `versionCode`, build type);
- the devices it ran on (Android major version and BLE capability flags);
- what was done.

A requirement is met when a recorded result says so. It is never met because the design says it should be (CLAUDE.md, "Honesty"). The simulation (S-scenarios) shows the logic is right, not that the radio delivers. Only field results (F-scenarios) close the performance requirements and the field questions.

### 1.3 Identifiers

Simulation scenarios are **S-nn**, field scenarios **F-nn**, device smoke tests **D-nn**, release-gate checks **G-nn**. They are never renumbered; a withdrawn one is marked *Withdrawn* and kept.

---

## 2. Test layers

| Layer | Runs | When | Blocks a release when |
|---|---|---|---|
| Unit tests (core) | JVM, `./gradlew check` | Every push (CI) | Any test fails |
| Conformance corpus | JVM, app tests and the library repository's validator | Every push; in the library repository on every push there | Any vector disagrees |
| Protocol simulation | JVM, `core/testing` | Every push (short set), before a release (full set) | An S-scenario's pass criterion fails |
| Fuzzing | JVM, Jazzer | Every push (short budget), before a release (long budget) | A crash, hang, out-of-memory or bound overrun is open |
| Lint and UI tests | Gradle lint, Compose UI tests | Every push | Lint error (translations are errors, A-16), UI test fails |
| Device smoke tests | Two phones, instrumented and by hand | Every internal-testing build | A D-test fails |
| Security and privacy checks | By hand with tools, section 6 | Before every release | Any G-check in section 6 fails |
| Field tests | Real groups, `fieldtest` build | Before the first production release, then when the protocol or radio code changes | A MUST requirement's F-criterion fails |
| Closed test on Play | 12+ testers, 14 days | Once, before production access (RUNBOOK Step 7) | Play's rule is not met |

---

## 3. Automated tests

### 3.1 Unit tests

Every core module has unit tests next to it (`core/*/src/test`). Time-dependent logic (repair timers, backoff, loss of contact, resume window, helper-code validity) runs with the fake clock from `core/testing`; no test sleeps. Areas covered at minimum:

- **Song format:** parsing, canonicalisation and hash, the line list, library-file checks, and setlist parsing and resolution. Each S-, W-, L-, A- and SL-code of song-format section 11 has at least one test that triggers it.
- **Protocol:** CBOR encode and decode, including every rejection rule of architecture A-08; frames; fragmentation and reassembly; DEFLATE with its cap; the carousel order; the burst on change; and the leader and singer state machines for every transition in ble-protocol section 3.3.
- **Storage logic:**
  - staging and the atomic swap: an interrupted import leaves the old revision usable (FR-LIB-15);
  - the startup sweep (FR-DATA-08);
  - "remove all data" (FR-DATA-07);
  - encryption and decryption of the resume record, and refusal of a tampered record (assessment SP-19).

### 3.2 Conformance corpus

`android/core/testing/src/main/resources/conformance/` holds the test vectors, one folder per kind:

| Kind | Each vector holds | Checked by |
|---|---|---|
| `songs/` | A `.cho` file, its expected canonical bytes, 16-byte hash, line list and report codes | App; library validator |
| `setlists/` | A `.zbs` file, a library state, expected resolution and codes | App; library validator |
| `archives/` | A `.zbl` or `.zip`, expected verdict and A-codes, including bomb, traversal, symlink and case-duplicate cases | App; library validator |
| `protocol/` | A document or frame as bytes, its decoded form, and for signed or sealed vectors the keys used | App (and a future iOS app) |
| `joinlinks/` | A join URL or intent link, expected payload or error | App |

Vector contents are written for the spec or generated. **Never lyrics from a real song** (CLAUDE.md hard constraint): song vectors use public-domain text or text written for the test.

The library repository (`github.com/pe1mew/Zangbundel-bibliotheek`) runs the `songs/`, `setlists/` and `archives/` vectors against `validate.py` at a pinned commit of this repository, so the two implementations cannot drift apart unnoticed (architecture A-22).

A disagreement is a bug in one of them or in the spec, and is fixed before either side releases.

### 3.3 Protocol simulation

The simulated transport of ble-protocol section 15.3 runs a leader and many singers in one JVM with configurable loss, duty cycle, connection-slot limit and latency (architecture A-07, NFR-TEST-01). Times are simulated, not wall-clock.

| ID | Scenario | Parameters | Pass |
|---|---|---|---|
| S-01 | Join and first song | 25 singers join over 30 s; a 4 kB song not held locally is pushed | Every singer shows the complete song; none shows a partial one (FR-BLE-04) |
| S-02 | Delivery time, legacy carrier | 25 singers, 3 data sets, no extended set, 10 % frame loss, 4 kB song not local | All complete within 5 s of the push in simulated time (NFR-PERF-01, FR-BLE-03) |
| S-03 | Delivery time, local copy | As S-02, song held locally by every singer | All show it within 2 s (NFR-PERF-01) |
| S-04 | Group size | 50 and 100 singers, 1 to 3 data sets, extended set on and off | 25 meets S-02 (FR-BLE-10 MUST); 50 recorded against the same 5 s aim; singers beyond the table limit get join result 2 and the "full" message |
| S-05 | Degraded mode | No data set (`BCAST` = 0), 25 singers | Everything still arrives over GATT; record the time (ble-protocol 7.8) |
| S-06 | Repair | 30 % loss on a third of the singers | Missing ranges fetched over GATT; only missing ranges requested (FR-BLE-16); connection slots never exceeded (ble-protocol 10.8) |
| S-07 | Requests and delegates | 10 singers send requests, a delegate is appointed with a helper code, accepts and pushes | Requests reach the leader within 5 s simulated (FR-REQ-03); more than 3 pending gets `0x82` (FR-REQ-06); helper code expires after 120 s and locks after 3 wrong tries |
| S-08 | Loss of contact | Singers silent for 15 s, 2 min, 6 min | Lost after 10 s, rejoin on return, ended after 5 min (FR-GATH-10, FR-GATH-11) |
| S-09 | Leader restart | Leader process killed, restarted after 3 min and after 11 min | Resume offered and works within the 10-minute window, with the same `gid`, tokens and delegates (FR-GATH-13); not offered after it; record gone after the sweep (FR-DATA-08) |
| S-10 | End and leave | Singer leaves with op 10; leader ends | Count drops at once (FR-GATH-07); after end, singers hold no lyrics and have a memory (FR-DATA-01) |

The short CI set runs S-01, S-02, S-07, S-08 and S-10 with 25 singers. The full set runs everything before a release.

### 3.4 Fuzzing

Jazzer runs one target per parser of untrusted input (NFR-SEC-04, assessment SP-09, architecture section 10):

| Target | Input | Bound checked |
|---|---|---|
| Song parser and canonicaliser | Bytes as from a library or BLE | 64 KiB, line and field limits (song-format 5.5) |
| Setlist parser | `.zbs` bytes | 64 KiB, 200 entries |
| Library file, archive manifest, `releases.json` | JSON bytes | Size cap, unknown fields ignored |
| ZIP reader | Archive bytes | Entry count, total size, ratio, path rules (song-format 12.2, 12.8) |
| DEFLATE | Raw stream | Output cap equal to the document limit |
| CBOR decoder | Payload bytes | Size given in advance, depth ≤ 8, no indefinite lengths |
| Frame reassembly | Sequences of legacy and extended frames, source and repair (ble-protocol 7.3) | Two versions per document, at most `k` frames held per group |
| Erasure-code decoder | Any set of frames with indices, `k` and `fs` | `n` ≤ 256, frame sizes checked, no exception for any subset |
| Join-link payload | URL or intent text as the camera app delivers it | base64url length, then the CBOR bound (ble-protocol 6.5) |
| Leader's GATT write handlers | Join, Request, Control and Transfer writes, sealed or plain | 512-byte values, ranges ≤ 32, rate limits |

Budgets: 60 s per target on every push in CI, 1 hour per target before a release. Seed corpora come from the conformance vectors. Every finding becomes a regression test before it is fixed. Pass: no crash, no uncaught exception, no time-bound overrun and no allocation above the target's bound in the long run.

### 3.5 Lint, UI and accessibility

- Lint with `MissingTranslation` and `ExtraTranslation` as errors (architecture A-16, guiDesign D-78); release fails on any lint error.
- Compose UI tests for:
  - the first-use path to the privacy screen (FR-GEN-06, FR-GEN-08);
  - the permission explanation before every permission request (FR-GEN-05);
  - the leader's notification-permission request, which singers never see (assessment SP-18);
  - text sizes (FR-DISP-05);
  - rotation keeping the current line (FR-DISP-04).
- By hand before each release:
  - TalkBack navigation through home, join and the lyric view (NFR-ACC-01);
  - system font scale at 200 % with the largest lyric size (NFR-ACC-02);
  - contrast of both themes and the scrim against WCAG AA (FR-DISP-10);
  - the pseudo-locales `en-XA` and `ar-XB` for truncation and start/end mistakes (guiDesign D-78).

### 3.6 Search speed

A synthetic library of 2,000 songs is generated by `core/testing` from text written for the test, never real lyrics. It is imported on the slowest phone in the device matrix (section 7.1). A title search and a lyric-fragment search, 20 queries each, must each return within 300 ms (NFR-PERF-03, architecture Q4).

---

## 4. Device smoke tests

Run by hand on two phones, one of them the oldest Android version in the matrix, for every build uploaded to internal testing (RUNBOOK Step 9). The instrumented parts live in `app/src/androidTest`.

| ID | Test | Pass |
|---|---|---|
| D-01 | Fresh install, first use | Welcome, introduction, privacy screen before any permission (FR-GEN-08) |
| D-02 | Import a library from its release URL, then from a `.zbl` file | Songs listed; report shown for bad songs (FR-LIB-07); file import works (FR-LIB-16) |
| D-03 | Start a gathering, join from the list | Pre-start scan of at least 5 s (FR-GATH-16); join within one dialog |
| D-04 | Join with the camera app from the QR code | App opens on the join screen (FR-GATH-20, FR-GATH-23) |
| D-05 | Push three songs, one not held by the singer | All arrive; the third over the air |
| D-06 | Leader screen off for 5 minutes | Beacon keeps running (FR-GATH-15); notification text is the neutral one, also on the lock screen (assessment SP-17) |
| D-07 | Leave, end | Count drops; singer holds a memory and no lyrics |
| D-08 | Share the app from the About screen | Share sheet offers Quick Share; About shows the fingerprint `57:86:7B:…:9B:10` (assessment SP-20) |

---

## 5. Field tests

### 5.1 Purpose and what is measured

Field tests answer what no simulation can: the radio, the battery, the phones the groups own and how people read at a fire. The leader runs the `fieldtest` build (architecture A-20). Its local file records only these items:
- the SP-16 data set: advertising sets opened, frames repeated and repaired, connection attempts and failures, signal-strength bands, singer counts over time, versions;
- the Android major version and the radio's capability flags;
- the battery level over time.

Delivery times are measured by video (5.3). Singers' phones record nothing beyond what the build type they run records. A singer phone runs the `fieldtest` build only when it belongs to an adult tester (5.4).

### 5.2 Scenarios

| ID | Scenario | Set-up and procedure | Measure | Pass, or what it decides |
|---|---|---|---|---|
| F-01 | Capability survey | At a group meeting, testers open the `fieldtest` build's radio check on their own phones | Android major version; multiple advertising support; number of advertising sets that open; extended advertising and its maximum data length | Answers ble-protocol Q2; sets the device matrix of 7.1 |
| F-02 | Range | Open air, one leader, singers at 2, 5, 10, 15 and 20 m; a 4 kB song not held locally is pushed 5 times at each distance | Delivery time per phone (video), repairs (leader log) | Every push complete within 5 s up to 10 m (FR-BLE-03, NFR-PERF-01); beyond 10 m recorded, not required |
| F-03 | Group size | Singers in a circle within 10 m, at 10 and 25 phones, and 50 if that many phones can be gathered; 10 pushes alternating local and not local | Delivery time per phone; slots, repairs and scan-response load on the leader | Not local within 5 s and local within 2 s on every phone at 25 (NFR-PERF-01, FR-BLE-10); the result at 50 is recorded against the aim; informs ble-protocol Q13 and FRD Q1 |
| F-04 | Leader battery | Leader on a mid-range phone at 100 %, screen mostly on, night theme, low brightness, a push every 4 minutes, at least 10 singers, for 4 hours | Battery curve (leader log) | Leader runs 4 hours (NFR-PERF-02); answers ble-protocol Q3 |
| F-05 | Singer battery and the foreground service | Two equal singer phones for 2 hours, one with the singers' foreground service, one in a test build without it; then each with the screen locked for 10 minutes | Battery used; songs missed while locked | Singers use less than the leader (NFR-PERF-02); decides architecture Q3 |
| F-06 | Loss and recovery | Singers walk out of range for 15 s, 2 min and 6 min and return; leader app force-stopped and restarted after 3 min | Who recovered without touching the phone; resume offered | At least 95 % of returns recover without user action (NFR-REL-01, FR-GATH-10); ended after 5 min (FR-GATH-11); resume works (FR-GATH-13); informs ble-protocol Q9 |
| F-07 | Joining | Join from the list, with a join code, by camera-app QR (App Link verified and the browser fallback), in QR-only mode, and late after three songs | Taps and time to the first lyric; fingerprint shown | Late joiner gets the current song at once (FR-GATH-06); QR join pins the leader (FR-GATH-19); QR-only hides the code (FR-GATH-21); fingerprint matches on leader and singers (FR-GATH-18) |
| F-08 | Requests and delegates | Singers send requests and a free-text request; the leader appoints a delegate with the helper code; the delegate accepts and pushes | Time from send to the leader's screen | Within 5 s (FR-REQ-03); free text of up to 100 characters arrives (FR-REQ-02); the delegate works (FR-LEAD-40, FR-LEAD-45) |
| F-09 | Two gatherings | A second leader tries to start within range; then moves away and starts; then the first leader moves within range of the second | Refusal time; warning while leading | Start refused during the at-least-5-s scan (FR-GATH-16); warning shown while leading (FR-GATH-17); informs ble-protocol Q11 |
| F-10 | Reading at the fire | A real campfire evening with the group's own leader and consenting adults, using the store or closed-test build; observers take notes, nobody films people | Notes on readability, text size chosen, fit-to-width use, taps, whether the phone draws attention | Answers guiDesign Q10; checks D-48 (the phone is not the attraction) and FR-SING-11 by observation |
| F-11 | Sharing the app | Share from a Play install and from the GitHub APK to a phone without the app, over Quick Share and Bluetooth; later, check whether Play updates the shared copy | Installs; fingerprint on About; update behaviour | Installs and joins without leader action (FR-DIST-05, FR-DIST-09); decides architecture Q1 |

### 5.3 How delivery time is measured

A phone or camera films the leader's screen and the singers' screens together, with a running clock in view. The push moment is the leader's tap. A singer's delivery time is the first frame where the complete song is visible on that singer's screen. At 30 frames per second this is accurate to about 0.1 s.

Only screens are filmed: no faces, no hands that identify a person, no voices. Display names are left at their generic defaults (*Zanger 7*). The video is deleted once the times are written into the result file (section 8).

### 5.4 Participants, phones and consent

- **Adult testers** run the `fieldtest` build on their own phones or on project phones. Each adult tester signs the consent of section 5.5 first. The build is never installed on a child's phone (assessment SP-22).
- **Before release**, singer phones in field tests are adult testers' phones and project-owned phones. If fewer than 25 or 50 phones can be gathered, F-03 is run at the count reached and that count is recorded. The requirement stays open until a run at 25 passes.
- **Scouts** take part only after the app is public, with the store build on their own phones, at a normal campfire led by their own leader (F-10). The group's leadership agrees beforehand, and parents are informed through the group's usual channel. Nothing is recorded on a scout's phone and nobody is filmed.
- The project records the device model only on the session sheet, with the phone owner's consent, never in the app's file (assessment SP-16).

### 5.5 Consent text for the field-test build

Shown on the first start of the `fieldtest` build and signed on paper or by e-mail before a session.

**Nederlands.** *Toestemming testversie Zangbundel.* Deze testversie van Zangbundel houdt op deze telefoon een logbestand bij van hoe de Bluetooth-verbinding werkt: hoeveel uitzendkanalen openen, hoeveel berichten opnieuw verstuurd of opgevraagd worden, hoeveel verbindingen lukken of mislukken, de signaalsterkte in grove stappen, het aantal deelnemers, de Android-versie, de Bluetooth-mogelijkheden van de telefoon en het batterijniveau. Het bestand bevat geen namen, geen naam van het kampvuur, geen liedteksten, geen sleutels of codes en geen adressen van telefoons. Het verlaat de telefoon alleen als u het zelf deelt, via het deelmenu. U kunt op elk moment stoppen: verwijder de testversie, dan is het bestand weg. Meedoen is vrijwillig. Vragen: zangbundel@rfsee.nl. *Ik ben 18 jaar of ouder, ik heb dit gelezen en ik doe mee.*

**English.** *Consent for the Zangbundel test build.* This test build of Zangbundel keeps a log file on this phone about how the Bluetooth connection works:
- how many broadcast channels open;
- how many messages are repeated or requested again;
- how many connections succeed or fail;
- signal strength in coarse steps;
- the number of participants;
- the Android version;
- the phone's Bluetooth capabilities;
- the battery level.

The file holds no names, no campfire name, no lyrics, no keys or codes and no phone addresses. It leaves the phone only when you share it yourself, through the share menu. You can stop at any time: uninstall the test build and the file is gone. Taking part is voluntary. Questions: zangbundel@rfsee.nl. *I am 18 or older, I have read this and I take part.*

---

## 6. Security and privacy checks per release

| ID | Check | How | Pass |
|---|---|---|---|
| G-01 | Permissions | Read the merged manifest of the release bundle | Exactly RUNBOOK Step 5; no camera, location only with `maxSdkVersion="30"`, no install permission (assessment S1) |
| G-02 | Backup | Merged manifest and `data_extraction_rules.xml`; `adb shell bmgr` backup attempt on a test phone | Backup and device transfer exclude everything (NFR-PRIV-02, assessment SP-05) |
| G-03 | Network during a gathering | A local packet-capture app on the leader's and a singer's phone (for example PCAPdroid from F-Droid) during D-05 | No network connection at all (FR-BLE-01, FR-NET-01) |
| G-04 | Network during import | The same capture during D-02 | Connections only to the library's host and its download servers; user agent `Zangbundel/<versionName>` (FR-NET-03, architecture A-14, privacy policy) |
| G-05 | Logs | `adb logcat` of the release build through D-01 to D-07 with a display name and a request text set | None of display name, request text, token, key or gathering name appears (NFR-SEC-03, architecture A-20) |
| G-06 | Dependencies | Diff of `./gradlew :app:dependencies` and `verification-metadata.xml` against the last release; review of every new or changed artefact | Every artefact is in architecture section 8 and FLOSS; no Google proprietary group (NFR-SEC-05, FDROID-RUNBOOK) |
| G-07 | Reproducibility | Build the tag twice on clean machines, compare with `diffoscope` | Identical apart from the signature (FDROID-RUNBOOK Step 7) |
| G-08 | Signature | `apksigner verify --print-certs` on the GitHub APK and a Play install | SHA-256 `57:86:7B:…:9B:10` on both (RUNBOOK Step 6) |
| G-09 | Fuzzing | Long budget of 3.4 done | No open finding |
| G-10 | Assessment | `design/security-privacy-assessment.md` | Re-run for anything added since the last pass (its section 7) |

---

## 7. Devices and the closed test

### 7.1 Device matrix

F-01 builds the matrix from the phones the groups actually own. Until it has run, the minimum set is:
- one phone on Android 8 or 9 (API 26–28, location permission path);
- one on Android 12 or later (Nearby devices permissions);
- one on the newest Android version available;
- one phone without extended advertising, if one can be found (the legacy carrier only).

The matrix records, per phone, the Android major version and the capability flags. The model goes only on the session sheet (5.4).

### 7.2 Closed test on Play

RUNBOOK Step 7: at least 12 opted-in testers for 14 consecutive days on the track "Scouting testers"; recruit 15 to 20 adults (leaders and parents) so a drop-out does not restart the clock.

Testers:
- run D-01 to D-08 in their own words;
- hold at least two gatherings with each other;
- report through GitHub issues or `zangbundel@rfsee.nl`.

The closed-test build is the release build, not the field-test build; it records nothing.

---

## 8. Recording results

Each session or run gets a file in `design/test-results/`, named `YYYY-MM-DD-<id>.md` (for example `2026-11-14-F-03.md`). Each file holds:
- the scenario IDs;
- the build (`versionName`, `versionCode`, build type);
- the devices (Android major version and capability flags; models only with consent);
- the participants as a count, never names;
- the conditions (open air or indoors, distance, weather);
- the measurements;
- pass or fail per criterion;
- which open questions the result answers.

Exported field-test logs are summarised into the file and then deleted. Raw logs do not go into the repository.

When a result closes an open question, the owning document marks it resolved and cites the result file. That is the same route the architecture used for its decisions.

---

## 9. Release gate

A production release (RUNBOOK Step 9) needs all of these:

1. Every automated layer of section 2 green on the release commit.
2. D-01 to D-08 passed on the internal-testing build of that commit.
3. G-01 to G-10 passed.
4. For the first release: F-02, F-03 at 25 phones, F-04 and F-06 passed, with result files. For a later release: the F-scenarios rerun whenever the protocol or `platform/ble` changed since the last pass.
5. For the first release: the 14-day closed test completed.

---

## 10. Traceability

| Requirement | Tests |
|---|---|
| FR-BLE-03, NFR-PERF-01 | S-02, S-03, F-02, F-03 |
| FR-BLE-04 | S-01, unit tests of reassembly |
| FR-BLE-10 | S-04, F-03 |
| FR-BLE-16 | S-06 |
| FR-BLE-17 | Unit tests of sealing; G-03, G-05 |
| NFR-PERF-02 | F-04, F-05 |
| NFR-PERF-03 | 3.6 |
| NFR-REL-01, FR-GATH-10, FR-GATH-11 | S-08, F-06 |
| FR-GATH-13, FR-DATA-08 | S-09, F-06, unit tests of the sweep |
| FR-GATH-06, FR-GATH-18, FR-GATH-19, FR-GATH-21, FR-GATH-23 | F-07, D-04 |
| FR-GATH-15 | D-06, F-05 |
| FR-GATH-16, FR-GATH-17 | D-03, F-09 |
| FR-GATH-07, FR-DATA-01 | S-10, D-07 |
| FR-REQ-02, FR-REQ-03, FR-REQ-06 | S-07, F-08 |
| FR-LEAD-40, FR-LEAD-45 | S-07, F-08 |
| FR-LIB-07, FR-LIB-15, FR-LIB-16 | D-02, unit tests of staging |
| FR-DIST-05, FR-DIST-09 | D-08, F-11 |
| FR-GEN-05, FR-GEN-06, FR-GEN-08 | 3.5, D-01 |
| FR-DISP-04, FR-DISP-05, FR-DISP-10, NFR-ACC-01, NFR-ACC-02 | 3.5 |
| FR-SING-11 | F-10 |
| FR-DATA-07 | Unit tests of storage logic |
| FR-NET-01, FR-NET-03, NFR-SEC-03 | G-03, G-04, G-05 |
| NFR-PRIV-02 | G-02 |
| NFR-SEC-04 | 3.4, G-09 |
| NFR-SEC-05 | G-06, G-07 |
| NFR-TEST-01 | 3.3 |
| NFR-MAINT-01 | 3.2 |

---

## 11. Open questions

| # | Question | Affects |
|---|---|---|
| Q1 | Can 50 phones be gathered for F-03 before release, or does the 50-singer aim (FR-BLE-10 SHOULD) wait for a field run after release with a group's own phones? | F-03, FRD Q1 |
| Q2 | Which group or groups host the field tests and F-10, and when (season, weather)? | Section 5 |
| Q3 | Is filming screens (5.3) acceptable to the participating groups, or is a manual stopwatch method needed as an alternative? | F-02, F-03 |
