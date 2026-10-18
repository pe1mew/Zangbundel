---
stack: Markdown design specifications (design phase); Android app planned (android/ is empty), iOS later; BLE; git-hosted song libraries in a ChordPro profile
status: Prototype — design phase, no application code yet
repo: github.com/pe1mew/Zangbundel
framework: agent-ready-projects v1.49.2
---

# Zangbundel

<!-- Canonical project file for Claude Code. No AGENTS.md exists; if one is
     added later, this file stays canonical and AGENTS.md should say so. -->

Zangbundel is a campfire songbook app for scouting groups. One phone is the **leader**: it starts a gathering, advertises a BLE beacon and broadcasts the lyrics of the current song to every **singer** phone around the fire, with no internet involved. Lyrics come from external git-hosted libraries so copyright stays outside the app. Phase 1 is the songbook over BLE, phase 2 karaoke, phase 3 chords and notation. The repository currently holds the specifications only; the Android app has not been started.

## Before You Start

| When | Read |
|------|------|
| Picking up where the last session left off | `memory/MEMORY.md` — **the index itself, not the topic files it lists**; those stay on demand, or Layer 3 collapses back into always-loaded context. Nothing loads this file on its own: since ADR-001 it sits below the cliff, so if this row is missing it is simply never read. Keep it near the top — an open handoff is worthless one session late. |
| Starting any session (framework drift) | Compare the `framework: agent-ready-projects vX.Y.Z` line in this file's header against https://github.com/ducroq/agent-ready-projects/blob/master/CHANGELOG.md (or local clone if present). If the project is behind the latest released version, briefly surface the drift to the user before starting work. `/update-drift`, or `templates/update-drift.md` <!-- placeholder --> pasted as a prompt, does this triage. Don't auto-update — adopting changes is the engineer's call. |
| Changing what the app must do, or checking whether something is required | `design/functional-requirements.md` — numbered FR/NFR requirements with MUST/SHOULD/MAY and phase tags, assumptions, open questions. Section 1.3 has the ID rules. |
| Working on the gathering, the beacon, lyric distribution, requests, delegates or anything on the air | `design/ble-protocol.md` — broadcast carousel, frame formats, GATT service, CBOR payloads, procedures, security. Its header names the FRD version it depends on. |
| Working on song files, library repositories, metadata, the content hash or the line list | `design/song-format.md` — the ChordPro profile, library layout, canonicalisation rules the protocol hashes against. |
| Wondering why a requirement or feature exists | `design/prompts.md` — the original ask, verbatim, dated. |
| Adding or changing anything that touches personal data, a permission, the network, or bytes received over the air | `design/security-privacy-assessment.md` — the principles made concrete, findings SP-nn, accepted risks; add a finding or re-run the assessment. |
| Changing deployment or infra, or anything about Google Play (account, tracks, signing, listing, releases) | `docs/RUNBOOK.md` — operational how-to; the fixed Play decisions (application ID, SDK levels, version scheme) live there. |
| Stuck or debugging something weird | `memory/gotcha-log.md` — problem-fix archive |
| **Before committing** | Diff-driven review, lenses chosen by what changed — `/review-changes` where your tool has skills, otherwise paste `templates/review-changes.md` <!-- placeholder --> as a prompt. This row is what fires it: nothing else prompts either party mid-flow. ⚠️ Since v1.40.0 it needs a per-repo **review profile** at `.claude/review-profile.md` — the same path whatever your tool, per `templates/README.md` <!-- placeholder -->. Copy `templates/review-profile.md` <!-- placeholder --> and fill it in; without one it STOPS rather than reviewing at LOW. |
| Ending a session | `memory/gotcha-log.md` — review, promote patterns, retire stale entries. Then `/curate`, or paste `templates/curate.md` <!-- placeholder -->. |
| Periodic — monthly, after restructuring, and when cutting a release | `/audit-context` (structural health across the layers), or paste `templates/audit-context.md` <!-- placeholder -->. No `/release` skill is installed: the project cuts no versioned releases yet. |

## Hard Constraints

