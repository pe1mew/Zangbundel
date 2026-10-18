# Zangbundel – Functional Requirements

| Field | Value |
|---|---|
| Document | Functional Requirements Document (FRD) |
| Project | Zangbundel – campfire songbook app |
| Version | 0.3 (draft) |
| Date | 2026-10-18 |
| Author | Remko Welling |
| Status | Draft for review |

| Version | Date | Change |
|---|---|---|
| 0.1 | 2026-10-18 | First draft. |
| 0.2 | 2026-10-18 | Added: only one gathering within BLE range (FR-GATH-16, 17); delegate role (section 8.4, FR-LEAD-40 to 46). |
| 0.3 | 2026-10-18 | Added: leader-to-singer information by repeated broadcast, connections only for upstream traffic and recovery of missed data (FR-BLE-15, 16). |

---

## 1. Introduction

### 1.1 Purpose

This document describes **what** the Zangbundel app must do. It is the reference for design, implementation and testing. It deliberately does not prescribe **how** the app is built; architecture, the BLE protocol and the song file format get their own design documents (see section 19).

### 1.2 Scope

Zangbundel is a mobile songbook app for singing together around a campfire, for example at scouting events. One device acts as the **leader** and distributes lyrics over Bluetooth Low Energy (BLE) to the devices of the **singers** sitting around the fire. The app works without internet access. Lyrics come from an external, version-controlled library (a git repository) so that copyrights and licences are managed outside the app.

The first target platform is Android. The design must allow an iOS version later with the same functionality and interoperability over BLE.

### 1.3 Document conventions

- Each requirement has a unique ID of the form `FR-<AREA>-<nn>` (functional) or `NFR-<AREA>-<nn>` (non-functional). IDs are never reused; a withdrawn requirement is marked *Withdrawn* and kept.
- Priority uses the key words of RFC 2119: **MUST** (required for the phase), **SHOULD** (expected unless there is a good reason), **MAY** (optional).
- Each requirement carries a **phase** tag: **P1** (first release), **P2** (karaoke release), **P3** (instrument release). See section 2.5.
- Dutch terms used in the app UI are given in parentheses where helpful, e.g. leader (*kampvuurleider*).

### 1.4 Definitions

| Term | Meaning |
|---|---|
| **App** | The Zangbundel application installed on a device. Every installation can act as leader or singer. |
| **Leader** (*kampvuurleider*) | The person, and the device, that starts and controls a gathering. Exactly one per gathering. |
| **Singer** (*zanger*) | A participant who has joined a gathering to receive lyrics. |
| **Delegate** (*assistent-leider*) | A singer to whom the leader has given the right to handle song requests and push songs on the leader's behalf. Zero or more per gathering; the role ends with the gathering. |
| **Gathering** (*kampvuur*) | A session started by a leader and ended by that leader. Singers join and leave while it runs. |
| **Beacon** | The BLE advertisement the leader broadcasts for the duration of the gathering so singers can discover and join it. |
| **Library** (*bundel*) | A collection of songs stored in a git repository, imported into the app and updated from the repository. |
| **Song** | A single entry in the library: lyrics plus metadata (title, language, author, licence, …) and, in later phases, timing data and notation. |
| **Setlist** | An ordered list of songs the leader prepares or builds during a gathering. |
| **Current song** | The song the leader has pushed to the singers as the one being sung now. |
| **Request** (*verzoeknummer*) | A message from a singer to the leader asking for a song. |
| **Gathering memory** | The list of song titles sung during a past gathering that a device keeps after the gathering ends. |

---

## 2. Product overview

### 2.1 Vision

Everyone around the fire sees the same lyrics at the same moment on their own phone, in the dark, without anyone needing mobile data or Wi-Fi. The leader decides what is sung and when; singers can ask for songs. The lyrics themselves live in an open, version-controlled library that a community can maintain and license properly.

### 2.2 Actors

| Actor | Description |
|---|---|
| **Leader** | Runs the gathering: starts/ends it, chooses songs, pushes lyrics, handles requests, may instruct singers to download or update the library. Usually a scout leader or the person with the guitar. |
| **Singer** | Joins a gathering, reads lyrics, scrolls at their own pace, sends requests, leaves when they want. |
| **Delegate** | A singer appointed by the leader during the gathering. Handles requests and pushes songs as the leader would, so the leader can sing or play. Cannot end the gathering or appoint other delegates. |
| **Library maintainer** | A person (outside the app) who edits the git repository with songs, metadata and licence information. Not an app user role, but the app's import rules are written for them. |

A device is not permanently a leader or a singer. Any installation can start a gathering (becoming leader) or join one (becoming singer). A device is never both at the same time.

### 2.3 Key scenario (happy path)

1. Before the trip, the leader opens Zangbundel with internet access and imports or updates the library from the configured repository.
2. At the fire, the leader starts a gathering and gives it a name ("Zomerkamp vrijdag"). The app first checks that no other gathering is running within range, then starts sending the beacon.
3. Singers open the app. It shows nearby gatherings; they tap the one for their fire and join.
4. The leader picks a song. The lyrics appear on all singers' devices within a few seconds. Singers scroll line by line as they sing; the display is white text on black.
5. A singer requests "Hoofd, schouders, knie en teen". The leader sees the request, accepts it and pushes that song next.
6. The leader picks up the guitar and makes another leader at the fire a delegate. From then on the delegate handles requests and pushes the next songs; the leader keeps control of the gathering itself.
7. A singer who has to leave early exits the gathering. Their device returns to the idle screen.
8. The leader ends the gathering. All singer devices leave automatically. Lyrics received over BLE are discarded; the list of titles sung is kept as a memory of the evening.

