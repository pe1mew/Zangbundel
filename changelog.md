# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/), and this project adheres to [Semantic Versioning](https://semver.org/). The project has no released version yet; the design documents carry their own document versions in their headers.

## [Unreleased]

### Added
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
