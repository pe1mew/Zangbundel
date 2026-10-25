# Key Paths

Moved verbatim from `CLAUDE.md` on 2026-10-25 to keep the project file under about 15k characters; the project file's Key Paths section points here. Keep rows in step with the repository.

| Path | What it is |
|------|-----------|
| `design/functional-requirements.md` | The FRD. Numbered requirements over 20 sections plus Appendix A, phases P1–P3, assumptions, open questions. `check-design.sh` prints the current count. |
| `design/ble-protocol.md` | BLE protocol 1.0 (document version in its header): beacon, carousel, GATT, CBOR payloads, procedures, traceability table. |
| `design/song-format.md` | Song file and library specification: ChordPro profile, metadata, canonical form, content hash, line list, release archive (`.zbl`), setlists (`.zbs`). |
| `design/guiDesign.md` | GUI design: numbered decisions D-nn, screen families, singer and leader mode, traceability, open questions. |
| `design/architecture.md` | Architecture: decisions A-nn, module layout under `android/`, runtime structure, data lifecycle, dependency list, build, test layers. Decisions taken by the owner 2026-10-22; open questions Q1–Q5 wait for builds and field tests. |
| `design/test-plan.md` | Test plan: layers and pass criteria, simulation S-01–S-10, fuzz targets, device D-01–D-08, field F-01–F-11, security checks G-01–G-10, consent text, closed test, release gate, traceability. |
| `design/gui/`, `design/ble/` | GUI assets and diagrams; BLE sequence diagrams. Each folder's README has the render command (PlantUML needs `-charset UTF-8` on Java 8). |
| `design/proposals/` | Dated change proposals; the first, `2026-10-25-erasure-coding.md`, is ble-protocol Q21 and FRD Q14. Approved content moves into the specification it changes. |
| `design/prompts.md` | Dated record of the prompts that produced the specs. |
| `design/security-privacy-assessment.md` | Secure-by-design and privacy-by-design principles, assessment of the design, findings and accepted risks. |
| `scripts/check-design.sh` | Checks requirement IDs, cross-references and dependency versions across `design/`. |
| `memory/MEMORY.md` | Memory index: current state, decisions, key paths. |
| `memory/gotcha-log.md` | Problem → root cause → fix journal. |
| `.claude/review-profile.md` | Risk tiers and guarantee surfaces for pre-commit review. |
| `changelog.md` | Keep a Changelog format; every notable change gets a line under `[Unreleased]`. |
| `docs/RUNBOOK.md` | Google Play: fixed decisions, account and app setup, data safety, permissions declarations, signing, tracks and the 12-tester rule, store listing, release flow. |
| `docs/privacy-policy.md` | The privacy policy the Play listing links to. A published surface: wording changes are reviewed at full depth. |
| `license.md`, `LICENSE`, `NOTICE` | Code: Apache 2.0 (Commons Clause removed 2026-10-20 for F-Droid). App graphics (icon, store graphics, background photograph): CC BY-SA 4.0. Other content: CC BY-NC 4.0. Lyrics: not in this repo. |
| `android/README.md` | Build and test commands, module table, where `keystore.properties` goes. |
| `android/gradle/libs.versions.toml` | Every toolchain and dependency version, pinned (A-19); a new dependency needs a row in architecture section 8. `gradle/verification-metadata.xml` holds the checksums. |
| `android/app/build.gradle.kts` | Application ID, `versionCode`/`versionName`, build types, signing from `keystore.properties`, lint rules, bundle splits off. |
| `docs/FDROID-RUNBOOK.md` | F-Droid: requirements check, signing decision, anti-features, in-repo store metadata, fdroiddata metadata file, local test, merge request, updates. |