### 2.4 Platforms

- **P1:** Android phones and tablets. The minimum Android version is fixed in the architecture document. BLE peripheral (advertising) mode is required on the leader's device and BLE central (scanning/connecting) mode on singer devices.
- **Later:** iOS (iPhone/iPad). The BLE protocol and the song format must not depend on Android-only features, so that Android and iOS devices can take part in the same gathering.

### 2.5 Phases

| Phase | Theme | Summary |
|---|---|---|
| **P1** | Songbook over BLE | Library import/update, gatherings, lyric distribution, display, requests, gathering memory, NL/EN UI. |
| **P2** | Karaoke | Timed lyrics, current-line highlighting synchronised with the leader, singing aids. |
| **P3** | Instruments | Chords and musical notation for instrument players, transposition, musician view. |

---

## 3. General requirements

**FR-GEN-01** (MUST, P1) — Every installation of the app MUST be able to act as leader or as singer; the user chooses the role when starting or joining a gathering. No separate "leader app" is installed.

**FR-GEN-02** (MUST, P1) — A device MUST NOT be leader and singer at the same time.

**FR-GEN-03** (MUST, P1) — All core functions (starting a gathering, joining, receiving and showing lyrics, requests, browsing the local library) MUST work without any internet connection.

**FR-GEN-04** (MUST, P1) — The app MUST clearly show its current state at all times: idle, leading a gathering, joined to a gathering, searching for gatherings, or importing/updating the library.

**FR-GEN-05** (MUST, P1) — The app MUST request only the permissions needed for the current action (Bluetooth, location where the platform requires it for BLE scanning, network for library updates) and MUST explain in plain language why each permission is needed before asking.

**FR-GEN-06** (SHOULD, P1) — The app SHOULD offer a short first-use introduction explaining the leader/singer concept and how to join a gathering.

**FR-GEN-07** (MUST, P1) — The app MUST show the version of the app and the version (commit/tag) of each imported library in an "About" screen.

---

## 4. Language and localisation

**FR-LANG-01** (MUST, P1) — The user interface MUST be available in Dutch (primary) and English (secondary).

**FR-LANG-02** (MUST, P1) — All user-visible UI text MUST be held in locale resource files so that a new UI language can be added by adding one translation file, without code changes.

**FR-LANG-03** (MUST, P1) — The app MUST follow the device language by default and MUST let the user override the UI language in the settings.

**FR-LANG-04** (MUST, P1) — Song content language is independent of UI language. A Dutch UI MUST be able to show English, German or any other songs, and vice versa.

**FR-LANG-05** (MUST, P1) — Every song MUST carry a language code (BCP 47, e.g. `nl`, `en`, `de`) in its metadata. The app MUST be able to filter and sort the library by song language.

**FR-LANG-06** (SHOULD, P1) — The library format SHOULD allow a song to have translations or variants (e.g. a Dutch and an English version of the same melody) that are linked to each other, so the app can offer "show this song in …" where available.

**FR-LANG-07** (SHOULD, P1) — Dates, numbers and sorting (e.g. ignoring "De", "Het", "The" at the start of a title) SHOULD follow the selected UI locale.

---

## 5. Lyrics library

### 5.1 Source and format

**FR-LIB-01** (MUST, P1) — Songs MUST be stored outside the app in one or more git repositories ("libraries"). The app contains no lyrics of its own.

**FR-LIB-02** (MUST, P1) — The app MUST ship with the URL of a default library pre-configured and MUST let the user add, remove, enable or disable further library repositories by URL.

**FR-LIB-03** (MUST, P1) — Songs in a library MUST be plain-text, human-editable files with a defined structure and metadata, so that maintainers can edit them with any text editor and review changes with normal git tooling. The exact format is specified in the song format design document (section 19). Candidate: ChordPro, which already supports lyrics, chords and metadata directives.

**FR-LIB-04** (MUST, P1) — Each song MUST have at least the following metadata: a stable unique identifier, title, language, lyrics text. Each song SHOULD have: author(s)/composer, copyright holder, licence identifier (e.g. SPDX or "public domain"), source/attribution, tags (e.g. *kampvuur*, *welpen*, *kerst*), and an optional melody reference ("sung to the tune of …").

**FR-LIB-05** (MUST, P1) — A library MUST contain a library-level metadata file with at least: library name, description, maintainer contact, licence terms of the collection, format version and the minimum app version that can read it.

**FR-LIB-06** (MUST, P1) — The library format MUST be extensible so that P2 (timing data) and P3 (chords, notation) can be added to a song file without breaking P1 apps. P1 apps MUST ignore song content they do not understand.

**FR-LIB-07** (MUST, P1) — The app MUST validate a library on import and MUST report songs it cannot parse (by file name and reason) without refusing the rest of the library.

### 5.2 Import and update

