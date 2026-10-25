---
stack: Android app in Kotlin and Jetpack Compose (android/, Gradle), iOS later; Markdown design specifications; BLE; git-hosted song libraries in a ChordPro profile
status: Prototype — specifications complete for phase 1; app scaffolded, protocol core in progress
repo: github.com/pe1mew/Zangbundel
framework: agent-ready-projects v1.49.2
---

# Zangbundel

<!-- Canonical project file for Claude Code. No AGENTS.md exists; if one is
     added later, this file stays canonical and AGENTS.md should say so. -->

Zangbundel is a campfire songbook app for scouting groups. One phone is the **leader**: it starts a gathering, advertises a BLE beacon and broadcasts the lyrics of the current song to every **singer** phone around the fire, with no internet involved. Lyrics come from external git-hosted libraries so copyright stays outside the app. Phase 1 is the songbook over BLE, phase 2 karaoke, phase 3 chords and notation. The specifications are complete for phase 1. The Android project in `android/` is scaffolded as the architecture prescribes (modules, build, manifest, resources, a first test per core module); the protocol, parsers, storage and screens are being written against it.

## Before You Start

| When | Read |
|------|------|
| Picking up where the last session left off | `memory/MEMORY.md` — **the index itself, not the topic files it lists**; those stay on demand, or Layer 3 collapses back into always-loaded context. Nothing loads this file on its own: since ADR-001 it sits below the cliff, so if this row is missing it is simply never read. Keep it near the top — an open handoff is worthless one session late. |
| Starting any session (framework drift) | Compare the `framework: agent-ready-projects vX.Y.Z` line in this file's header against https://github.com/ducroq/agent-ready-projects/blob/master/CHANGELOG.md (or local clone if present). If the project is behind the latest released version, briefly surface the drift to the user before starting work. `/update-drift`, or `templates/update-drift.md` <!-- placeholder --> pasted as a prompt, does this triage. Don't auto-update — adopting changes is the engineer's call. |
| Changing what the app must do, or checking whether something is required | `design/functional-requirements.md` — numbered FR/NFR requirements with MUST/SHOULD/MAY and phase tags, assumptions, open questions. Section 1.3 has the ID rules. |
| Working on the gathering, the beacon, lyric distribution, requests, delegates or anything on the air | `design/ble-protocol.md` — broadcast carousel, frame formats, GATT service, CBOR payloads, procedures, security. Its header names the FRD version it depends on. |
| Working on song files, library repositories, metadata, the content hash or the line list | `design/song-format.md` — the ChordPro profile, library layout, canonicalisation rules the protocol hashes against, release archive, setlists. |
| Designing or changing a screen, a control, a theme or what a singer or leader sees | `design/guiDesign.md` — numbered decisions D-nn (D-48 ranks above the rest), open questions at the end; `design/gui/` and `design/ble/` hold the assets and diagrams. Grep the FRD for the area's MUSTs before writing a decision. |
| Building, testing, or adding a dependency to the app | `android/README.md` — build and test commands, module table, `keystore.properties`, version catalog rules. |
| Writing or planning app code: toolkit, modules, storage, dependencies, build, tests | `design/architecture.md` — decisions A-nn (all taken 2026-10-22; a change supersedes, never edits), module layout and dependency rules, data lifecycle table, allowed dependency list. |
| Writing tests, preparing a release, or planning a field test | `design/test-plan.md`: test layers with pass criteria, simulation S-nn, device D-nn, field F-nn, release checks G-nn, the field-test consent text, the release gate; results go in `design/test-results/`. |
| Wondering why a requirement or feature exists | `design/prompts.md` — the original ask, verbatim, dated. |
| Adding or changing anything that touches personal data, a permission, the network, or bytes received over the air | `design/security-privacy-assessment.md` — the principles made concrete, findings SP-nn, accepted risks; add a finding or re-run the assessment. |
| Changing deployment or infra, or anything about Google Play (account, tracks, signing, listing, releases) | `docs/RUNBOOK.md` — operational how-to; the fixed Play decisions (application ID, SDK levels, version scheme) live there. |
| Anything about F-Droid, adding a dependency to the app, or choosing the app signing key | `docs/FDROID-RUNBOOK.md` — what F-Droid requires, the signing decision that must precede the first Play upload, anti-features, metadata, submission. |
| Stuck or debugging something weird | `memory/gotcha-log.md` — problem-fix archive |
| **Before committing** | Diff-driven review, lenses chosen by what changed — `/review-changes` where your tool has skills, otherwise paste `templates/review-changes.md` <!-- placeholder --> as a prompt. This row is what fires it: nothing else prompts either party mid-flow. ⚠️ Since v1.40.0 it needs a per-repo **review profile** at `.claude/review-profile.md` — the same path whatever your tool, per `templates/README.md` <!-- placeholder -->. Copy `templates/review-profile.md` <!-- placeholder --> and fill it in; without one it STOPS rather than reviewing at LOW. |
| Ending a session | `memory/gotcha-log.md` — review, promote patterns, retire stale entries. Then `/curate`, or paste `templates/curate.md` <!-- placeholder -->. |
| Periodic — monthly, after restructuring, and when cutting a release | `/audit-context` (structural health across the layers), or paste `templates/audit-context.md` <!-- placeholder -->. No `/release` skill is installed: the project cuts no versioned releases yet. |

