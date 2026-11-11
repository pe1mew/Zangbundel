# Release checks 0.5.0 – 2026-11-11

Test plan section 6 for version 0.5.0 (versionCode 2026111101), on the working tree of `main` at `0a11056` plus the version change, before it is committed. Compared with the 0.4.0 release build of tag `v0.4.0` (`C:\Users\drasv\build\zangbundel-0.4.0`).

**What changed in the app since 0.4.0** (six source files: `PackageImport`, `SongParser`, `Setlist`, the new `TagLabel` in `core/songformat`; `ReportText` and `SongbookViewModel` in `app`):
- tag labels from a package's manifest, shown in the songbook (song format v0.26 to v0.28, assessment SP-40);
- package checks as the library manager's shared test libraries say (59 cases; the 9 differences the owner approved on 2026-11-06 remain), with the FDS codes in a package's song errors;
- stricter reading of songs and setlists (song format v0.23 to v0.25): C1 control characters refused, an escaped closing brace malformed, indented directives, `{meta: …}` split at spaces or tabs. The line list of a song is unchanged from 0.4.0.

No resource, manifest, dependency, database schema, protocol, BLE, crypto, storage or network code changed (`git diff v0.4.0 --stat` on those paths is empty).

| Check | How | Result |
|---|---|---|
| **G-01** Permissions | `aapt2 dump permissions` and `dump xmltree AndroidManifest.xml` (build-tools 36.0.0) of the 0.4.0 and 0.5.0 release APKs, compared | **Pass.** The same 14 permissions. The manifests differ only in `versionCode` and `versionName`. Not debuggable. |
| **G-02** Backup | `git diff v0.4.0` on the backup and data-extraction rules and the manifest | **Pass.** No change; the database schema is still version 6. |
| **G-03** Network during a campfire | `git diff v0.4.0 --stat` on `core/protocol`, `platform/ble`, `platform/crypto` and the gathering code | **Pass, carried over from 0.4.0:** none of it changed. |
| **G-04** Network during import | Packet capture | **Not run:** no capture app on the phones. By code: `platform/net` and the library import's network code are unchanged since 0.4.0. Open with D-10's capture. |
| **G-05** Logs | `git diff v0.4.0` on every Kotlin source file, for new `Log.`, `println` or `FieldLog.` calls | **Pass:** none added. Not rerun with `adb logcat` on a release build. |
| **G-06** Dependencies | `git diff v0.4.0` on `libs.versions.toml`, `verification-metadata.xml` and every `build.gradle.kts` | **Pass.** No artefact or version changed; `app/build.gradle.kts` changed in `versionCode` and `versionName` only. |
| **G-07** Reproducibility | A fresh clone of the tag, built as for 0.4.0, compared with CI's unsigned APK | **Not yet run:** needs the commit and tag `v0.5.0` (the owner's). |
| **G-08** Signature | `apksigner verify --print-certs` on the GitHub APK and a Play install | **Not yet run:** after the tag and the owner's signing with the app signing key (RUNBOOK Step 9.7). The bundle is signed with the upload key `C0:A3:C6:…:95:17:2A`, as it should be. |
| **G-09** Fuzzing | `scripts/fuzz.sh 3600` on the targets whose parsers changed: `songParser`, `setlistParser`, `packageManifest`, `libraryOpen` | **Running** since 2026-11-11 12:28. |
| **G-10** Assessment | `design/security-privacy-assessment.md` v0.32 | **Pass.** SP-40 is built as resolved; the other parser changes accept less than before. No new permission, network peer or dependency, no data to the developer; the Data safety declaration stays "no data collected". |

**Build.**
- `./gradlew check assembleRelease bundleRelease` passes (2026-11-11, `BUILD SUCCESSFUL in 2m 14s`): every unit test, and lint with missing and extra translations as errors.
- The corpus tests on their own, with `--rerun-tasks`: `ConformanceLibrariesTest` 1, `PackageTest` 17 and `PackageDecisionTest` 5 tests, no failures. `PackageTest` differs from the corpus on 9 of 59 cases, exactly its list of approved differences, and on 0 of 59 for the format 1.1 path. Both SNG-10 cases of FDS D-46, `song-title-duplicate` and `song-title-other-language`, are accepted with W013, as expected.
- `check-design.sh` (187 requirements, FRD v0.26), `check-translation.py all` (one warning on an Italian review-build label, no error) and `store-metadata.sh --check` pass.

**The bundle.**
- File: `C:\Users\drasv\build\zangbundel-0.5.0-2026111101.aab`, 7 693 083 bytes, SHA-256 `97e08b87b895f95267c69e106b92d1af3c50883e9816bae289d35b870856c4c6`, signed with the upload key (`CN=Remko Welling, O=PE1MEW, C=NL`, SHA-256 `C0:A3:C6:52:…:95:17:2A`).
- Version: versionName `0.5.0`, no `-review`.
- Languages: the app's own texts are in English, Dutch and German (`aapt2 dump resources`, for example `home_songbook`: *Songbook*, *Zangbundel*, *Liederbuch*); no French or Italian.

**Open, the owner's:**
- commit, tag `v0.5.0`, then G-07 and G-08 (RUNBOOK Step 9.7);
- upload to internal testing, D-01 to D-08 from the Play install, then the closed test (RUNBOOK Step 9.4 and 9.5);
- push `wiki/` (the manuals now say 0.5.0);
- the German reviewer's check of the German texts and the de-DE store listing, including 0.5.0's release note;
- G-04 and D-10's packet capture, as for 0.4.0.