- **Lead with the point; no narrative.** Each entry is a fact or a rule in a line or two. How it got that way goes in the changelog, not here: this file is loaded every session, and narrative is what grows it.
- **Never commit, push, merge or rebase.** Git is manual and the owner's. Stage the files, write a suggested commit message, then stop — also when asked to "save" or "finalise".
- **The design documents are the specification.** Requirement IDs (`FR-AREA-nn`, `NFR-AREA-nn`) are never reused; a withdrawn requirement is marked *Withdrawn* and kept. Every ID cited in any document must be defined in the FRD. `scripts/check-design.sh` enforces this; run it before staging a design change.
- **Keep the documents in step.** A change to the FRD bumps its version and adds a change-history row. `design/ble-protocol.md` and `design/song-format.md` state the FRD version they depend on; update them in the same change or say explicitly that they are behind.
- **English prose, Dutch UI terms in parentheses** (*kampvuurleider*, *zanger*, *verzoeknummer*). Requirement key words follow RFC 2119.
- **Never add lyrics to this repository.** Lyrics live in external libraries with their own licences; the app ships none. Example songs in `design/song-format.md` must be public domain or written for the spec.
- **Secure by design, privacy by design; users are under 18.** Nothing personal leaves a phone unencrypted or reaches the developer; defaults never identify a person; every input from the air or a library is untrusted; least privilege. FRD section 2.6 defines it, `design/security-privacy-assessment.md` assesses it. A feature that touches personal data, a permission, the network or received bytes gets a new assessment entry before it is designed in.
- **Everything in a gathering works over BLE with no internet.** Do not design or implement a gathering feature that needs a network, a hotspot or system Bluetooth pairing (FR-BLE-01, FR-BLE-02).
- **Keystores and passwords never enter the repository.** `.gitignore` covers `*.jks`, `*.keystore`, `keystore.properties`; the upload key lives in the password manager (RUNBOOK Step 6). Never weaken these ignore rules.
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
│   └── song-format.md            ← song file + library spec (depends on FRD and protocol)
├── scripts/check-design.sh       ← cross-document consistency check
├── docs/
│   ├── RUNBOOK.md                ← Google Play setup, signing, tracks, release flow
│   └── privacy-policy.md         ← published for the Play listing (EN + NL)
├── memory/                       ← MEMORY.md index + gotcha-log.md (committed, shared)
├── .claude/review-profile.md     ← per-repo half of /review-changes
├── README.md, changelog.md, contributing.md, code_of_conduct.md, license.md, LICENSE
└── android/                      ← empty; the app goes here
```

Dependency order among the specs: FRD → protocol → song format. Change upstream first.

## Key Paths

| Path | What it is |
|------|-----------|
| `design/functional-requirements.md` | The FRD. Numbered requirements over 20 sections plus Appendix A, phases P1–P3, assumptions, open questions. `check-design.sh` prints the current count. |
| `design/ble-protocol.md` | BLE protocol 1.0 (doc v0.2): beacon, carousel, GATT, CBOR payloads, procedures, traceability table. |
| `design/song-format.md` | Song file and library specification: ChordPro profile, metadata, canonical form, content hash, line list. |
| `design/prompts.md` | Dated record of the prompts that produced the specs. |
| `design/security-privacy-assessment.md` | Secure-by-design and privacy-by-design principles, assessment of the design, findings and accepted risks. |
| `scripts/check-design.sh` | Checks requirement IDs, cross-references and dependency versions across `design/`. |
| `memory/MEMORY.md` | Memory index: current state, decisions, key paths. |
| `memory/gotcha-log.md` | Problem → root cause → fix journal. |
| `.claude/review-profile.md` | Risk tiers and guarantee surfaces for pre-commit review. |
| `changelog.md` | Keep a Changelog format; every notable change gets a line under `[Unreleased]`. |
| `docs/RUNBOOK.md` | Google Play: fixed decisions, account and app setup, data safety, permissions declarations, signing, tracks and the 12-tester rule, store listing, release flow. |
| `docs/privacy-policy.md` | The privacy policy the Play listing links to. A published surface: wording changes are reviewed at full depth. |
| `license.md`, `LICENSE` | Code: Apache 2.0 with Commons Clause. Content: CC BY-NC 4.0. Lyrics: not in this repo. |

## How to Work Here

```bash
# Consistency check across the design documents (the only test there is today)
bash scripts/check-design.sh

# Stage design changes — never commit
git add design/
```

Every notable change gets a line under `[Unreleased]` in `changelog.md` in the same staging step.

There is no build, test suite or deployment yet. When application code lands in `android/`, add its build and test commands here and re-tier `android/**` in `.claude/review-profile.md`.

## Commit Conventions

Imperative subject, under 72 characters, naming the document and its new version when a spec changes, as in `BLE protocol v0.2: broadcast-first carousel with repair; FRD v0.3 adds FR-BLE-15/16`. The owner commits; an agent only proposes the message.