**FR-LIB-10** (MUST, P1) — When internet is available the app MUST be able to import a library from its repository (initial clone or equivalent download of a chosen branch or tag) and store it locally.

**FR-LIB-11** (MUST, P1) — The app MUST be able to update a locally stored library to the latest version of its configured branch/tag and MUST show what changed (songs added, changed, removed) after the update.

**FR-LIB-12** (MUST, P1) — Import and update MUST be user-initiated or leader-directed (see FR-LEAD-30). The app MUST NOT download library data silently in the background without the user having enabled this.

**FR-LIB-13** (SHOULD, P1) — The user SHOULD be able to enable automatic update checks when on Wi-Fi.

**FR-LIB-14** (MUST, P1) — The app MUST support repositories served over HTTPS without authentication (public libraries). Support for private repositories with credentials MAY be added later.

**FR-LIB-15** (MUST, P1) — Import and update MUST be resumable or atomic: an interrupted download MUST never leave the app with a half-updated library. The previously imported version stays usable until the new one is complete.

**FR-LIB-16** (SHOULD, P1) — The app SHOULD support importing a library from a local file (e.g. a zip exported from the repository) for devices that never have internet.

**FR-LIB-17** (MUST, P1) — The app MUST record, per library, the repository URL, branch/tag and the exact revision (commit hash) that is installed, so that leader and singers can determine whether they have the same version of a song (see FR-BLE-12).

### 5.3 Copyright and licensing

**FR-LIB-20** (MUST, P1) — The app MUST display the licence and attribution metadata of a song to the user on request (e.g. via an info button on the song screen).

**FR-LIB-21** (MUST, P1) — The app MUST display the library-level licence terms when a library is first imported and on request afterwards.

**FR-LIB-22** (SHOULD, P1) — The library format SHOULD allow a maintainer to mark a song as "display only / do not redistribute". The leader app SHOULD then push only the song identifier (not the lyrics text) over BLE, so singers must have that library installed to see it.

**FR-LIB-23** (MUST, P1) — The app itself MUST NOT alter lyrics or metadata of imported songs. Corrections are made in the repository.

### 5.4 Browsing the local library

**FR-LIB-30** (MUST, P1) — The user MUST be able to browse all locally imported libraries as one combined songbook, and MUST be able to filter by library, language and tag.

**FR-LIB-31** (MUST, P1) — The user MUST be able to search songs by title and by a fragment of the lyrics.

**FR-LIB-32** (SHOULD, P1) — The user SHOULD be able to mark songs as favourites.

**FR-LIB-33** (MUST, P1) — Browsing and reading songs from the local library MUST be possible at any time, also outside a gathering and also while joined to a gathering (without losing the current song view, see FR-SING-14).

---

## 6. Gathering lifecycle

**FR-GATH-01** (MUST, P1) — A leader MUST be able to start a gathering by giving it a name. The app MUST propose a default name (e.g. device name plus date).

**FR-GATH-02** (MUST, P1) — From the moment a gathering is started until it is ended, the leader's device MUST broadcast a BLE beacon that identifies the app, the gathering name and a unique gathering identifier, so singers can discover it.

**FR-GATH-03** (MUST, P1) — A singer MUST be able to see a list of all gatherings currently in range and join one of them by tapping it.

**FR-GATH-04** (MUST, P1) — When multiple gatherings are in range, the list MUST show their names and, where possible, an indication of signal strength or distance, so a singer can tell which fire is theirs.

**FR-GATH-05** (SHOULD, P1) — The leader SHOULD be able to protect a gathering with a short join code (e.g. 4 digits) that singers must enter. By default a gathering is open.

**FR-GATH-06** (MUST, P1) — After joining, a singer MUST immediately receive the current song (if any) and the state of the gathering, so late joiners are not left on an empty screen.

**FR-GATH-07** (MUST, P1) — A singer MUST be able to leave a gathering at any time with a single, confirmed action. Leaving MUST NOT affect other singers or the leader beyond updating the participant count.

**FR-GATH-08** (MUST, P1) — Only the leader MUST be able to end a gathering. Ending MUST require confirmation.

**FR-GATH-09** (MUST, P1) — When the leader ends the gathering, the beacon stops and all joined singer devices MUST leave automatically and show that the gathering has ended.

