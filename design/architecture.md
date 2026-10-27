# Zangbundel – Architecture

| Field | Value |
|---|---|
| Document | App architecture: technology choices, module boundaries, runtime structure, storage, build, testing, Android/iOS strategy |
| Project | Zangbundel – campfire songbook app |
| Document version | 0.21 (draft) |
| Date | 2026-10-27 |
| Author | Remko Welling |
| Status | Decisions taken; open questions Q1–Q5 wait for the first builds and field tests |
| Depends on | `design/functional-requirements.md` v0.19, `design/ble-protocol.md` v0.13, `design/song-format.md` v0.5, `design/guiDesign.md` v0.12, `design/security-privacy-assessment.md` v0.4, `docs/RUNBOOK.md` Decisions, `docs/FDROID-RUNBOOK.md` |

| Version | Date | Change |
|---|---|---|
| 0.1 | 2026-10-22 | First draft, before any code: decisions A-01 to A-22, module layout, runtime structure, storage model, dependency list, build and release, test layers, the security checks the assessment asks for, open questions. |
| 0.2 | 2026-10-22 | The owner decided every *Proposed* item as recommended: A-02 to A-08, A-11 to A-15 and A-17 to A-21 are now *Decided*. FRD Q7 and the package part of Q11, ble-protocol Q5, song-format Q11 and Q13 and guiDesign Q2 are marked resolved in those documents. The five inconsistencies of section 12 are fixed in the RUNBOOK and the BLE protocol. |
| 0.3 | 2026-10-22 | Resolutions of assessment v0.4 applied, as the owner asked. A-06 sets the notification text: fixed and neutral, also on the lock screen (SP-17). The notification permission is asked only from leaders (A-06, section 9; SP-18). The leader's resume record is encrypted with a Keystore key (5.4, section 6; SP-19). A-18 shares only the app's own install and shows the signing fingerprint on the About screen (SP-20). A-20 sets the conditions for the field-test build (SP-22). Section 10 adds fuzz targets for the join-link payload and the leader's GATT write handlers (SP-09). No decision superseded. |
| 0.4 | 2026-10-22 | `design/test-plan.md` v0.1 exists: sections 1.2 and 10 point at its scenarios instead of "still to write". No decision changed. |
| 0.5 | 2026-10-25 | Depends on FRD v0.12 (open question Q14 on NFR-PERF-01); nothing in this document changes. |
| 0.6 | 2026-10-25 | Depends on FRD v0.13 (FR-SING-05, the singer's reception bar); no other change. |
| 0.7 | 2026-10-26 | Section 6, found while building the install pipeline: an installed revision's directory is `<revision>-<8 random hex>`, new for every install, so reinstalling the revision that is in use never overwrites its files; the file names inside are the app's own (`library.json`, `license.txt`, `manifest.json`, `songs/<song id>.cho`, `setlists/<setlist id>.zbs`). |
| 0.8 | 2026-10-26 | Depends on FRD v0.14 (Q3 resolved: the default library is `github.com/pe1mew/Zangbundel-bibliotheek`). The app holds the default library's URL as a constant (`app/library/DefaultLibrary`); no decision changed. |
| 0.9 | 2026-10-26 | Depends on FRD v0.15 (FR-NET-01 and FR-NET-03 widened to setlist addresses); no other change. |
| 0.10 | 2026-10-26 | Depends on FRD v0.16 (FR-BLE-18: the setlist's songs are broadcast ahead; FR-SING-05 amended); no other change. |
| 0.11 | 2026-10-26 | Depends on FRD v0.17 (FR-SING-05: the reception view is a hidden feature, opened by a long press only); no other change. |
| 0.12 | 2026-10-26 | Depends on FRD v0.18 (requests, likes and the delegate role moved to phase 2); no other change. |
| 0.13 | 2026-10-27 | Section 5.4, with BLE protocol v0.39 and guiDesign D-67 built: the resume record also holds `ts`, the setlist as started, the positions pushed and the current position, and is rewritten on every push and every 30 s; the window counts from its last write; ending deletes it at once rather than after the 60 s end phase, so a leader that dies while ending is not offered an ended gathering. The record's code is `ResumeRecord` in `core/protocol` and `ResumeStore` in `platform/storage`. Assessment SP-27. No decision superseded. |
| 0.14 | 2026-10-27 | Section 5.4, with BLE protocol v0.40 (guiDesign D-58): the resume record holds the setlist as it stands, which may change during the gathering, and is rewritten on every setlist change too. No decision superseded. |
| 0.15 | 2026-10-27 | A-14 built: the Wi-Fi update check as `LibraryUpdateWorker` (once a day on an unmetered network, the first run as soon as it is switched on); section 9's manifest list gains `WAKE_LOCK` and `RECEIVE_BOOT_COMPLETED`, which WorkManager brings (assessment SP-29). No decision superseded. |
| 0.16 | 2026-10-27 | Depends on FRD v0.19 (FR-DATA-09). The statistics row of the data lifecycle table names its table and requirement; the statistics export is dropped from kotlinx-serialization-json's uses, since the built export writes its fixed JSON by hand (`CampfireRecord`). |
| 0.17 | 2026-10-27 | A-18 built (FR-DIST-05, guiDesign D-84): the copy, its name, and why it outlives the share sheet. |
| 0.18 | 2026-10-27 | A-20 built: the field-test log, its consent screen, its export, and the `fieldtest` build's debug-key fallback without a keystore. |
| 0.19 | 2026-10-27 | Section 8: the transitive artefacts named, and JUnit 5 under Jazzer with its licence (release checks G-06 and G-09). The fuzz targets of section 10 are built as a `fuzz` source set in `core/songformat` and `core/protocol` (test plan 3.4 as of v0.12). |
| 0.20 | 2026-10-27 | A-06: the leader's one notification question built. |
| 0.21 | 2026-10-27 | A-19: R8 9.1.57 pinned for Kotlin 2.4; the fieldtest build's signature follows `keystore.properties`. |

---

## 1. Introduction

### 1.1 Purpose

The FRD says what the app must do; the protocol, the song format and the GUI design say what goes over the air, what a library looks like and what the user sees. This document says **how the app is built**: the language and toolkit, how the code is split into modules, which component runs where at run time, where data lives and when it is deleted, which libraries the app may depend on, how it is built and released, and how it is tested. It answers what the other documents leave to "the architecture document": the minimum Android version and tablets (FRD Q7), the shareable package (FRD constraint 12, Q11), module boundaries and the Android/iOS strategy (FRD section 19, NFR-PORT-01), the on-device storage model and the fetch code (song-format sections 1.2 and 12.7), git or archives only (song-format Q13), the validator's home (song-format Q11) and the toolkit (guiDesign section 1.2).

### 1.2 Scope

In scope: the Android app of phase 1 (P1), with room left for karaoke (P2) and instruments (P3), and the structure that keeps an iOS app possible later. Out of scope: the wire format (`design/ble-protocol.md`), the file formats (`design/song-format.md`), screens and texts (`design/guiDesign.md`), Play and F-Droid operations (`docs/RUNBOOK.md`, `docs/FDROID-RUNBOOK.md`), and field-test scenarios (`design/test-plan.md`).

### 1.3 Decision status

Every decision is numbered **A-nn** and carries a status. **Fixed** means it was already decided elsewhere and is repeated here so the build follows it; the source is named. **Proposed** means this document recommends it and the owner decides; until then it is the working assumption, and changing it later costs what the *Why* line says. **Decided** means the owner accepted a proposal, on the date given. A decision is never renumbered; a replaced one is marked *Superseded* and kept.

---

## 2. Constraints that shape everything

| Constraint | Source | Consequence for the architecture |
|---|---|---|
| Users are under 18; secure and private by design | FRD 2.6, assessment S1–S7, P1–P8 | No server, no account, no component that talks to the internet on its own, everything received is untrusted and bounded, personal data lives only for a gathering. |
| Free software in every channel | CLAUDE.md, FDROID-RUNBOOK Step 2 | Every dependency FLOSS and from Maven Central or Google's Maven repository; no Google Play Services, Firebase, ML Kit, Play Core or Play Integrity; one build for Play and F-Droid. |
| Everything in a gathering works over BLE | FR-BLE-01, FR-BLE-02 | The gathering engine has no network path; the network module is used only for libraries (FR-NET-01). |
| Minimum SDK 26, target SDK 36, application ID `nl.pe1mew.zangbundel.app` | RUNBOOK Decisions | `startAdvertisingSet` (API 26) is available everywhere; the permission model differs below and above API 31. |
| Business logic separate from Android UI and BLE | NFR-PORT-01, NFR-TEST-01 | Library, parsing, gathering state and protocol live in modules without Android dependencies and run against a simulated transport. |
| Hostile input everywhere | NFR-SEC-04 | Every parser has explicit size, count, depth and time bounds and is fuzz-tested; a parse failure is a failed reception, never a crash. |
| Reproducible, pinned build | NFR-SEC-05, FDROID-RUNBOOK Step 7 | Pinned toolchain and dependencies with verified checksums; no timestamps or machine paths in the output. |
| Four hours of leading on one battery | NFR-PERF-02 | The radio duty cycle is set by the protocol; the app adds nothing that wakes the phone: no polling, no background work during a gathering besides the engine. |

---

## 3. Decisions

### 3.1 Platform and toolkit

**A-01 — Android first, API 26 to 36.** *Fixed (RUNBOOK Decisions).* Phase 1 is Android phones and tablets, minimum SDK 26 (Android 8.0), target SDK 36, application ID `nl.pe1mew.zangbundel.app`. A device without multiple advertising sets or without peripheral mode can join but not lead (FRD constraint 1, guiDesign D-24). This answers the first half of FRD Q7.

**A-02 — Tablets use the phone layouts in phase 1.** *Decided 2026-10-22 (owner).* Compose layouts are written for width classes, so a tablet gets the phone screens with more room (larger margins, a wider lyric column, the songbook list and a song side by side where guiDesign already allows two panes). No tablet-only screens, no 2560 × 1600 backdrop export (guiDesign Q2), no lead-sheet layout before P3 (FR-INST-07). *Why:* the users are phones around a fire; tablet layouts double the screens to test. Revisit if field tests show leaders on tablets. Answers the second half of FRD Q7 and guiDesign Q2.

**A-03 — Kotlin, Jetpack Compose with Material 3, one activity.** *Decided 2026-10-22 (owner).* The app is written in Kotlin. The UI is Jetpack Compose with Material 3, themed with the palette of guiDesign section 9, in a single activity with Compose navigation. The system splash comes from `androidx.core:core-splashscreen` (guiDesign D-05), edge-to-edge from `enableEdgeToEdge()` (guiDesign section 7). *Why:* Compose is the platform's current toolkit, is Apache 2.0 from Google's Maven repository, handles the theme switch, text sizes, rotation (FR-DISP-04, guiDesign D-42) and font scaling (NFR-ACC-02) with little code, and its state model matches the engine's flows (A-06).

**A-04 — Pure-Kotlin core modules, Android only at the edges.** *Decided 2026-10-22 (owner).* The code is split into Gradle modules (section 4). Song format, protocol, gathering logic and their models are Kotlin/JVM modules with no Android dependency; Android BLE, storage, networking and UI are separate modules that implement the core's interfaces. *Why:* NFR-PORT-01 and NFR-TEST-01 ask for exactly this; it also makes the core testable at JVM speed and fuzzable (NFR-SEC-04).

**A-05 — iOS later, decided at the start of the iOS phase.** *Decided 2026-10-22 (owner).* Nothing is built for iOS in phase 1. The core modules are written so they can become Kotlin Multiplatform modules later: they use the JVM only through a short, named list of platform calls (Unicode NFC normalisation, SHA-256, raw DEFLATE, ZIP reading, the secure random source, the crypto interface of A-09), each behind a small interface or confined to one file. At the start of the iOS phase the owner chooses between a shared Kotlin Multiplatform core with a SwiftUI front end and a native Swift implementation; either way the conformance corpus (A-21) proves both apps agree byte for byte. The protocol and file formats are already platform-neutral (FR-BLE-08, guiDesign D-75: nothing language-dependent goes over BLE). Whether iOS shares the Android string source is decided then (guiDesign D-76). *Why:* committing to Multiplatform now adds build complexity to an Android-only phase; keeping the core free of Android makes the later choice cheap.

### 3.2 Runtime structure

**A-06 — One gathering engine, owned by a foreground service, run as a single-threaded actor.** *Decided 2026-10-22 (owner).* A `GatheringService` (foreground service, type `connectedDevice`) is started when the user starts or joins a gathering and stopped when it ends, for leader and singer alike. It owns the one `GatheringEngine` of the process: the leader or singer state machine from `core/protocol`, the transport, the timers and the ephemeral gathering store (A-12). All engine state is changed on one coroutine dispatcher confined to a single thread; BLE callbacks, timer expiries and UI commands are posted to it as events, so the protocol code never needs a lock. The engine publishes its state as `StateFlow`s; screens observe them through ViewModels and send commands back. The service's notification is silent and low-importance, opens the app and has no actions; it is the only notification the app shows (guiDesign D-68 rules out others). Its text is fixed and neutral in the app language, *Kampvuur actief* / *Campfire running*. It never shows the gathering name, a song title, a count or a display name, and its lock-screen (public) version is the same text (assessment SP-17). The app asks for `POST_NOTIFICATIONS` (Android 13+) only from a leader, once, at the first start of a gathering, after the plain-language explanation of FR-GEN-05. It never asks a singer. *(Built 2026-10-27, found missing by release check G-10:)* on Android 13 and later, while the permission is not granted, the first *Starten* (guiDesign D-25) shows the explanation, records that the question was asked (`AppSettings.notificationAsked`) and opens Android's question; the gathering starts after either answer, and the question never comes again. Without the permission the service runs unchanged: Android only hides the notification from the shade (assessment SP-18). *Why:* FR-GATH-15 needs the leader's beacon to survive the screen turning off, and Android allows that only from a foreground service. For singers the lyric view keeps the screen on (FR-DISP-07), but a singer who locks the phone should not lose the gathering; the same service covers both roles with one code path. Whether singers need it is field-test question Q3 of this document.

**A-07 — The transport is the protocol's four primitives.** *Decided 2026-10-22 (owner).* `core/protocol` talks to the radio only through a `Transport` interface with the primitives of ble-protocol section 15.3: advertise (set, bytes), scan results (bytes, RSSI), connect and disconnect, and read, write and notify (UUID, bytes), plus the capability queries the leader needs (number of advertising sets, extended advertising, maximum advertising data length). `platform/ble` implements it on Android; `core/testing` implements it as a simulated radio with loss, duty cycle, slot limits and latency for 50 virtual singers. The Android rules of the protocol (one scan started per gathering and never restarted, notifications serialised on `onNotificationSent`, status 133 expected, MTU requested first) live in `platform/ble` and nowhere else. *Why:* NFR-TEST-01, and it keeps Android's BLE quirks out of the protocol logic.

**A-08 — CBOR stays, encoded by a small bounded codec of our own.** *Decided 2026-10-22 (owner).* The protocol keeps CBOR (answering ble-protocol Q5: no switch to Protocol Buffers; the payload tables are written and the encoding is compact enough). `core/protocol` contains its own codec for the subset the protocol uses: unsigned and negative integers, byte and text strings, arrays, maps, booleans and null, definite lengths only, preferred serialization on encode. The decoder rejects indefinite lengths, tags, floats, duplicate map keys and nesting deeper than 8, and is given the payload's size limit before it starts (ble-protocol section 9.1 limits). Signatures are verified over the received body bytes, so no re-encoding is needed for verification. *Why:* the protocol names Jackson or cbor-java as options; a few hundred lines of our own give exact control over bounds (NFR-SEC-04), one dependency fewer (NFR-SEC-05), a codec the fuzzer can cover completely, and no JVM-only library in the core (A-05).

**A-09 — Cryptography through libsodium, behind an interface.** *Fixed (ble-protocol section 8.4 and Q15, FDROID-RUNBOOK Step 2).* Ed25519 signatures, X25519 key pairs and `crypto_box_seal` come from libsodium through `lazysodium-android` on the phone and `lazysodium-java` in JVM tests. The core sees only a `Crypto` interface (sign, verify, key pair generation, seal, open, SHA-256, random bytes from the platform's CSPRNG). *Why:* the protocol requires byte-identical libsodium behaviour, and libsodium is what an iOS app would use too. The F-Droid scanner check on the native library is open question Q2 of this document.

**A-10 — Own song-format parser in the core.** *Fixed (song-format section 2.2).* `core/songformat` holds the ChordPro-profile parser, the canonicaliser and content hash (section 8), the canonical line list (9.1), the library file (4), the validation report with its codes (11), the setlist parser and resolver (13) and the archive reader (12). It does not depend on any ChordPro library. JSON files are read with kotlinx-serialization into plain data classes with unknown fields ignored (section 10.1).

### 3.3 Libraries, storage and network

**A-11 — Libraries arrive as release archives or snapshots; no git client.** *Decided 2026-10-22 (owner).* The app imports a library from a release archive (`.zbl`) found through the library's `releases.json`, from a file the user picks (`.zbl` or a snapshot `.zip`, FR-LIB-16), or from a URL to either. It does not clone git repositories. FR-LIB-17 is met by the archive manifest's `revision` and `source`; FR-LIB-10 and FR-LIB-11 allow an "equivalent download of a chosen branch or tag" (song-format section 12.1). *Why:* a git client (JGit) is large, brings its own parsers of hostile input, and gives nothing an archive does not for a user who wants the latest release; maintainers who want instant updates cut a release, which the library repository's workflow already does. Answers song-format Q13.

**A-12 — Storage: one database, canonical files, and a separate ephemeral gathering store.** *Decided 2026-10-22 (owner).* Section 6 has the full model. In short: a Room (SQLite) database holds the library index, song metadata and an FTS4 search table, setlists, gathering memories, per-song usage (guiDesign D-52) and the opt-in statistics (D-60). Canonical song files of an installed library revision sit in app-private files; an import or update is written to a staging directory and swapped in one database transaction (FR-LIB-15). Everything that belongs to a running gathering (received documents and songs, the singer table, tokens, keys, requests, resume data) lives only in memory and in `noBackupFilesDir/gathering/`, and is deleted at the end of the gathering and, as a backstop, at every app start once the resume window has passed (FR-DATA-01, FR-DATA-06, FR-DATA-08). Settings are a DataStore preferences file.

**A-13 — No backup and no device transfer of app data.** *Decided 2026-10-22 (owner).* `android:allowBackup="false"`, `android:fullBackupContent="false"` and data-extraction rules that exclude every domain from cloud backup and device-to-device transfer. *Why:* NFR-PRIV-02 allows either exclusion or no backup; excluding selectively is easy to get wrong when a file is added later. The cost is that a new phone re-imports its libraries, which takes a minute and needs no account. Closes assessment SP-05 at the configuration level.

**A-14 — Network: the platform's HTTPS client, user-initiated, with a fixed user agent.** *Decided 2026-10-22 (owner).* `platform/net` downloads release indexes and archives with `HttpsURLConnection`: HTTPS only, certificate validation by the platform, no redirect to `http`, a conditional GET for the index (song-format 12.7), the size shown before the download (FR-NET-04), a hard size cap while streaming, SHA-256 verified before the archive is opened. The user agent is the fixed string `Zangbundel/<versionName>`, because the platform's default names the phone model. Downloads start only from a user action (FR-LIB-12, FR-NET-02); the opt-in automatic check on Wi-Fi (FR-LIB-13) is the one scheduled job, run by WorkManager with an unmetered-network constraint, and it only checks and notifies inside the app, never installs. *Why:* one download type does not justify OkHttp; fewer components that can open a socket make NFR-SEC-03 easy to review.

**A-15 — QR codes are generated, never scanned, in 1.0.** *Decided 2026-10-22 (owner).* The join QR code and the store QR code are generated with ZXing core (QR encoder only). Singers scan with the phone's camera app, which opens the join link (FR-GATH-23, RUNBOOK Step 5); the app has no camera permission. Pasting a library URL replaces scanning it (guiDesign D-50). An in-app scanner, if ever added, uses ZXing and asks for the camera permission only when tapped.

### 3.4 Language, sharing and dependencies

**A-16 — Android string resources, English default, Dutch in `values-nl`.** *Fixed (guiDesign D-76 to D-78).* Implemented with `values/strings.xml` (English, the fallback) and `values-nl/strings.xml` (Dutch, primary), `locales_config.xml`, and AppCompat's per-app locale API so the in-app language setting works from API 26 up (FR-LANG-03). Lint's `MissingTranslation` and `ExtraTranslation` are errors in every build, and the pseudo-locales are enabled in debug builds (D-78). Generated song labels come from the same resources (song-format section 7, guiDesign D-77). Sorting uses `java.text.Collator` for the app locale with the leading-article rule of FR-LANG-07.

**A-17 — Few dependencies, all listed, manual dependency injection.** *Decided 2026-10-22 (owner).* Section 8 lists every allowed dependency with its purpose. Objects are wired by hand in one `AppContainer` created by the `Application` class; there is no Hilt or Dagger. Adding a dependency means adding a row to section 8 in the same change, with its licence and whether it can open a network connection (NFR-SEC-03). *Why:* every dependency is reviewed per release (NFR-SEC-05) and must pass the F-Droid rules; a short list keeps that review real.

**A-18 — Every install is one APK, so the app can share itself.** *Decided 2026-10-22 (owner).* The App Bundle disables splitting by language, density and ABI (`bundle { language { enableSplit = false } … }`) and has no dynamic feature modules. Play then installs a single base APK signed with the owner's app signing key, the same key as the GitHub and F-Droid APKs (RUNBOOK Decisions). *Share the app* (FR-DIST-05) copies the installed APK (`applicationInfo.sourceDir`) to the cache and hands it to the share sheet through a `FileProvider` with type `application/vnd.android.package-archive`, which offers Quick Share and Bluetooth; it is offered only when `splitSourceDirs` is empty, and otherwise falls back to the store link (FR-DIST-04). It never goes over the gathering's BLE (FR-DIST-08) and needs no install permission (RUNBOOK Step 5). The app shares only its own installed file, never an APK it received. The About screen shows the short fingerprint of the app signing certificate (`57:86:7B:…:9B:10`), so an adult can compare a shared copy with the one in the README (assessment SP-20). *Why:* FRD constraint 12: a set of split APKs cannot be installed by a receiver without an installer app. The price is a larger download, mostly the native crypto library for four ABIs; the app ships no other native code and few images. Answers FRD Q11's first part; the store-update part is Q1 of this document. *(As built, v0.17:)* `AppPackage` in the app module; *Delen zonder internet* on the install panel (guiDesign D-84). The copy is named `zangbundel-<versionName>.apk`, replaces any earlier `.apk` in the share directory and is not deleted when the share sheet closes, because Quick Share or Bluetooth may read it later; "remove all data" clears the cache with it. Measured on the A52: the copy is byte-identical to the installed `base.apk`, and it installs on a second phone. Q1 stays open until the first Play install.

### 3.5 Build, logging and testing

**A-19 — One pinned, reproducible build; three build types.** *Decided 2026-10-22 (owner), following FDROID-RUNBOOK Step 7.* Section 9 has the details: Gradle wrapper, Android Gradle Plugin, Kotlin, JDK major, build tools and every dependency pinned in a version catalog, with checksums in `gradle/verification-metadata.xml`; no product flavours (one build for Play and F-Droid); build types `debug`, `release` and `fieldtest`; no timestamps, machine paths or VCS information in the output; dependency metadata blocks disabled; `versionCode` and `versionName` literal in `android/app/build.gradle.kts`. *(Amended 2026-10-27, v0.21:)* R8 9.1.57 is pinned on the root build script's classpath (`libs.r8`) over the R8 8.13 that AGP 8.13.2 bundles: Kotlin 2.4 needs R8 9.1.29 or newer, and the older one could not read its metadata (`design/test-results/2026-10-27-r8-9-1.md`). Drop the pin when AGP's own R8 is new enough. With `keystore.properties` present, `fieldtest` is signed with the upload key, so it cannot update a copy signed with the debug key.

**A-20 — Logging never carries personal data; field logs only in the field-test build.** *Decided 2026-10-22 (owner).* All logging goes through one small wrapper. In `release`, verbose, debug and info calls are removed by R8, and warnings and errors may carry codes and counts but never a display name, request text, token, key, gathering name or song text (NFR-SEC-03). The `fieldtest` build type additionally writes radio figures (sets opened, frames repeated and repaired, connection attempts and failures, RSSI bands, battery level over time) to a local file the tester exports by hand through the share sheet, the logging test build of assessment SP-16 route 2. It has its own application ID suffix `.fieldtest`, so it never replaces or reads the store app's data. It is never uploaded to Play or F-Droid. Its file holds only the SP-16 data set plus the battery level, never a name, a gathering name, a token, a key, a song text or a device address. It is installed only on phones of consenting adult testers named in the test plan, never on a child's phone, and the test plan carries the consent text (assessment SP-22). No crash reporter exists in any build. *(As built, v0.18:)* `FieldLog` and `FieldConsentScreen` in `src/radiocheck`, with no-op stand-ins in `src/release`; the code is compiled into `debug` too but writes only in the `.fieldtest` app, and only after the tester tapped *Ik doe mee* on the consent screen of test plan 5.5, which comes before anything else (*Niet meedoen* closes the app). The file is `noBackupFilesDir/fieldtest/log.jsonl`, one JSON object per line, at most 4 MiB: per session a `start` line (role, date, Android major version and SDK level, the radio's capability flags, app and protocol versions; for a leader also whether it was resumed, the sets opened and the connection limit), a `sample` line every minute and an `end` line, each with the time from the session's start, the battery level and whether the phone charges; a leader's lines add the singer count, frames sent, repairs served and their bytes, the peak of connections and those refused; a singer's add its repairs and failed repairs, its carrier and its state. Signal-strength bands and connection attempts are not measured yet. *Log delen* on the radio check shows the file and then hands it to the share sheet; *Log wissen* deletes it. Without `keystore.properties` the `fieldtest` build is signed with the debug key, so a developer can install it; with it, with the owner's key.

**A-21 — Five test layers, with a conformance corpus shared across implementations.** *Decided 2026-10-22 (owner).* Section 10: JVM unit tests of the core; a conformance corpus of song, setlist, archive and protocol test vectors with expected canonical bytes, hashes, line lists and error codes, which the app's tests and the library repository's validator both run; protocol simulation over the simulated transport with 50 virtual singers; fuzzing of every parser of untrusted input; and instrumented and field tests on real phones.

**A-22 — The command-line validator and release tool live in the library repository.** *Fixed (existing).* `validate.py` and `release.py` in `github.com/pe1mew/Zangbundel-bibliotheek` implement song-format sections 11 to 13 for maintainers and CI. The app has its own Kotlin implementation of the same rules in `core/songformat`; the two are kept in step by the conformance corpus (A-21), not by sharing code (the library's scripts carry a different licence). Answers song-format Q11.

---

## 4. Module layout

```
android/
├── settings.gradle.kts, build.gradle.kts, gradle.properties
├── gradle/libs.versions.toml            ← every version, pinned (A-19)
├── gradle/verification-metadata.xml     ← SHA-256 of every artefact (NFR-SEC-05)
├── core/
│   ├── model/        Kotlin/JVM  ids, song references, setlist entries, gathering and role states
│   ├── songformat/   Kotlin/JVM  parser, canonical form, hash, line list, library file, report codes,
│   │                             setlists, archive and snapshot reader with bounds (song-format 4–13)
│   ├── protocol/     Kotlin/JVM  CBOR codec, documents 1–8, frames, fragmentation and reassembly,
│   │                             DEFLATE bound, carousel scheduler, leader and singer state machines,
│   │                             sealed-payload framing, Transport and Crypto interfaces (ble-protocol)
│   └── testing/      Kotlin/JVM  simulated transport, fake clock, conformance corpus runner (test only)
├── platform/
│   ├── ble/          Android     Transport over BluetoothLeAdvertiser, scanner, GATT server and client
│   ├── crypto/       Android     Crypto over lazysodium-android
│   ├── storage/      Android     Room database and DAOs, canonical file store, staging and swap,
│   │                             ephemeral gathering store, DataStore settings, startup sweep
│   └── net/          Android     HTTPS fetch, release index, conditional GET, download caps, WorkManager job
└── app/              Android     Application and AppContainer, MainActivity, Compose screens and navigation,
                                  ViewModels, GatheringService, QR rendering, share sheet, resources and locales
```

Dependency rules, enforced by the module graph:

| Module | May depend on | Must not depend on |
|---|---|---|
| `core/model` | Kotlin stdlib | anything else |
| `core/songformat` | `core/model`, kotlinx-serialization-json | Android, network, storage |
| `core/protocol` | `core/model`, `core/songformat` (hash, line list), kotlinx-coroutines | Android, network, storage, any crypto library (only the `Crypto` interface) |
| `platform/*` | the core modules, AndroidX, their one library each | `app`, each other (except `storage` used by `net` for staging) |
| `app` | everything | — |

`core/testing` is a test fixture for the other core modules and for `app`'s instrumented tests; it never ships.

---

## 5. Runtime structure

### 5.1 Components

```
 ┌────────────────────────── app process ──────────────────────────┐
 │  MainActivity (Compose)                                          │
 │    screens ── ViewModels ──┐                                     │
 │                            │ StateFlow / commands                │
 │  GatheringService (FGS) ── GatheringEngine (single-thread actor) │
 │                            │   leader or singer state machine    │
 │                            │   carousel, timers, ephemeral store │
 │                            ├── Transport ── platform/ble ── radio│
 │                            └── Crypto ───── platform/crypto      │
 │  LibraryRepository ─── platform/storage (Room, files)            │
 │        └────────────── platform/net (HTTPS, user-initiated)      │
 └──────────────────────────────────────────────────────────────────┘
```

The engine and the library side meet in one place: when a song is pushed or followed, the engine asks the library repository for a local song with the referenced hash (ble-protocol 10.3, FR-BLE-12) and, on the leader, for the canonical bytes to broadcast. The network module is never called by the engine (FR-NET-01).

### 5.2 Leader at run time

Starting a gathering (ble-protocol 10.1): the service starts, the engine runs the pre-start scan for at least 5 s (FR-GATH-16), generates the gathering keys, persists the resume record (5.4), then opens the GATT server, the beacon set, up to three legacy data sets and the extended set, and starts the carousel. The carousel scheduler in `core/protocol` decides, every advertising interval, which frame each set carries next; `platform/ble` only replaces the set's data. The low-duty scan of FR-GATH-17 (2 s every 30 s) runs from the same engine. Push, next song, requests and delegate commands are engine events; each state change increments `seq`, re-signs the state and triggers the burst of ble-protocol 7.4.

### 5.3 Singer at run time

Joining (ble-protocol 10.2, 6.5): from the home screen's scan or from the join link (App Link or `zangbundel:` scheme, opened by the camera app), the service starts, the engine pins the leader's key, registers over a short GATT connection with the sealed Join, and then listens to the carousel with one continuous scan. Reassembly keeps a bitmap per document version and never hands an incomplete document to the screen (FR-BLE-04). Repairs, requests and delegate commands open short connections with the backoff of ble-protocol 10.4.

### 5.4 Resume, ending and the startup sweep

The leader persists a resume record in `noBackupFilesDir/gathering/resume.cbor` when the gathering starts, on every push and setlist change (v0.14), whenever the singer table changes, and at least every 30 s: the Ed25519 and X25519 secret keys, `gid`, name, join code, `seq`, `ts`, the singer table (`sid`, `tok`, `xk`) and the delegate list, and since v0.13 the setlist as it stands (per position `library-id/song-id` and title; since v0.14 it may change during the gathering), the positions pushed in order, the current position, the start time and the time of the write (BLE protocol 10.1 step 2, v0.39). The record is `ResumeRecord` in `core/protocol`, written and read by `ResumeStore` in `platform/storage`. It is written atomically (temporary file, then rename) and is never part of a backup (A-13). It is encrypted with AES-GCM under a key held in the Android Keystore. The key is non-exportable, created once per installation and needs no user authentication, so a copy of the file alone is useless (assessment SP-19). Uses the platform API, no new dependency. After a crash, a start within the resume window (10 minutes from the record's last write, ble-protocol 10.9) offers *Hervatten* (guiDesign D-67). Ending deletes the record at once (v0.13; before, after the 60 s end phase of ble-protocol 10.10, which would have offered to resume a gathering that was ending). At every app start, before anything else, the startup sweep deletes any resume record older than the resume window and every singer-side gathering file, so nothing outlives a crash for longer than the next start (FR-DATA-08). A singer persists nothing across its own restart except what the gathering memory keeps (FR-DATA-03); it rejoins as a new registration if the app was killed.

---

## 6. Data model and lifecycle

| Data | Where | Lives until | Backup |
|---|---|---|---|
| Installed library revisions: canonical `.cho` files, setlists, licence file, archive manifest, under names the app chooses | `filesDir/libraries/<library id>/<revision>-<random>/` (a new directory per install, v0.7) | replaced by an update or removed by the user | never (A-13) |
| Library index, song metadata, line-list text for search (FTS4, unicode61 tokenizer with diacritics removed), validation report | Room `zangbundel.db` | as above; the report until the next import (song-format 11) | never |
| Import or update in progress | `filesDir/libraries/<library id>/.staging-<random>/` | swapped in one transaction, or deleted at the next start | never |
| Prepared setlists (own and duplicated) | Room | removed by the user (FR-DATA-06, guiDesign D-55) | never |
| Gathering memories, per-song usage | Room | removed by the user (FR-DATA-04, guiDesign D-49, D-52) | never |
| Opt-in statistics records | Room, table `campfire_statistics` | *Wissen* or "remove all data" (FR-DATA-09; guiDesign D-60, D-62) | never |
| Settings: display name, theme, text size, language, keep-screen-on, vibration | DataStore `settings.preferences_pb` | "remove all data" (FR-DATA-07) | never |
| Per-device radio figures (`max_conn`, advertising sets that worked) | DataStore | "remove all data" | never |
| Running gathering: received documents and songs, setlist copy, requests, singer table, tokens, keys | memory, plus `noBackupFilesDir/gathering/` | end of gathering, leave, timeout, or the startup sweep (FR-DATA-01, FR-DATA-06, FR-DATA-08) | never |
| Leader resume record (AES-GCM, Keystore key) | `noBackupFilesDir/gathering/resume.cbor` | end of gathering or the resume window (5.4) | never |
| APK copy for sharing, exports for the share sheet | `cacheDir/share/` | deleted when the share sheet returns and at every start | never |

"Remove all data" (FR-DATA-07) deletes the database, the library files, the settings, the gathering directory and the cache, and returns the app to its first-use state.

The import pipeline follows song-format 12.7 step by step, inside `core/songformat` for the checks and `platform/storage` for the files: bound the archive from its central directory before inflating, read the manifest or fall back to snapshot handling, refuse on library errors before unpacking, verify every song's canonical form and hash against the manifest, validate, write to staging, swap in one transaction, record revision, version, source and archive SHA-256. Entries are never extracted to their own paths; files are written under names the app chooses.

---

## 7. Protocol implementation map

| Protocol part | Module | Notes |
|---|---|---|
| Beacon encodings A and B, flags (6.1) | `core/protocol` (bytes), `platform/ble` (AD structures) | Singers accept both encodings. |
| Legacy and extended frames, fragmentation, reassembly (6.2, 7.2) | `core/protocol` | Bitmap per (`doc`, `dv`), at most two versions per document. |
| Carousel scheduler, bursts, pass time `pt` (7.4) | `core/protocol` | Pure function of the documents and the set count; tested in simulation. |
| Advertising sets: opening, counting `n`, replacing data (7.1) | `platform/ble` | Count found by opening sets until the stack refuses; remembered per device. |
| Repair timers and backoff (7.6, 10.4) | `core/protocol` | Clock injected. |
| GATT service and characteristics (8.1) | `platform/ble` (server and client), `core/protocol` (payloads) | UUIDs in one constants file. |
| Slots and `max_conn` (10.8) | `core/protocol` (policy), `platform/ble` (enforcement) | |
| CBOR payloads (9) | `core/protocol` codec (A-08) | Required-key checks per document type. |
| Signing, sealing, hashes (5, 8.4, 9.4) | `core/protocol` through `Crypto` (A-09) | `gid` binding checked on every sealed plaintext. |
| DEFLATE (7.3) | `core/protocol` | Raw DEFLATE through `java.util.zip.Inflater(nowrap = true)` with an output cap equal to the document limit. |
| Song references and local match (10.3) | `core/protocol` asks `LibraryRepository` | Match by `hash` only. |
| QR payload and join link (6.5) | `core/protocol` (payload), `app` (intent filters, QR rendering) | Intent filters from `docs/app-links/intent-filters.xml`. |
| Relays (11), protocol 1.1 | — | Not in 1.0; the frame `RELAY` flag is parsed and honoured by the pre-start scan. |

---

## 8. Dependencies

Every library the app may use. Licences are as published by each project and are confirmed at the dependency review before each release (NFR-SEC-05); "network" says whether the library can open a connection by itself.

| Library | Purpose | Licence | Network |
|---|---|---|---|
| Kotlin standard library, kotlinx-coroutines | Language, the engine's actor and flows | Apache 2.0 | no |
| kotlinx-serialization-json | `zangbundel.json`, archive manifest, `releases.json` | Apache 2.0 | no |
| AndroidX core, activity-compose, lifecycle (ViewModel, service), navigation-compose | App structure | Apache 2.0 | no |
| Jetpack Compose (BOM), Material 3 | UI (A-03) | Apache 2.0 | no |
| AndroidX core-splashscreen | System splash (guiDesign D-05) | Apache 2.0 | no |
| AndroidX appcompat | Per-app locale on API 26–32 (A-16) | Apache 2.0 | no |
| AndroidX Room (with KSP) | Database, FTS4 search (A-12) | Apache 2.0 | no |
| AndroidX DataStore preferences | Settings | Apache 2.0 | no |
| AndroidX WorkManager | Opt-in Wi-Fi update check only (A-14) | Apache 2.0 | no (schedules our own job) |
| lazysodium-android, with JNA | Ed25519, X25519, sealed box (A-09) | MPL 2.0; JNA Apache 2.0 or LGPL 2.1 | no |
| ZXing core | QR code generation (A-15) | Apache 2.0 | no |
| *Transitive, brought in by the libraries above:* Guava `listenablefuture` (WorkManager, Room), okio (DataStore), kotlinx atomicfu (coroutines), JetBrains annotations, JSpecify (AndroidX); named here since check G-06 (2026-10-27) | — | Apache 2.0 | no |
| *Test only:* JUnit 4, kotlin-test, kotlinx-coroutines-test, lazysodium-java, Jazzer (`jazzer-junit`, with the JUnit 5 Jupiter engine and platform launcher it runs on, in the `fuzz` source sets only), Compose UI test, AndroidX test runner | Tests (A-21) | Apache 2.0, MPL 2.0 (lazysodium), EPL (JUnit 4 and 5) | no |

Not used, on purpose: Google Play Services, Firebase, ML Kit, Play Core, Play Integrity (CLAUDE.md); Hilt and Dagger (A-17); OkHttp and Retrofit (A-14); Jackson and other CBOR libraries (A-08); JGit (A-11); any analytics, crash-reporting or advertising SDK (FR-NET-03, NFR-SEC-03). The platform itself supplies SHA-256 (`MessageDigest`), NFC normalisation (`java.text.Normalizer`), DEFLATE and ZIP (`java.util.zip`), collation (`java.text.Collator`) and the secure random source (`SecureRandom`).

---

## 9. Build and release

- **Location.** The Gradle project is `android/`; the application module `android/app` is the F-Droid build `subdir` (FDROID-RUNBOOK).
- **Toolchain.** Gradle wrapper committed (`gradlew`, `gradle/wrapper/`), Android Gradle Plugin, Kotlin, KSP, JDK major version and build tools pinned; the versions are chosen when the project is created and recorded in `libs.versions.toml`.
- **Dependency verification.** `gradle/verification-metadata.xml` with SHA-256 for every artefact, regenerated only when a dependency changes, in the same commit as the change (NFR-SEC-05).
- **Versions.** `versionName` semantic, `versionCode` in the form `YYYYMMDDnn`, both literal in `android/app/build.gradle.kts` (RUNBOOK Decisions, FDROID-RUNBOOK).
- **Build types.** `debug` (pseudo-locales on, logging on), `release` (R8 minify and resource shrinking, logs stripped per A-20), `fieldtest` (release settings plus the field log, application ID suffix `.fieldtest`, never published).
- **Reproducibility.** No build time, user or path in `BuildConfig` or resources; VCS info off; `dependenciesInfo { includeInApk = false; includeInBundle = false }`; no baseline profile in 1.0; fixed resource ordering. Two builds of the same tag are compared with `diffoscope` before the first F-Droid submission.
- **Bundle.** Splits disabled and no dynamic features (A-18).
- **Signing.** Release signing reads `keystore.properties` outside git (RUNBOOK Step 6); the build fails with a clear message when it is absent. No signing key or password is available to CI.
- **Manifest.** Permissions exactly as RUNBOOK Step 5: `BLUETOOTH_SCAN` with `neverForLocation`, `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT` (API 31+); `BLUETOOTH`, `BLUETOOTH_ADMIN`, `ACCESS_FINE_LOCATION` with `maxSdkVersion="30"`; `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`; `INTERNET`, `ACCESS_NETWORK_STATE`; `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` from WorkManager for the opt-in update check (A-14, SP-29); `POST_NOTIFICATIONS` (API 33+) for the service notification, requested only from leaders as A-06 says. Backup attributes per A-13. App Link and scheme intent filters from `docs/app-links/intent-filters.xml`.
- **CI.** A GitHub Actions workflow runs `bash scripts/check-design.sh`, `./gradlew check` (unit tests, lint with the translation checks as errors, dependency verification), the conformance corpus and a short fuzzing run per target, and builds an unsigned release APK. It has no secrets.
- **Source headers.** `SPDX-License-Identifier: Apache-2.0` in every new source file (FDROID-RUNBOOK).

---

## 10. Testing

| Layer | What | Where | Answers |
|---|---|---|---|
| Unit tests | Parser, canonicaliser, hash, line list, setlist resolution, archive bounds, CBOR codec, frames, reassembly, carousel, state machines, timers with a fake clock | `core/*/src/test`, JVM | NFR-TEST-01 |
| Conformance corpus | Test vectors: song files with their canonical bytes, hash, line list and report codes; setlists; archives good and bad (each A-code); protocol documents and frames with their bytes; sealed payloads opened with known keys | `android/core/testing/src/main/resources/conformance/`, run by the app's tests; the library repository's validator runs the song and archive vectors | A-21, A-22, NFR-MAINT-01 |
| Protocol simulation | Leader and 50 singers over the simulated transport with loss, slot limits and latency: join, push, repair, request, delegate, leave, end, resume | `core/testing` and `core/protocol` tests | NFR-TEST-01; NFR-PERF-01 and FR-BLE-10 in simulation only |
| Fuzzing | Jazzer targets for the song parser, setlist parser, library and manifest JSON, ZIP reader, DEFLATE with cap, CBOR decoder, frame reassembly, the join-link payload (base64url text as the camera app delivers it, then CBOR, ble-protocol 6.5) and the leader's GATT write handlers (Join, Request, Control, Transfer with its ranges); each run with its size and time bound | `core/*/src/test`, short runs in CI, long runs before a release | NFR-SEC-04, assessment SP-09 |
| Device and field tests | Two-phone smoke tests (instrumented), then field tests with the `fieldtest` build and known groups: range, singer counts, battery, resume | `app/src/androidTest`; scenarios D-01 to D-08 and F-01 to F-11 in `design/test-plan.md` | NFR-PERF-01, NFR-PERF-02, NFR-REL-01, ble-protocol Q2, Q3, Q9 |

Field results are measurements, not this document's claims. Until the field scenarios of `design/test-plan.md` have run and their results are recorded, the protocol's capacity and battery figures remain open questions. The test plan sets the scenarios, pass criteria, budgets and release gate for every layer in this table.

---

## 11. Security and privacy checks

The assessment asks for SP-05, SP-08, SP-09 and SP-15 to be checked against the concrete choices once this document exists (assessment section 7, item 1). Where this draft stands:

| Finding | Concrete choice | Status in this draft |
|---|---|---|
| SP-05 Auto-backup | A-13: backup and device transfer disabled for all app data | Met by configuration; check the merged manifest of the first bundle (assessment 7, item 2). |
| SP-08 Components that phone home, logs that leak | A-14 one HTTPS path, user-initiated; A-17 and section 8 list every dependency with its network behaviour; A-20 logging policy; no crash reporter | Met by the dependency list and the logging rule; re-checked at each dependency review. |
| SP-09 Parsers on hostile input | A-08 own bounded CBOR codec; A-10 bounded song parser; archive bounds from song-format 12.2; DEFLATE output cap; fuzzing in A-21 | Designed; met only when the fuzz targets exist and run. |
| SP-15 Supply chain | A-19 pinned toolchain, verification metadata, no flavours, reproducible output | Designed; met when the first build passes verification and a reproducibility comparison. |

The decisions were taken on 2026-10-22, so the assessment's re-run for this document (assessment section 7, item 1) is now due; it is recorded in the assessment's own version table, not here.

---

## 12. Found while writing

Inconsistencies found in other documents while writing v0.1. All five were fixed on 2026-10-22 (`docs/RUNBOOK.md` Decisions and Step 9 item 7; `design/ble-protocol.md` v0.13); the list stays as the record:

1. **GitHub Release APK.** RUNBOOK Decisions ("Packaging") and Step 9 put the Play-signed universal APK from the App Bundle Explorer on GitHub Releases; FDROID-RUNBOOK Steps 3 and 6 put an owner-signed APK built from the tag there, which F-Droid's reproducible build is compared with. Both carry the same signature, but only the tag-built APK can be byte-identical to F-Droid's build. Recommendation: the tag-built APK (FDROID-RUNBOOK), and the RUNBOOK row follows.
2. **Resume record contents.** ble-protocol 10.1 and 10.9 list what the leader persists for resume but not the X25519 secret key or the delegate list, which a resumed gathering needs to open sealed writes and keep its delegates. Section 5.4 of this document includes both; the protocol should say so.
3. **Rate-limit reference.** ble-protocol 10.12 cites "10.8 rate limits", which are in section 13.
4. **State frame count.** ble-protocol 9.4 sizes the state record for its maximum, a 440-byte body in 30 legacy frames; the capacity table in 7.7 uses 15 frames without saying that this is a typical rather than a maximum record. The table should name the size it assumes, since the leader's burst timing (7.4) depends on it.
5. **Protocol Q7** (explicit leave) is still listed as open although Control op 10 (v0.11) answers it.

---

## 13. Open questions

| # | Question | Affects |
|---|---|---|
| Q1 | With splits disabled (A-18), confirm on the first internal-test install that a Play install has no split APKs, that the shared copy installs on a second phone, and whether Play later updates a copy that was installed by sharing (Android's update ownership may keep Play from updating an app it did not install). | A-18, FR-DIST-05, FRD Q11 |
| Q2 | Does `fdroid scanner` accept the prebuilt libsodium and JNA native libraries from Maven Central, or does F-Droid want them built from source? If the latter, the fallback is a srclib build of libsodium. | A-09, FDROID-RUNBOOK |
| Q3 | Do singers need the foreground service (A-06), or does the screen-on lyric view keep scanning reliable enough? Measure in the field tests. | A-06, NFR-PERF-02 |
| Q4 | FTS4 search with the unicode61 tokenizer on 2,000 songs on a low-end phone: within 300 ms? | A-12, NFR-PERF-03 |
| Q5 | Kotlin Multiplatform core or native Swift for iOS; decided at the start of the iOS phase. | A-05 |

All decisions were taken by the owner on 2026-10-22. The ones that are costly to change once code exists are A-03 (toolkit), A-04 (module split), A-06 (engine in a service), A-08 (own CBOR codec), A-11 (no git client), A-13 (no backup) and A-18 (single APK); reopening one of them means a new decision that supersedes it (section 1.3).
