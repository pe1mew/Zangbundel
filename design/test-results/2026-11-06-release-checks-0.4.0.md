# Release checks 0.4.0 – 2026-11-06

Test plan section 6 for version 0.4.0 (versionCode 2026110601), on the working tree of `main` at `bf510c3` plus the version change, before it is committed. Compared with the 0.3.0 release build of tag `v0.3.0` (`C:\Users\drasv\build\zangbundel-0.3.0`).

**What changed in the app since 0.3.0:**
- library packages (FRD v0.26 FR-LIB-08, FR-LIB-09; architecture A-26; guiDesign D-87, D-88; assessment SP-39), with database version 6;
- opening a library from other apps (FR-LIB-18, A-24, SP-37);
- the update check on *Over* (FR-GEN-09, A-25, D-86, SP-38);
- the check mark for the name (D-44 as of v0.68);
- texts in Dutch, English and German, and in French and Italian for review builds only.

**The owner's decision on German (2026-11-06):** the new German texts are released while the German reviewer checks them. That includes the privacy screen's internet sentence and the new sentence in the German privacy policy.

| Check | How | Result |
|---|---|---|
| **G-01** Permissions | `aapt2 dump permissions` and `dump xmltree AndroidManifest.xml` of the 0.3.0 and 0.4.0 release APKs, compared | **Pass.** The same 14 permissions. The manifests differ in `versionCode`, `versionName`, a label resource id, and the three intent filters of A-24 (SEND for the library type, ZIP and `octet-stream`; VIEW for the library type; VIEW by name). Not debuggable. |
| **G-02** Backup | `git diff v0.3.0` on the backup and data-extraction rules and the manifest's backup attributes | **Pass.** No change. The new device memory table (`library_memory`) is in the same database, which the rules already exclude from backup and device transfer (A-13). |
| **G-03** Network during a campfire | `git diff v0.3.0 --stat` on `core/protocol`, `platform/ble`, `platform/crypto` and the gathering code | **Pass, carried over from 0.3.0:** none of it changed. The update check is disabled while a campfire runs (D-86). |
| **G-04** Network during import | Packet capture | **Not run:** no capture app on the phones. By code: the one new connection is `AppUpdateCheck`'s GET of `api.github.com/repos/pe1mew/Zangbundel/releases/latest`, started only from the button on *Over*. Library import and update are unchanged. A library from another app is read from the `content:` URI and makes no connection. D-10's capture is open with it. |
| **G-05** Logs | `git diff v0.3.0` on every Kotlin file, for new `Log.`, `println` or `FieldLog.` calls | **Pass:** none added. Not rerun with `adb logcat` on a release build. |
| **G-06** Dependencies | `git diff v0.3.0` on `libs.versions.toml`, `verification-metadata.xml` and every `build.gradle.kts` | **Pass.** No artefact or version changed. `platform/net` gained `testImplementation` of `junit` and `kotlin-test-junit`, which other modules already use and the verification metadata already lists. |
| **G-07** Reproducibility | A fresh clone of `85f6fb7`, the commit tag `v0.4.0` points at, with `core.autocrlf=false` in `C:\Users\drasv\build\zangbundel-0.4.0`; `assembleRelease` with `--no-daemon --no-build-cache --no-configuration-cache` and no `keystore.properties`. Compared with CI's unsigned APK (run 42226383479, which passed) | **Pass.** Both are 5 891 042 bytes with SHA-256 `1f26c52e…a297ce2a`: **identical**. |
| **G-08** Signature | `apksigner verify --print-certs` (build-tools 34.0.0) on the APK downloaded from the GitHub release | **GitHub APK: pass.** The owner signed the build of G-07 with apksigner **34.0.0** and the app signing key, `--v1-signing-enabled false`. Release https://github.com/pe1mew/Zangbundel/releases/tag/v0.4.0, published 2026-11-06 14:14 UTC (not a draft or pre-release), created with `gh` from this session at the owner's request. The downloaded `zangbundel-0.4.0.apk` is 5 902 730 bytes with SHA-256 `2cd16ab0…7dd1204f`. It is signed v2 and v3, not v1, by one signer, `CN=Remko Welling, O=PE1MEW, C=NL`, SHA-256 `57867b3f…a2c89b10`. Its 486 entries and their CRCs equal CI's unsigned APK. Not checked: the signer of a Play install of 0.4.0. |
| **G-09** Fuzzing | `scripts/fuzz.sh 3600` on the new parser targets `packageManifest` and `libraryOpen`; the other targets' parsers are unchanged | See the result below; the CI budget of 60 s per target passed with no findings on 2026-11-06. |
| **G-10** Assessment | `design/security-privacy-assessment.md` v0.30 | **Pass.** SP-37, SP-38 and SP-39 cover what was added. SP-37's conditions are built (content URIs only, never the app's own provider, the picker's cap, no install without the user, cleaned names, other extras ignored, strict dispatch), with the manifest parsers fuzzed. SP-38's D-10 ran in part. No new permission, no data to the developer; the Data safety declaration stays "no data collected". |

**Database migration 5 to 6.** The A52's debug app had been installed with database version 5 and the default library 2026.11.0 before 2026-11-06. It was updated in place to version 6 with these changes, and the library stayed installed and usable, with its songs, *Bijwerken* and *Niet ondertekend* (`design/test-results/2026-11-06-library-packages.md`). The migration is Room's automatic one; `6.json` is in `platform/storage/schemas`.

**Build.**
- `./gradlew check assembleRelease bundleRelease` passes: every unit test, and lint with missing and extra translations as errors.
- `check-design.sh`, `check-translation.py all` and `store-metadata.sh --check` pass.

**The bundle.**
- File: `C:\Users\drasv\build\zangbundel-0.4.0-2026110601.aab`, 7 688 747 bytes, SHA-256 `e10eb54471401cbde5cfdd1dda771dde04a6cb1438624d407bd7258809b4f726`.
- Version: versionName `0.4.0`, no `-review`.
- Languages: the release APK offers `nl`, `en` and `de`, with the new texts in all three (*Zoeken naar een nieuwe versie*, *Nach einer neuen Version suchen*); no French or Italian.

**Also tested before this release:** D-09 on the A52 (`2026-11-06-D-09.md`), D-10 in part (`2026-11-06-D-10.md`), and the package questions and refusals on the A52 (`2026-11-06-library-packages.md`).

**From Play.** The owner uploaded the bundle to internal testing and ran D-01 to D-08 on the S23 from the Play install, reported as done on 2026-11-06; the details were not recorded here. The same day a review release with French and Italian, `0.4.0-review` (versionCode 2026110690, upload key `C0:A3:…`, bundle `zangbundel-0.4.0-review-2026110690.aab` with SHA-256 `17abf0e8…def92409`), went to the internal track only (RUNBOOK, review release). The German store listing (`docs/store-listing/de-DE.md`) was added in Play Console and submitted.

**After the tag.** Tag `v0.4.0` on `85f6fb7`, G-07 and G-08 pass, and the GitHub release carries the APK signed with the app signing key (RUNBOOK Step 9.7). The wiki manual for 0.4.0 is pushed (wiki `44b6a80`).

**Open, the owner's:**
- the German reviewer's check of the new texts and of the de-DE store listing;
- G-04 and D-10's packet capture.
