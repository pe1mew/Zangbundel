# Release checks 0.3.0 – 2026-11-03

Test plan section 6 for version 0.3.0 (versionCode 2026110301), on the working tree before it is committed: German becomes a language of the app (FRD v0.23 FR-LANG-08), with the fixes of issues #1 to #4. Compared with the 0.2.0 build of tag `v0.2.0` (`C:\Users\drasv\build\zangbundel-0.2.0`, unsigned SHA-256 `d92492d8…a7b65f9aced6`).

**What changed in the app since 0.2.0:** strings, word lists and language lists (`values`, `values-nl`, `values-de`, `languages.xml`, `locales_config.xml`), two code comments, and in `app/build.gradle.kts` the version and the review-release switch (architecture A-23), which is off for this build. **The code is byte-identical:** `classes.dex` and every native library of the 0.3.0 release APK equal those of 0.2.0.

| Check | How | Result |
|---|---|---|
| **G-01** Permissions | `aapt2 dump xmltree AndroidManifest.xml` of both release APKs, compared | **Pass.** The manifests differ only in `versionCode` and `versionName`; the same fourteen permissions as 0.2.0 |
| **G-02** Backup | `git diff 27d03a4` on the manifest and the backup and data-extraction rules | **Pass, carried over:** no change since 0.2.0, where the `bmgr` attempt on a phone passed |
| **G-03** Network during a campfire | Not rerun: no phone connected | **Carried over from 0.2.0:** the code is byte-identical (dex and native libraries) |
| **G-04** Network during import | Not rerun | **Carried over**, as G-03 |
| **G-05** Logs | Not rerun | **Carried over**, as G-03; the new texts are resources, never logged |
| **G-06** Dependencies | `git diff 27d03a4` on `libs.versions.toml`, `verification-metadata.xml` and every `build.gradle.kts` | **Pass.** No dependency changed; `app/build.gradle.kts` changed only in the version and the review switch |
| **G-07** Reproducibility | A fresh clone of tag `v0.3.0` (`acce35a`) with `core.autocrlf=false` in `C:\Users\drasv\build\zangbundel-0.3.0`, `assembleRelease` with `--no-daemon --no-build-cache --no-configuration-cache` and no `keystore.properties`, compared with CI's unsigned APK (run 41696625600, which passed) | **Pass.** Both are 5 820 590 bytes with SHA-256 `8e500785…c32c336b409`, **identical**; the working-tree build is the same apart from its signature |
| **G-08** Signature | `apksigner verify --print-certs` on the GitHub APK and a Play install | **GitHub APK: pass.** The owner signed the tag build with apksigner **34.0.0** and the app signing key, `--v1-signing-enabled false`, and published release https://github.com/pe1mew/Zangbundel/releases/tag/v0.3.0 (not a draft or pre-release). The downloaded `zangbundel-0.3.0.apk` is 5 828 946 bytes, SHA-256 `31b408bb…b4934f56b`; signed v2 and v3 by one signer, `CN=Remko Welling, O=PE1MEW, C=NL`, SHA-256 `57867b3f…a2c89b10`. With the 8 192-byte signing block and its 164 bytes of padding cut out, it is byte-identical to the unsigned build. **Play install: open.** The bundle is signed with the upload key `C0:A3:…:95:17:2A` |
| **G-09** Fuzzing | Parsers unchanged | **Pass, carried over:** no parser code changed (dex identical) |
| **G-10** Assessment | `design/security-privacy-assessment.md` v0.27 | **Pass.** SP-36 covers the review release; German adds no data, permission or network use. The German privacy policy is a new text for the reviewer to check |

Build: `./gradlew check assembleRelease bundleRelease` passes (unit tests, lint with missing and extra translations as errors). `check-translation.py` and its self-test, `check-design.sh` and `store-metadata.sh --check` pass.

The bundle: `C:\Users\drasv\build\zangbundel-0.3.0-2026110301.aab`, 7 565 442 bytes, SHA-256 `50ae6826…d1530d2e3357`; versionName `0.3.0`, no `-review`; it contains the German texts (*Dein Name*, *Horche*, *Lagerfeuer leiten*).

**Open, the owner's:** upload to internal testing and run D-01 to D-08 on two phones, including the language setting with *Deutsch*; G-07 and G-08 after the tag; the GitHub release with the APK signed with the app signing key (RUNBOOK Step 9.7).