## Hard Constraints

- **Lead with the point; no narrative.** Each entry is a fact or a rule in a line or two. How it got that way goes in the changelog, not here: this file is loaded every session, and narrative is what grows it.
- **Never commit, push, merge or rebase.** Git is manual and the owner's. Stage the files, write a suggested commit message, then stop — also when asked to "save" or "finalise".
- **The design documents are the specification.** Requirement IDs (`FR-AREA-nn`, `NFR-AREA-nn`) are never reused; a withdrawn requirement is marked *Withdrawn* and kept. Every ID cited in any document must be defined in the FRD. `scripts/check-design.sh` enforces this; run it before staging a design change.
- **Keep the documents in step.** A change to the FRD bumps its version and adds a change-history row. `design/ble-protocol.md`, `design/song-format.md` and `design/guiDesign.md` state the FRD version they depend on; update them in the same change or say explicitly that they are behind.
- **English prose, Dutch UI terms in parentheses** (*kampvuurleider*, *zanger*, *verzoeknummer*). Requirement key words follow RFC 2119.
- **Never add lyrics to this repository.** Lyrics live in external libraries with their own licences; the app ships none. Example songs in `design/song-format.md` must be public domain or written for the spec.
- **Secure by design, privacy by design; users are under 18.** Nothing personal leaves a phone unencrypted or reaches the developer; defaults never identify a person; every input from the air or a library is untrusted; least privilege. FRD section 2.6 defines it, `design/security-privacy-assessment.md` assesses it. A feature that touches personal data, a permission, the network or received bytes gets a new assessment entry before it is designed in.
- **Everything in a gathering works over BLE with no internet.** Do not design or implement a gathering feature that needs a network, a hotspot or system Bluetooth pairing (FR-BLE-01, FR-BLE-02).
- **Keystores and passwords never enter the repository.** `.gitignore` covers `*.jks`, `*.keystore`, `keystore.properties`; the upload key lives in the password manager (RUNBOOK Step 6). Never weaken these ignore rules.
- **The app stays free software.** Code under Apache 2.0, every dependency free and from Maven Central or Google's Maven repository, and nothing proprietary: no Google Play Services, Firebase, ML Kit, Play Core or Play Integrity. One build serves Google Play and F-Droid (`docs/FDROID-RUNBOOK.md`).
- **The application ID is `nl.pe1mew.zangbundel.app`.** It is permanent once a bundle is uploaded to Play; the Gradle file must say so before any upload. Minimum SDK 26, target SDK 36 (RUNBOOK Decisions).
- **Honesty.** Never claim a check passed without running it, never claim a file exists without reading it, and never describe a design as "verified" when it has only been written. Field tests on real phones are open questions (ble-protocol.md Q2, Q3), not facts.

## Architecture

The product, as specified:

```
 Library repos (git, ChordPro files, licences)      ── imported over HTTPS when online
          │
          ▼
 Leader phone ── BLE beacon + broadcast carousel ──▶ Singer phones (any number)
     ▲   (signed state, current song, setlist…)        │
     └────── short GATT connections ───────────────────┘
             (register, song request, delegate command, repair of missed frames)
```

