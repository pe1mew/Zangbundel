# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/), and this project adheres to [Semantic Versioning](https://semver.org/). The project has no released version yet; the design documents carry their own document versions in their headers.

## [Unreleased]

### Added
- `design/security-privacy-assessment.md` v0.1: secure-by-design and privacy-by-design principles made concrete for an app used by minors, assessment of the design so far, 15 findings (SP-01 to SP-15), accepted risks. FRD v0.6: principles in section 2.6; FR-GATH-01, FR-SING-01, FR-DATA-03 amended (no device-derived default names, nickname guidance, no names in the gathering memory); FR-GEN-08 in-app privacy screen, FR-GATH-18 gathering fingerprint, FR-DATA-08 resume-data deletion, NFR-PRIV-02 no cloud backup of gathering data, NFR-SEC-03 to 05 added; Q6 closed. BLE v0.6 wording fix. Principle added to CLAUDE.md, the runbook and the review profile.
- `docs/RUNBOOK.md`: Google Play preparation. Fixed decisions (application ID `nl.pe1mew.zangbundel.app`, min SDK 26, target SDK 36, free, Music & Audio, 13+), account check, app creation, dashboard tasks with answers derived from the design, data safety, permission declarations for BLE and the foreground service, upload key and Play App Signing, tracks and the 12-tester closed-test rule, store listing assets, release flow, common problems.
- `docs/privacy-policy.md`: draft privacy policy in English and Dutch for the Play listing (no data collected, Bluetooth exchange explained, location permission on old Android explained).
- `.gitignore`: keystores, `keystore.properties`, Android build output, the owner's `raw/` scratch folder; `.claude/` stays tracked.
- `docs/RUNBOOK.md` Step 4 and `design/ble-protocol.md` v0.4 Q14: Google Play's Data safety definitions applied to the protocol (display name and requests count as "collected" unless end-to-end encrypted); recommendation to encrypt those payloads to the leader so "no data collected" is literally true.
- FRD v0.5 (FR-BLE-17) and `design/ble-protocol.md` v0.5 section 8.4: Q14 adopted. Sealed payloads with libsodium sealed boxes: leader X25519 key `ek` in the signed state, singer key `xk` in the Join; Join, Request, Control and the delegate's request queue end-to-end encrypted; write framing byte; `gid` binding; error `0x8D`. Privacy policy and runbook Data safety step updated to "no data collected, no data shared".
- `design/song-format.md` v0.2: section 12, the release archive (`.zbl`): a ZIP built from a tagged library revision with a manifest of revision, version and per-song content hashes; repository snapshots; release index for update checks; build and load procedures; validation codes A001–A103; security limits. `release_index` field in `zangbundel.json`. Later sections renumbered. `design/ble-protocol.md` v0.3: the download instruction's URL may name an archive or index.
- `design/functional-requirements.md` v0.4: getting the app to participants (section 8.5, FR-DIST-01 to 09: QR code with the store link, sharing the link in advance, offline Android-to-Android sharing of the app package) and Appendix A comparing the distribution options investigated. `ble-protocol.md` and `song-format.md` now depend on FRD v0.4; their content is unchanged.
- `design/functional-requirements.md` v0.3: the functional requirements document. 157 numbered requirements over 20 sections, phases P1 (songbook over BLE), P2 (karaoke) and P3 (instruments), assumptions and open questions. v0.2 added the single-gathering rule (FR-GATH-16, 17) and the delegate role (FR-LEAD-40 to 46); v0.3 added broadcast-first delivery (FR-BLE-15, 16).
- `design/ble-protocol.md` v0.2: the BLE protocol design. Discovery beacon, broadcast carousel over legacy and extended advertising, GATT service for registration, requests, delegate commands and repair of missed frames, CBOR payloads, signed state, procedures, performance budget, security considerations, requirements traceability.
- `design/song-format.md` v0.1: song file format (a ChordPro profile), library repository layout, metadata, canonical form, content hash and canonical line list.
- `design/prompts.md`: the original prompt that started the project, verbatim.
- `scripts/check-design.sh`: cross-document consistency check (requirement IDs, citations, section references, dependency versions).
- Agent-ready-projects scaffold v1.49.2: `CLAUDE.md`, `memory/MEMORY.md`, `memory/gotcha-log.md`, `.claude/review-profile.md`.
- Community files: `README.md`, `contributing.md`, `code_of_conduct.md`, `license.md`, `LICENSE`, this changelog.

### Changed
- *Nothing.*

### Fixed
- *Nothing.*

### Removed
- *Nothing.*

<!-- Template for a release entry. Copy it above this comment when a version is cut.

## [x.y.z] - yyyy-mm-dd

### Added
- *Nothing.*

### Changed
- *Nothing.*

### Fixed
- *Nothing.*

### Removed
- *Nothing.*
-->