**FR-GATH-10** (MUST, P1) — If a singer device loses contact with the leader (out of range, leader's Bluetooth switched off) it MUST keep showing the current lyrics, indicate that the connection is lost, and automatically rejoin when the leader is in range again as long as the gathering is still running.

**FR-GATH-11** (MUST, P1) — If the singer device cannot find the gathering again within a configurable time (default: 5 minutes), it MUST treat the gathering as ended (FR-DATA-01 applies).

**FR-GATH-12** (MUST, P1) — The leader MUST see the number of joined singers at all times and SHOULD be able to see a list of them by a display name chosen by each singer.

**FR-GATH-13** (SHOULD, P1) — If the leader's app is closed unexpectedly (crash, battery empty) and restarted within a configurable time, the app SHOULD offer to resume the same gathering with the same identifier, so singers reconnect automatically.

**FR-GATH-14** (MAY, P1) — The leader MAY hand over the leader role to a joined singer, after which the roles are swapped and the gathering continues.

**FR-GATH-15** (MUST, P1) — The gathering MUST keep running while the leader's screen is off or the app is in the background, within the limits the platform allows for BLE advertising. The app MUST warn the leader when the platform is likely to stop the beacon (e.g. battery saver mode).

**FR-GATH-16** (MUST, P1) — Only one gathering can run within BLE range. Before starting a gathering the app MUST listen for running gatherings for at least 5 seconds. If one is in range, the app MUST refuse to start a new gathering, MUST show the name of the gathering it found and MUST offer to join it instead. There is no override (see open question Q9).

**FR-GATH-17** (SHOULD, P1) — While leading, the app SHOULD keep detecting other running gatherings that come into range (started out of range and moved closer) and SHOULD warn the leader, naming the other gathering. The app cannot stop a gathering started elsewhere; both continue.

---

## 7. Communication over BLE

These are functional requirements on the behaviour the user experiences. The protocol itself is specified in a separate BLE protocol design document.

**FR-BLE-01** (MUST, P1) — All communication between leader and singers during a gathering MUST take place over BLE. No Wi-Fi, hotspot, mobile data or internet is required for any gathering function.

**FR-BLE-02** (MUST, P1) — The beacon MUST be recognisable as a Zangbundel gathering by the singer app without pairing, and joining MUST NOT require the user to pair devices in the system Bluetooth settings.

**FR-BLE-03** (MUST, P1) — The leader MUST be able to push the current song to all joined singers. On a singer device the new song MUST be visible within 5 seconds of the leader pushing it under normal conditions (singers within 10 m, up to the supported number of singers, see NFR-PERF-01).

**FR-BLE-04** (MUST, P1) — Lyric distribution MUST be reliable: a singer either receives the complete song or is told that reception failed and can request a resend; partially received lyrics MUST never be shown as complete.

**FR-BLE-05** (MUST, P1) — Song data MUST be transferred in a compact form (text compression, chunking) suited to BLE throughput. A typical song (up to about 4 kB of text) MUST reach a singer within the time stated in FR-BLE-03.

**FR-BLE-06** (MUST, P1) — The protocol MUST support messages from singers to the leader (requests, section 11) and from the leader to singers (current song, setlist, download instruction, gathering end, informational messages).

**FR-BLE-07** (MUST, P1) — The protocol MUST be versioned. A singer with an older protocol version MUST get a clear message ("update the app to join this gathering") instead of failing silently.

**FR-BLE-08** (MUST, P1) — The protocol MUST be platform-neutral so that Android and (future) iOS devices can take part in the same gathering, in either role.

**FR-BLE-10** (MUST, P1) — The app MUST support at least 25 simultaneously joined singers per gathering. The design SHOULD aim for 50. If the leader device cannot serve more singers, additional singers MUST get a clear message rather than an unexplained failure.

**FR-BLE-11** (SHOULD, P1) — Where the number of singers exceeds what the leader device can serve directly, the app SHOULD distribute lyrics via joined singer devices (relay) transparently, so the user experience stays the same.

**FR-BLE-12** (MUST, P1) — When the leader pushes a song, the app MUST first send the song identifier and library revision. A singer that already has that exact song version in its local library MUST use its local copy and MUST NOT download the lyrics over BLE. Only singers that lack the song receive the full text. This keeps distribution fast when most singers have the library installed.

**FR-BLE-13** (MUST, P1) — The leader MUST be able to send a short free-text message to all singers (e.g. "Pauze, over 10 minuten verder").

**FR-BLE-14** (MUST, P1) — Communication MUST be limited to data needed for the gathering. No personal data other than the self-chosen display name is exchanged.

**FR-BLE-15** (MUST, P1) — Information from the leader to the singers (current song, lyrics, setlist, messages, request outcomes, gathering state) MUST be sent primarily by broadcast, so that it reaches any number of singers at once without a connection per singer, and MUST be repeated more than once so that singers who miss part of it still receive it. A singer MUST need a connection to the leader only to register, to send a request, to act as delegate, or to recover information it missed. The objective is to keep the number of connections small so that the number of participants is not limited by the leader device's connection capacity (FR-BLE-10).

**FR-BLE-16** (MUST, P1) — A singer that has not received information completely within a reasonable time MUST be able to request exactly the missing part from the leader, and MUST get it without disturbing the broadcast to the other singers.

---

## 8. Leader functions

Where sections 8.1 and 8.2 say "the leader", a delegate (section 8.4) can perform the same action unless the requirement says *leader only*.

### 8.1 Choosing and pushing songs

**FR-LEAD-01** (MUST, P1) — The leader MUST be able to select any song from the local library and push it as the current song with one action.

**FR-LEAD-02** (MUST, P1) — The leader MUST be able to prepare a setlist before the gathering (offline) and reuse setlists across gatherings.

**FR-LEAD-03** (MUST, P1) — During a gathering the leader MUST be able to see the setlist, push the next song, skip a song, and add songs to the setlist (e.g. accepted requests).

**FR-LEAD-04** (MUST, P1) — The leader MUST see the current song in the same lyric view as the singers, so the leader can sing along, and MUST be able to switch between the lyric view and the control view quickly.

**FR-LEAD-05** (SHOULD, P1) — The leader SHOULD be able to indicate the current position in the song (verse/line). Singers who have "follow the leader" switched on (FR-SING-12) scroll along automatically.

**FR-LEAD-06** (MUST, P1) — The leader MUST be able to clear the current song (nothing being sung) and to re-push the current song (e.g. for singers that report a failed transfer).

**FR-LEAD-07** (SHOULD, P1) — The leader SHOULD be able to push a song in a different language variant when one is available (FR-LANG-06).

### 8.2 Handling requests

**FR-LEAD-10** (MUST, P1) — The leader MUST see incoming requests in a list with the song title, the requester's display name and the time, with the newest or most-requested on top.

**FR-LEAD-11** (MUST, P1) — The leader MUST be able to accept a request (which adds it to the setlist), push it immediately, or decline it. The requester is informed of the outcome (FR-REQ-05).

**FR-LEAD-12** (SHOULD, P1) — Identical requests from several singers SHOULD be grouped and show the count.

**FR-LEAD-13** (SHOULD, P1) — The leader SHOULD be able to temporarily disable requests ("no more requests tonight").

### 8.3 Directing library downloads

**FR-LEAD-30** (MUST, P1) — The leader MUST be able to send an instruction to all singers to download or update a given library (repository URL, branch/tag and revision). On the singer device this shows as a prompt; it is only carried out after the singer confirms and only if the singer device has internet access.

**FR-LEAD-31** (SHOULD, P1) — The leader SHOULD be able to see which singers have the library at the same revision as the leader, so the leader knows whether pushing songs will be fast (FR-BLE-12).

**FR-LEAD-32** (MUST, P1) — Singers without internet MUST still be able to take part fully; the leader's download instruction is advisory and lyrics continue to be sent over BLE to singers that lack them.

### 8.4 Delegates

**FR-LEAD-40** (MUST, P1, *leader only*) — The leader MUST be able to give one or more joined singers the delegate role, chosen from the list of joined singers by display name, and MUST be able to revoke it at any time. The role ends automatically when the gathering ends or when the delegate leaves.

**FR-LEAD-41** (MUST, P1) — A delegate MUST see the same request list as the leader (FR-LEAD-10) and MUST be able to accept, decline and immediately push requests (FR-LEAD-11). The requester is informed of the outcome in the same way.

**FR-LEAD-42** (MUST, P1) — A delegate MUST be able to push a song from the local library as the current song, add songs to the setlist and push the next song in the setlist (FR-LEAD-01, FR-LEAD-03), provided the leader's device also has that song. Pushing a song the leader's device does not have SHOULD be possible by transferring the song from the delegate to the leader over BLE first.

**FR-LEAD-43** (MUST, P1) — Delegate actions take effect through the leader's device, which remains the single source of the gathering state. If the leader's device cannot be reached, the delegate's action MUST be refused with a clear message and MUST NOT be applied later without the delegate repeating it.

**FR-LEAD-44** (MUST, P1, *leader only*) — Ending the gathering, changing the join code, appointing or revoking delegates, sending library download instructions and handing over the leader role remain leader-only. The delegate UI MUST NOT offer these actions.

**FR-LEAD-45** (MUST, P1) — A singer MUST be told when the leader makes them a delegate and when the role is revoked, and the leader MUST see at all times who the delegates are. Songs pushed and requests handled by a delegate appear on the leader's device as they happen.

**FR-LEAD-46** (SHOULD, P1) — The delegate role SHOULD survive a temporary loss of contact between the delegate and the leader within the gathering (FR-GATH-10).

---

## 9. Singer functions

### 9.1 Joining and receiving

**FR-SING-01** (MUST, P1) — A singer MUST be able to choose a display name once (stored on the device) that is shown to the leader. The name is optional; a default (e.g. "Zanger 7") is used otherwise.

**FR-SING-02** (MUST, P1) — When the leader pushes a new song, the singer device MUST switch to it automatically and MUST make the change noticeable (brief visual cue; optional vibration, configurable).

**FR-SING-03** (MUST, P1) — The singer MUST be able to see the setlist (songs already sung and, if the leader shares it, upcoming songs).

**FR-SING-04** (MUST, P1) — The singer MUST see the connection state with the leader (connected / reconnecting / gathering ended).

### 9.2 Reading lyrics

**FR-SING-10** (MUST, P1) — Lyrics MUST be shown in full, in the order sung, with verses, choruses and repeats laid out so the structure is clear (e.g. chorus indented or labelled *Refrein*).

**FR-SING-11** (MUST, P1) — The singer MUST be able to scroll the lyrics line by line with a simple, reliable gesture usable in the dark and with cold fingers: tap on the lower/upper half of the screen for one line down/up, and swipe for continuous scrolling. The hardware volume keys SHOULD also scroll one line down/up when enabled in settings.

**FR-SING-12** (SHOULD, P1) — The singer SHOULD be able to switch "follow the leader" on or off. When on, the view follows the position the leader indicates (FR-LEAD-05); any manual scroll switches it off until the singer switches it on again or the next song is pushed.

**FR-SING-13** (MUST, P1) — The singer MUST be able to jump to the beginning of the song and to the next/previous verse or chorus.

**FR-SING-14** (MUST, P1) — The singer MUST be able to leave the current song view to browse the local library or the setlist and return to the current song with one action. When the leader pushes a new song while the singer is browsing, FR-SING-02 applies.

**FR-SING-15** (MUST, P1) — The singer MUST be able to open the current song's metadata (author, licence, source) from the song view.

---

## 10. Display

**FR-DISP-01** (MUST, P1) — The lyric view MUST offer at least two themes: **day** (black text on white) and **night** (white text on black, with no bright UI elements). The user MUST be able to switch with one action from the lyric view.

**FR-DISP-02** (SHOULD, P1) — The app SHOULD be able to switch between day and night theme automatically based on the device's dark-mode setting or the ambient light sensor, with manual override.

**FR-DISP-03** (MUST, P1) — The night theme MUST avoid large bright areas: dialogs, lists and buttons are dark as well, so the display does not dazzle at the fire.

**FR-DISP-04** (MUST, P1) — The lyric view MUST work in portrait and landscape orientation and MUST keep the current line in view when the device is rotated.

**FR-DISP-05** (MUST, P1) — The user MUST be able to change the text size of the lyrics in steps (at least 5 steps, including a very large size readable from arm's length). The setting is remembered.

**FR-DISP-06** (MUST, P1) — Long lines MUST wrap; horizontal scrolling MUST never be needed to read lyrics.

**FR-DISP-07** (MUST, P1) — The screen MUST stay on while the lyric view is shown during a gathering. The user MUST be able to switch this off.

**FR-DISP-08** (SHOULD, P1) — The lyric view SHOULD offer a screen brightness slider that does not require leaving the app.

**FR-DISP-09** (MUST, P1) — The current line (when following the leader or in karaoke mode) MUST be clearly distinguishable, e.g. by a highlight bar or a different weight, in both themes.

**FR-DISP-10** (MUST, P1) — The lyric view MUST use a highly legible font and sufficient contrast (WCAG AA or better) in both themes.

**FR-DISP-11** (SHOULD, P1) — The lyric view SHOULD show, in a small unobtrusive header or footer: song title, position in the setlist (e.g. 3/12), connection state and number of singers (leader only).

---

## 11. Song requests

**FR-REQ-01** (MUST, P1) — A singer MUST be able to request a song from the local library by selecting it. One action sends the request to the leader.

**FR-REQ-02** (MUST, P1) — A singer MUST be able to request a song that is not in their local library by typing a title (free text, max. 100 characters).

**FR-REQ-03** (MUST, P1) — A request MUST reach the leader within 5 seconds under normal conditions. If the singer is not connected, the request is queued and sent on reconnection, or discarded with a message when the gathering ends.

**FR-REQ-04** (SHOULD, P1) — A singer SHOULD be able to withdraw a pending request.

**FR-REQ-05** (MUST, P1) — The singer MUST see the status of each request: pending, accepted (and position in the setlist if known), declined, or sung.

**FR-REQ-06** (SHOULD, P1) — The app SHOULD limit the number of pending requests per singer (default: 3) to prevent flooding the leader.

**FR-REQ-07** (MAY, P1) — Singers MAY be able to see and "+1" other singers' pending requests when the leader has enabled this.

---

## 12. Connectivity and offline behaviour

**FR-NET-01** (MUST, P1) — The app MUST never block a gathering function on network availability. Network is used only for library import/update (section 5.2) and nothing else.

**FR-NET-02** (MUST, P1) — The app MUST indicate clearly when a library action requires internet and internet is not available, and MUST NOT retry in the background without the user's consent.

**FR-NET-03** (MUST, P1) — The app MUST NOT contact any server other than the configured library repositories. No analytics, telemetry or crash reporting is sent without explicit opt-in.

**FR-NET-04** (SHOULD, P1) — Library downloads SHOULD be possible over mobile data only after a confirmation that shows the expected download size.

---

## 13. Data retention and the gathering memory

**FR-DATA-01** (MUST, P1) — Song content received over BLE during a gathering MUST be stored only for the duration of that gathering and MUST be deleted from the singer device when the singer leaves, when the leader ends the gathering, or when the gathering is treated as ended (FR-GATH-11).

**FR-DATA-02** (MUST, P1) — Songs that are part of a library the user imported themselves are not affected by FR-DATA-01 and remain on the device.

**FR-DATA-03** (MUST, P1) — After a gathering ends, the device MUST keep a **gathering memory**: gathering name, date, the leader's display name (singer side) or participant count (leader side) and the ordered list of song titles sung, including the song identifier and library reference so the song can be found if the user later imports that library.

**FR-DATA-04** (MUST, P1) — The user MUST be able to view past gathering memories and delete any or all of them. The app MUST NOT share gathering memories with anyone.

**FR-DATA-05** (SHOULD, P1) — From a gathering memory the user SHOULD be able to open a song if it is in a local library, and SHOULD be able to export the list of titles as plain text (e.g. to share a "what we sang" list).

**FR-DATA-06** (MUST, P1) — Requests, setlists of the gathering and connection data MUST be discarded when the gathering ends, except for the song titles kept in the gathering memory. Prepared setlists (FR-LEAD-02) are kept.

**FR-DATA-07** (MUST, P1) — Data stored on the device (libraries, settings, gathering memories) MUST be removable by the user from within the app ("remove all data").

---

## 14. Phase 2 – Karaoke mode

**FR-KAR-01** (MUST, P2) — The song format MUST allow optional timing data per line (and optionally per syllable) relative to the start of the song, so a song can be sung in karaoke mode. Songs without timing data remain fully usable in P1 mode.

**FR-KAR-02** (MUST, P2) — The leader MUST be able to start, pause, resume and stop karaoke playback of the current song. All singers' displays MUST follow the same timeline.

**FR-KAR-03** (MUST, P2) — In karaoke mode the current line MUST be highlighted on all devices within 300 ms of each other, and the next line(s) MUST be visible, so singers can read ahead.

**FR-KAR-04** (MUST, P2) — Karaoke timing MUST be synchronised over BLE without internet or GPS, using a shared gathering clock established between leader and singers.

**FR-KAR-05** (SHOULD, P2) — The leader SHOULD be able to adjust the tempo (faster/slower) during playback; singers follow.

**FR-KAR-06** (SHOULD, P2) — The app SHOULD offer a count-in (visual, optionally audible on the leader device) before the first line.

**FR-KAR-07** (MAY, P2) — The app MAY offer a melody guide (pitch indication per line or syllable) when the song carries pitch data, and MAY play a simple reference melody on the leader device.

**FR-KAR-08** (MAY, P2) — The leader MAY be able to record line timings while singing a song once ("tap to time"), and export them as a proposed contribution to the library.

**FR-KAR-09** (MUST, P2) — A singer MUST be able to switch off karaoke highlighting locally and scroll manually, without affecting other singers.

---

## 15. Phase 3 – Instrument notation

**FR-INST-01** (MUST, P3) — The song format MUST allow chords inline with the lyrics (as ChordPro does) and MUST allow attaching musical notation (melody line, e.g. in ABC notation or MusicXML) to a song.

**FR-INST-02** (MUST, P3) — A user MUST be able to choose a **musician view** that shows chords above the lyrics and, when available, the notation, instead of the plain lyric view. The choice is per device and does not affect other participants.

**FR-INST-03** (MUST, P3) — The musician view MUST support transposition in semitone steps, with the chosen key shown, and SHOULD support a capo setting for guitar.

**FR-INST-04** (SHOULD, P3) — The app SHOULD show chord diagrams for guitar and ukulele for the chords in the current song.

**FR-INST-05** (SHOULD, P3) — The leader SHOULD be able to set the key for the gathering, so that all musician views transpose consistently.

**FR-INST-06** (MUST, P3) — Notation and chords MUST be optional content; singers in the plain lyric view are not affected.

**FR-INST-07** (MAY, P3) — The app MAY render notation in day and night theme and MAY offer a landscape "lead sheet" layout for tablets.

---

## 16. Non-functional requirements

**NFR-PERF-01** (MUST, P1) — With 25 singers within 10 m in open air, a pushed song of up to 4 kB that singers do not have locally MUST be visible on all singer devices within 5 seconds; a song that singers have locally (FR-BLE-12) within 2 seconds.

**NFR-PERF-02** (MUST, P1) — The leader device MUST be able to run a gathering for at least 4 hours on a full battery of a mid-range phone, with the screen mostly on in night theme at low brightness. The singer app MUST use less battery than the leader app.

**NFR-PERF-03** (MUST, P1) — Browsing and searching a library of 2,000 songs MUST feel instantaneous (results within 300 ms).

**NFR-REL-01** (MUST, P1) — A singer device moving out of range and back MUST recover automatically (FR-GATH-10) without user action in at least 95% of cases in field testing.

**NFR-SEC-01** (MUST, P1) — Library downloads MUST use HTTPS with certificate validation. The app MUST refuse an update whose repository data is corrupt or does not parse.

**NFR-SEC-02** (MUST, P1) — Data sent over BLE MUST NOT allow a third device to impersonate the leader of a running gathering (e.g. by using the gathering identifier and a session token established at join). Encryption of lyrics in transit is not required, as lyrics are not confidential.

**NFR-PRIV-01** (MUST, P1) — The app MUST NOT collect or transmit personal data. The display name is the only user-entered identity and is only exchanged over BLE within the gathering.

**NFR-ACC-01** (SHOULD, P1) — The app SHOULD be usable with the platform screen reader for navigation, and all controls SHOULD have a minimum touch target of 48 dp for use in the dark.

**NFR-ACC-02** (MUST, P1) — The lyric view MUST honour the platform's system font scaling in addition to its own text size setting.

**NFR-PORT-01** (MUST, P1) — Business logic (library handling, song parsing, gathering state, protocol) MUST be separated from Android-specific UI and BLE code so that an iOS implementation can reuse the protocol and format specifications and, where the chosen technology allows, the code.

**NFR-MAINT-01** (MUST, P1) — The song format, library metadata format and BLE protocol MUST each have a written specification in `design/` and a version number; the app MUST state which versions it supports.

**NFR-TEST-01** (SHOULD, P1) — The gathering logic SHOULD be testable without physical BLE hardware (simulated transport), so that multi-singer scenarios can be tested automatically.

---

## 17. Constraints and assumptions

1. **BLE capability of the leader device.** Not every Android device supports BLE peripheral (advertising) mode; the app must detect this and tell the user that the device can join but not lead a gathering.
2. **BLE connection limits.** Android devices typically allow a limited number of simultaneous BLE connections. Meeting FR-BLE-10 may require connectionless distribution (extended advertising), relaying via singer devices (FR-BLE-11), or a combination. This is a design decision for the BLE protocol document, not for this FRD.
3. **Platform background limits.** Android and especially iOS restrict BLE activity when the app is in the background. The leader's device is expected to keep the app in the foreground; the app warns when this is not the case (FR-GATH-15).
4. **Permissions.** BLE scanning on Android requires location or "nearby devices" permissions depending on the Android version. The app must explain this (FR-GEN-05); the app never uses actual location data.
5. **Lyrics licensing is a library responsibility.** The app displays whatever the library provides along with its licence metadata; the maintainers of a library are responsible for having the right to distribute its contents.
6. **Library imported by singers persists.** It is assumed that FR-DATA-01 (discarding lyrics at the end of a gathering) applies only to lyrics received over BLE, and not to libraries the user imported themselves. If the intention is to discard **all** lyrics on singer devices, FR-DATA-02 must be changed.
7. **Song identity across libraries.** Song identifiers are unique within a library; the pair (library identifier, song identifier) is unique globally.
8. **No audio playback in P1.** Singing is acoustic; the app does not play music or record audio in P1.
9. **One gathering per area is enforced at start only.** FR-GATH-16 prevents a second gathering from being started within range of a running one. Two gatherings started out of each other's range that later overlap cannot be prevented; the app only warns (FR-GATH-17), and singers choose by name (FR-GATH-04).
10. **The leader's device is the only authority.** Delegates act through the leader's device (FR-LEAD-43). A delegate cannot keep a gathering going when the leader's device is gone; FR-GATH-14 (hand-over) covers that case.

---

## 18. Open questions

| # | Question | Impact |
|---|---|---|
| Q1 | What is the realistic maximum number of singers per gathering (FR-BLE-10)? 25, 50, 100? This drives the BLE distribution design. | BLE protocol design |
| Q2 | Must singers be able to see upcoming songs in the setlist, or only what has been sung? (FR-SING-03 assumes the leader can choose.) | Leader UI |
| Q3 | Should the default library be a repository under the project's own control, and who maintains it? | Library governance, FR-LIB-02 |
| Q4 | Do we adopt ChordPro as the song format, or define our own Markdown/YAML-based format? | Song format spec, P3 |
| Q5 | Is leader hand-over (FR-GATH-14) needed in P1, e.g. when the leader's battery runs out? | Scope P1 |
| Q6 | Should the gathering memory also be kept on the leader device, including who requested what? (Current assumption: titles only, no names.) | Privacy, FR-DATA-03 |
| Q7 | Minimum Android version and whether a tablet layout is in scope for P1. | Architecture |
| Q8 | Which languages beyond NL and EN are planned, and do we need right-to-left support? | Localisation |
| Q9 | Should FR-GATH-16 allow an override when the gathering in range is clearly abandoned (e.g. a forgotten phone still advertising), or is "ask that leader to end it" always acceptable? | FR-GATH-16, leader UI |
| Q10 | Should singers see who pushed a song or handled their request (leader or which delegate), or is this detail only for the leader? | FR-LEAD-45, setlist display |

---

## 19. Related documents (to be written)

| Document | Content |
|---|---|
| `design/architecture.md` | App architecture, technology choices, module boundaries, Android/iOS strategy. |
| `design/ble-protocol.md` | Beacon format, join procedure, message types, chunking and compression, versioning, relay strategy. |
| `design/song-format.md` | Song file and library metadata format, metadata fields, timing (P2) and notation (P3) extensions. |
| `design/ui-flows.md` | Screen flows for leader and singer, day/night theme guidelines. |
| `design/test-plan.md` | Field test scenarios (range, number of singers, battery), automated test strategy. |

---

## 20. Requirement summary by phase

| Area | P1 (MUST) | P1 (SHOULD/MAY) | P2 | P3 |
|---|---|---|---|---|
| General (GEN) | 01–05, 07 | 06 | | |
| Language (LANG) | 01–05 | 06, 07 | | |
| Library (LIB) | 01–07, 10–12, 14, 15, 17, 20, 21, 23, 30, 31, 33 | 13, 16, 22, 32 | | |
| Gathering (GATH) | 01–04, 06–12, 15, 16 | 05, 13, 14, 17 | | |
| BLE (BLE) | 01–08, 10, 12–16 | 11 | | |
| Leader (LEAD) | 01–04, 06, 10, 11, 30, 32, 40–45 | 05, 07, 12, 13, 31, 46 | | |
| Singer (SING) | 01–04, 10, 11, 13–15 | 12 | | |
| Display (DISP) | 01, 03–07, 09, 10 | 02, 08, 11 | | |
| Requests (REQ) | 01–03, 05 | 04, 06, 07 | | |
| Network (NET) | 01–03 | 04 | | |
| Data (DATA) | 01–04, 06, 07 | 05 | | |
| Karaoke (KAR) | | | 01–09 | |
| Instruments (INST) | | | | 01–07 |