The repository, today:

```
Zangbundel/
├── CLAUDE.md                     ← this file (Layer 1)
├── design/
│   ├── prompts.md                ← the original ask, verbatim
│   ├── functional-requirements.md  ← FRD: what the app must do (canonical)
│   ├── ble-protocol.md           ← how leader and singers talk (depends on FRD)
│   ├── song-format.md            ← song file, library, release archive and setlist spec (depends on FRD and protocol)
│   ├── guiDesign.md              ← GUI decisions D-nn for every screen (depends on FRD)
│   ├── architecture.md           ← how the app is built: decisions A-nn, modules, storage, build, tests
│   ├── test-plan.md              ← what is tested, how, pass criteria, field tests, release gate
│   ├── security-privacy-assessment.md ← findings SP-nn, accepted risks
│   ├── test-results/             ← one file per test run, YYYY-MM-DD-<id>.md
│   ├── proposals/                ← dated change proposals awaiting the owner's approval
│   ├── gui/                      ← backdrop photograph and exports, singer-mode flowchart (.puml/.svg/.png)
│   ├── ble/                      ← BLE sequence diagrams per scenario (.puml/.svg/.png)
│   ├── icon/                     ← app icon master, launcher layers, feature graphic
│   └── icon-proposals/           ← the six icon proposals and their contact sheet
├── scripts/check-design.sh       ← cross-document consistency check
├── docs/
│   ├── RUNBOOK.md                ← Google Play setup, signing, tracks, release flow
│   ├── FDROID-RUNBOOK.md         ← F-Droid requirements, signing decision, metadata, submission
│   ├── privacy-policy.md         ← published for the Play listing (EN + NL)
│   ├── privacy/, j/              ← GitHub Pages: rendered privacy policy, join-and-install page
│   ├── app-links/                ← assetlinks template and intent filters for App Links
│   └── store-listing/            ← listing texts and graphics for Play
├── memory/                       ← MEMORY.md index, gotcha-log.md, session records (committed, shared)
├── .claude/                      ← review-profile.md, launch.json (preview servers)
├── raw/                          ← owner's scratch folder, ignored by git
├── README.md, changelog.md, contributing.md, code_of_conduct.md, license.md, LICENSE, NOTICE
├── .github/workflows/android.yml ← CI: design check, unit tests, lint, unsigned release APK
└── android/                      ← the Gradle project: core/{model,songformat,protocol,testing}, platform/{ble,crypto,storage,net}, app
```

Dependency order among the specs: FRD → protocol → song format → GUI design. Change upstream first. The song library itself lives in a separate repository, `github.com/pe1mew/Zangbundel-bibliotheek`, with the validator and release tool that implement song-format sections 11 to 13.

## Key Paths

The table of important files and what each holds is in `memory/key-paths.md` (moved there verbatim on 2026-10-25). The design documents are also routed by Before You Start and shown in the tree above.

## How to Work Here

```bash
# Consistency check across the design documents
bash scripts/check-design.sh

# The app (JDK 17 on JAVA_HOME; Android SDK platform 36 in android/local.properties or ANDROID_HOME)
cd android
./gradlew check            # unit tests of every module and lint; missing translations are errors
./gradlew assembleDebug    # debug APK (application id suffix .debug, pseudo-locales on)
./gradlew assembleRelease  # unsigned without keystore.properties; bundleRelease refuses without it

# Stage changes — never commit
git add design/ android/
```

Every notable change gets a line under `[Unreleased]` in `changelog.md` in the same staging step. A new dependency gets its row in `design/architecture.md` section 8 and a regenerated `android/gradle/verification-metadata.xml` (`./gradlew --no-configuration-cache --refresh-dependencies --write-verification-metadata sha256 assembleDebug check assembleRelease`; without `--refresh-dependencies` poms already in the local cache are left out and CI fails) in the same change. Every source file starts with `SPDX-License-Identifier: Apache-2.0`.

## Commit Conventions

Imperative subject, under 72 characters, naming the document and its new version when a spec changes, as in `BLE protocol v0.2: broadcast-first carousel with repair; FRD v0.3 adds FR-BLE-15/16`. The owner commits; an agent only proposes the message.
