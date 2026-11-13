# Release checks 0.5.1 – 2026-11-12

Test plan section 6 for version 0.5.1 (versionCode 2026111201), on the working tree of `main` at `6bcf20c` (version 0.5.0) plus the 0.5.1 changes, before they are committed. 0.5.1 takes the place of 0.5.0, whose G-09 failed (`2026-11-11-release-checks-0.5.0.md`); 0.5.0 has no tag and no GitHub release. Compared with 0.5.0 at `6bcf20c` and with tag `v0.4.0`.

**What changed in the app since 0.5.0** (three source files in `core/songformat`, one in `app`, one string in each language):
- a chorus repeat that would take a song past 32 768 lines is refused with S013, checked before the lines are copied (SP-41, `design/proposals/2026-11-12-chorus-repeat-bound.md`); every song within the bound keeps its line list;
- every bound in `ZipReader` is compared by subtraction, so ZIP64 offsets and sizes near 2^63 are refused (A001) instead of overflowing (SP-42);
- the report text for S013 (`report_s013` in en, nl, de and the review build's fr and it; `ReportText`).

No manifest, permission, backup rule, dependency, database schema, protocol, BLE, crypto, storage or network code changed (`git diff 6bcf20c --stat` on those paths is empty).

| Check | How | Result |
|---|---|---|
| **G-01** Permissions | `aapt2 dump permissions` and `dump xmltree AndroidManifest.xml` (build-tools 36.0.0) of the 0.4.0 and 0.5.1 release APKs, compared | **Pass.** The same 14 permissions. The manifests differ only in `versionCode` and `versionName`. Not debuggable. |
| **G-02** Backup | `git diff 6bcf20c` on the backup and data-extraction rules and the manifest | **Pass.** No change; the database schema is still version 6. |
| **G-03** Network during a campfire | `git diff v0.4.0 --stat` on `core/protocol`, `platform/ble`, `platform/crypto` and the gathering code | **Pass, carried over from 0.4.0:** none of it changed. A leader's song now goes through the S013 bound when a singer parses it, which can only refuse a song, never send anything. |
| **G-04** Network during import | Packet capture | **Not run:** no capture app on the phones. By code: `platform/net` and the import's network code are unchanged since 0.4.0. Open with D-10's capture. |
| **G-05** Logs | `git diff 6bcf20c` on every Kotlin source file, for new `Log.`, `println` or `FieldLog.` calls | **Pass:** none added. Not rerun with `adb logcat` on a release build. |
| **G-06** Dependencies | `git diff v0.4.0` on `libs.versions.toml`, `verification-metadata.xml` and every `build.gradle.kts` | **Pass.** No artefact or version changed; `app/build.gradle.kts` changed in `versionCode` and `versionName` only. |
| **G-07** Reproducibility | A fresh clone of the tag, built as for 0.4.0, compared with CI's unsigned APK | **Not yet run:** needs the commit and tag `v0.5.1` (the owner's). |
| **G-08** Signature | `apksigner verify --print-certs` on the GitHub APK and a Play install | **Not yet run:** after the tag and the owner's signing with the app signing key (RUNBOOK Step 9.7). The bundle is signed with the upload key `C0:A3:C6:…:95:17:2A`, as it should be. |
| **G-09** Fuzzing | `scripts/fuzz.sh 3600` on every target whose code changed since 0.4.0: `songParser`, `setlistParser`, `packageManifest`, `libraryOpen` (0.5.0's changes and S013) and `archive` (the ZIP reader) | **Pass.** First run started 2026-11-12 19:47, with the S013 fix: `songParser` 3 607 s, `setlistParser` 3 615 s and `packageManifest` 3 607 s passed; `libraryOpen` found an `ArrayIndexOutOfBoundsException` after 1 166 s (ZIP64 bounds overflowing, SP-42). Fixed, and the finding's input is replayed by the regression mode of `check`. Rerun started 23:09 and ended 2026-11-13 01:10: `libraryOpen` 3 606 s and `archive` 3 607 s passed. The other eleven targets read nothing that changed (the protocol targets and `libraryFile`, `releaseIndex`, `archiveManifest`, which do not use `ZipReader`); they replay their regression inputs in `check`. Both findings' inputs are in `src/fuzz/resources/`. |
| **G-10** Assessment | `design/security-privacy-assessment.md` v0.33 | **Pass.** SP-41 and SP-42 are built as resolved, both Medium. Both changes accept less than before. No new permission, network peer or dependency, no data to the developer; the Data safety declaration stays "no data collected". |

**Build.**
- `./gradlew check assembleRelease bundleRelease` passes (2026-11-13, `BUILD SUCCESSFUL in 1m 51s`): every unit test, including the new S013 and ZIP64 tests and the regression replay of both fuzz findings, and lint with missing and extra translations as errors.
- `check-design.sh` (187 requirements, FRD v0.26), `check-translation.py all` (one warning on an Italian review-build label, as for 0.5.0; no error) and `store-metadata.sh --check` pass.

**The bundle.**
- File: `C:\Users\drasv\build\zangbundel-0.5.1-2026111201.aab`, 7 693 664 bytes, SHA-256 `5c29c00aa0d484d4517e4aa05dff086fb17b166cf71b50ab6d6e3bc2a5e90558`, signed with the upload key (`CN=Remko Welling, O=PE1MEW, C=NL`, SHA-256 `C0:A3:C6:52:…:95:17:2A`).
- Version: versionName `0.5.1`, no `-review`.
- Languages: the app's own texts are in English, Dutch and German (`aapt2 dump resources` of the release APK, for example `report_s013` and `home_songbook`: *Songbook*, *Zangbundel*, *Liederbuch*); the only French and Italian entries are AndroidX's own strings.

**Open, the owner's:**
- commit, tag `v0.5.1`, then G-07 and G-08 and the GitHub release with the signed APK (RUNBOOK Step 9.7);
- upload the bundle to Play in place of 0.5.0 with the release notes of `docs/store-listing/` for nl-NL, de-DE and en-GB (the en-US text), then D-01 to D-08 from the Play install and the closed test (RUNBOOK Step 9.4 and 9.5);
- push `wiki/` (the manuals now say 0.5.1);
- pass `design/proposals/2026-11-12-chorus-repeat-bound.md` to the library session: S013's number and code in song format 5.5, 7.2 and 11.3, and the same bound in `validate.py` and the manager;
- the German reviewer's check of the German texts and the de-DE store listing, including 0.5.1's release note;
- G-04 and D-10's packet capture, as for 0.4.0.
