# F-Droid runbook

<!-- Operational principles + how-to for publishing Zangbundel on F-Droid. Loaded via the
     project file's "Before You Start" table. Sibling of docs/RUNBOOK.md (Google Play);
     the two share one app, one application ID and, if Step 3 goes that way, one signing key. -->

Status 2026-10-20: requirements investigated; code licence changed to plain Apache 2.0 and the app artwork to CC BY-SA 4.0, so the app qualifies with no anti-feature label; the background photograph stays out of the app until consent is recorded; store texts and graphics exist for Play (`docs/store-listing/`). No app code exists yet, so nothing can be built or submitted; the steps below are what to do from the first release on. **One step cannot wait for code: the signing decision (Step 3) must be made before the first upload to Google Play.**

## What F-Droid is, in one paragraph

F-Droid is a catalogue of free and open-source Android apps. Its build server compiles every app from the published source code and signs the result, unless the developer's own signed APK can be shown to be byte-identical to F-Droid's build (a *reproducible build*). Apps are added by a merge request to the `fdroiddata` repository on GitLab, which holds one metadata file per app; once merged, new versions are picked up automatically from release tags. There are no fees, accounts, data-safety forms or review queues of the Play kind; there is a human review of licence, buildability and *anti-features*, the labels F-Droid shows on listings that have a property users may object to.

## Principles

- **Free software only, in code and in every dependency.** F-Droid includes only FLOSS, judged by the DFSG, FSF, GNU and OSI definitions. Proprietary libraries such as Google Play Services, Firebase or Crashlytics are not allowed at all, not even with an anti-feature.
- **One build for every channel.** The app has no Google dependency (CLAUDE.md hard constraint), so the same source and the same build configuration serve Play, F-Droid and GitHub Releases. No `play` and `fdroid` flavours unless a future feature forces one.
- **One signature everywhere, if at all possible.** Android only installs an update signed with the same certificate as the installed app. Different signatures per channel mean users cannot move between channels without uninstalling, and an APK shared offline (FRD FR-DIST-05) only updates copies from the same channel. Step 3.
- **Disclose every anti-feature yourself.** Undisclosed anti-features lead to rejection; disclosed ones are only a label.
- **The store texts live in the source repository.** F-Droid reads title, descriptions, changelogs and graphics from `fastlane/metadata/android/` at the release tag; editing them is a commit, not a form.

## Decisions

| Decision | Value | Status |
|---|---|---|
| Code licence | Apache License 2.0, SPDX `Apache-2.0`; the Commons Clause removed | **Done 2026-10-20** (LICENSE, NOTICE, license.md) |
| Application ID | `nl.pe1mew.zangbundel.app`, the same as on Play | Fixed (RUNBOOK Decisions). F-Droid rejects an ID that copies someone else's domain; `pe1mew.nl` is the owner's. |
| Signing | Option B below (owner's own app signing key on Play and for F-Droid's reproducible build) recommended | **Open — decide before the first Play upload** (Step 3) |
| Artwork licence | App icon, launcher layers and store graphics under CC BY-SA 4.0 → no *NonFreeAssets* label | **Done 2026-10-20** (license.md) |
| Background photograph | Stays out of the app in every channel until the photographer's permission and the parents' consent are recorded in `design/gui/README.md`; screens use the flat background meanwhile | **Decided 2026-10-20** (Step 4) |
| Anti-features | None | Holds while the app ships only the CC BY-SA artwork and no tracking (Step 4) |
| Categories | `Lyrics`, `Multimedia` (from fdroiddata `config/categories.yml`) | Proposed |
| Release tags | `v<versionName>`, e.g. `v1.0.0`, on the commit the release is built from | Proposed; matches RUNBOOK Step 9 |
| Store metadata layout | `fastlane/metadata/android/<locale>/` at the repository root, locales `nl-NL` and `en-US` | Proposed |

## Requirements check

What F-Droid requires (sources at the end), and where Zangbundel stands. "Design" means the specifications commit to it; nothing is built yet.

| Requirement | Zangbundel |
|---|---|
| Source code public, under a FLOSS licence, with the licence file in the repository | Public on GitHub; `LICENSE` is Apache 2.0 since 2026-10-20. Before that the Commons Clause made the code non-free and the app ineligible. |
| Every dependency FLOSS, built from source or taken from Maven Central, Google's Maven repository or another trusted repository | Design: libsodium binding, CBOR and QR libraries are free. Rule for the future: check every new dependency's licence and source before adding it (Step 2). |
| No proprietary tracking or advertising libraries, no Google Play Services or Firebase | Design: none (CLAUDE.md hard constraint). Play "automatic protection" is already off (RUNBOOK Decisions), so no Play Integrity check is compiled in. |
| A 100 % FLOSS build toolchain (OpenJDK, Gradle, Android SDK) | Standard Android tooling qualifies; do not add proprietary Gradle plugins. |
| Builds from the published source on F-Droid's build server | To prove with the first release (Step 8). |
| No download of executable code without opt-in | Libraries are song text and metadata, not code; the app downloads nothing executable. |
| No undisclosed anti-features | None apply (Step 4): the artwork is CC BY-SA 4.0, so no *NonFreeAssets*; *Tracking* does not apply (statistics are opt-in, off by default, never transmitted by the app, guiDesign D-60); *NonFreeNet* and *TetheredNet* do not apply (libraries come from any HTTPS address or a file the user chooses, FRD FR-LIB-02, FR-LIB-16; nothing in a gathering uses a network). |
| Assets with valid licences; non-commercial licences must allow redistribution | App icon, launcher layers and store graphics are CC BY-SA 4.0, a free licence. The photograph stays out of the app until its rights and consent are recorded. |
| Actively developed, functional, useful, suitable for all users including children | Phase 1 release first; submit only when a gathering works end to end on two phones. |
| Application ID not copying someone else's domain | `nl.pe1mew…` is the owner's. |

## F-Droid setup

### Step 1 — Licence (done 2026-10-20)

- [x] `LICENSE` holds the Apache License 2.0 text, `NOTICE` the copyright line, `license.md` explains the change. The same change was made to the library repository's `scripts/` so both repositories have one code licence.
- [ ] When code lands in `android/`: put an SPDX header (`SPDX-License-Identifier: Apache-2.0`) in new source files. Not required by F-Droid, but it makes the licence of every file obvious to reviewers.
- The documentation and design content stay CC BY-NC 4.0; F-Droid does not look at them because they are not part of the app. The artwork that is part of the app is CC BY-SA 4.0 (Step 4).

### Step 2 — Keep the app free of non-free code

- Before adding any dependency: its licence must be FLOSS (check the SPDX identifier in its POM or repository), it must come from Maven Central or Google's Maven repository (`google()`), and it must not pull in a Google proprietary artifact transitively (`com.google.android.gms`, `com.google.firebase`, `com.google.mlkit`, `com.google.android.play`). `./gradlew :app:dependencies` shows the transitive tree.
- The in-app QR scanner, if it is ever built (FRD FR-GATH-23, MAY), uses a free library such as ZXing, never ML Kit.
- Native libraries (the libsodium binding ships `.so` files) are acceptable from Maven Central when freely licensed; F-Droid's `fdroid scanner` reports prebuilt binaries, so run it before submitting (Step 8) and expect a reviewer question.
- Do not add a proprietary Gradle plugin (for example the Google Services or Crashlytics plugins).

### Step 3 — Signing decision (before the first Google Play upload)

Android installs an update only when it is signed with the same certificate as the installed copy. F-Droid signs with its own per-app key unless it can verify that the developer's APK is a reproducible copy of its own build.

| Option | How | Consequence |
|---|---|---|
| **A. Separate keys** | Play: Google-generated app signing key (as RUNBOOK Step 6 says today). F-Droid: its own key. GitHub Releases: the Play-signed universal APK. | Simplest. Three channels, two signatures: a copy from F-Droid cannot update a copy from Play or GitHub and the other way round; moving means uninstalling, which deletes gathering memories and settings. App Links (RUNBOOK Step 5c) need F-Droid's certificate fingerprint added to `assetlinks.json`. An APK shared offline only updates phones whose copy came from the same channel. |
| **B. One key: the owner's** (recommended) | Play: at the first upload, choose to upload the owner's own app signing key instead of a Google-generated one (Play Console → App signing; Play offers this at setup, verify the wording there). GitHub Releases: an APK built from the release tag and signed with that key. F-Droid: reproducible build with `Binaries` pointing at the GitHub APK and `AllowedAPKSigningKeys` set to the key's SHA-256; F-Droid then publishes the owner-signed APK. | One signature in every channel: updates work across Play, F-Droid, GitHub and offline sharing; `assetlinks.json` lists one fingerprint. Costs: the build must be reproducible (Step 7), and the owner holds the only copy of the signing key, so its loss would end updates in every channel. Keep it in the password manager and an offline backup, never in the repository (CLAUDE.md hard constraint). |
| **C. F-Droid only signs, Play later** | Publish on F-Droid first with option A, decide Play later. | Postpones the problem; the first Play upload still fixes the Play key for good. |

- [ ] Owner decides A or B and records it here and in RUNBOOK Decisions before RUNBOOK Step 6.
- With B, the RUNBOOK Step 6 keystore becomes the **app signing key** rather than only an upload key; Play still asks for a separate upload key, which may be a second keystore.

### Step 4 — Assets and anti-features

- **Icon, launcher layers, feature graphic** (`design/icon/`, `docs/store-listing/graphics/`): made for the project by the owner and licensed **CC BY-SA 4.0** since 2026-10-20 (`license.md`, section "App artwork"). CC BY-SA is a free licence, so F-Droid needs no *NonFreeAssets* label. Copies in the Android project and under `fastlane/metadata/android/` carry the same licence; credit "Remko Welling, PE1MEW" on the About screen next to the code licence (FRD FR-GEN-07).
- **Any new artwork that ships** (screenshots excepted, which show the app itself) must be CC BY-SA 4.0 or another free licence before it goes into the app or the store metadata; anything under CC BY-NC, CC BY-ND or without a recorded licence would bring the *NonFreeAssets* label back. Add the file to the table in `license.md`.
- **Background photograph** (`design/gui/backdrop-*.webp`): **stays out of the app in every channel** until the photographer's permission and the parents' consent are recorded in `design/gui/README.md` (decided 2026-10-20). The configuration screens use the flat background meanwhile, which D-08 already allows (every screen reads the same without the image). When consent is recorded, decide the photograph's licence before it ships: under CC BY-NC it would bring back the *NonFreeAssets* label; under a free licence anyone could reuse a picture of children, which privacy by design argues against. A free illustration in the icon's style is the alternative that avoids both.
- **The default library** (FRD FR-LIB-02) is CC BY-NC song content downloaded at run time, not part of the APK. F-Droid's anti-features do not cover content a user downloads; a reviewer may still ask. Answer: the app ships no content, libraries are configured by the user and can come from any HTTPS address or a file.
- **Statistics** (guiDesign D-60): opt-in, off by default, never sent by the app → no *Tracking* label. If that ever changes, declare *Tracking*.
- Declare in the metadata file (Step 7) exactly what applies; mention the reason in the description.

### Step 5 — Store metadata in the source repository

When `android/` exists, add at the **repository root** (F-Droid does not read it from inside the Gradle module):

```
fastlane/metadata/android/
  en-US/                         fallback locale for F-Droid
    title.txt                    ≤ 50 characters: Zangbundel
    short_description.txt        ≤ 80 characters
    full_description.txt         ≤ 4000 characters
    changelogs/<versionCode>.txt ≤ 500 characters, e.g. changelogs/2026110101.txt
    images/icon.png              512 × 512
    images/featureGraphic.png    1024 × 500
    images/phoneScreenshots/1.png, 2.png, …
  nl-NL/                         the same files in Dutch
```

- [ ] Copy title and descriptions from `docs/store-listing/en-US.md` and `nl-NL.md`; keep one source of truth (either move the Play texts here and let Play be pasted from these files, or keep both in step in the same commit).
- [ ] Copy `docs/store-listing/graphics/icon-512.png` and `feature-1024x500.png`.
- [ ] One changelog file per release, named after the `versionCode` without padding, in both locales (guiDesign D-80: a new text goes into every supported language).
- F-Droid strips image metadata and may recompress images; PNG or JPEG only.

### Step 6 — Versions and release tags

- `versionName` and `versionCode` as RUNBOOK Decisions fixes them (`1.0.0`, `YYYYMMDDnn`), declared literally in `android/app/build.gradle(.kts)` so `fdroid checkupdates` can read them.
- Every release commit gets the tag `v<versionName>` (`v1.0.0`); F-Droid's update check matches tags with `^v[0-9.]+$`. Tag only commits that are releases.
- With option B, the GitHub Release for the tag carries the owner-signed APK named `zangbundel-<versionName>.apk`, which the `Binaries` URL in Step 7 points at.

### Step 7 — Reproducible build (option B only)

- Pin everything that goes into the APK: Gradle wrapper version (commit `gradlew` and `gradle/wrapper/`), Android Gradle Plugin, Kotlin, build-tools, JDK major version, and every dependency version (also FRD NFR-SEC-05).
- Remove sources of difference: no build timestamps or machine paths in `BuildConfig`; disable embedded version-control info if the Android Gradle Plugin adds it; no baseline-profile or resource steps that differ per machine.
- Sign release APKs with `apksigner` (F-Droid copies the signature with the same algorithm).
- Check locally: build twice on two machines (or once locally and once in the fdroidserver container, Step 8), compare the unsigned APKs with `diffoscope`, fix every difference before the first submission.

### Step 8 — The metadata file and a local test

The file goes into a fork of `https://gitlab.com/fdroid/fdroiddata` as `metadata/nl.pe1mew.zangbundel.app.yml` (the file name must equal the application ID). Starting point, to be completed at the first release:

```yaml
Categories:
  - Lyrics
  - Multimedia
License: Apache-2.0
AuthorName: Remko Welling
AuthorEmail: zangbundel@rfsee.nl
WebSite: https://github.com/pe1mew/Zangbundel
SourceCode: https://github.com/pe1mew/Zangbundel
IssueTracker: https://github.com/pe1mew/Zangbundel/issues
Changelog: https://github.com/pe1mew/Zangbundel/blob/main/changelog.md

AutoName: Zangbundel

RepoType: git
Repo: https://github.com/pe1mew/Zangbundel.git
# Option B only: the owner-signed APK of each release, and that key's SHA-256.
Binaries: https://github.com/pe1mew/Zangbundel/releases/download/v%v/zangbundel-%v.apk

Builds:
  - versionName: 1.0.0
    versionCode: 2026110101
    commit: <full 40-character hash of the v1.0.0 commit>
    subdir: android/app
    gradle:
      - yes

AllowedAPKSigningKeys: <lowercase hex SHA-256 of the app signing certificate>
AutoUpdateMode: Version
UpdateCheckMode: Tags ^v[0-9.]+$
CurrentVersion: 1.0.0
CurrentVersionCode: 2026110101
```

- No `AntiFeatures` field: none applies (Step 4). If one ever does, add it with the reason; `fdroid rewritemeta` brings it into the format the current fdroidserver expects.
- Remove `Binaries` and `AllowedAPKSigningKeys` under option A.
- The version and code above are placeholders; fill them from the real first release.

Test in the fdroidserver build container, which the F-Droid Quick Start Guide describes how to start (Git, Docker and fdroidserver needed). From `/build` in the container:

```bash
fdroid readmeta
```

```bash
fdroid rewritemeta nl.pe1mew.zangbundel.app
```

```bash
fdroid lint nl.pe1mew.zangbundel.app
```

```bash
fdroid checkupdates --allow-dirty nl.pe1mew.zangbundel.app
```

```bash
fdroid build nl.pe1mew.zangbundel.app
```

```bash
fdroid scanner nl.pe1mew.zangbundel.app
```

Fix the YAML or the app until all of them pass.

### Step 9 — Merge request and review

1. Commit the metadata file on a branch named after the application ID, with the message `New App: nl.pe1mew.zangbundel.app`, and push it to your fork.
2. Open a merge request against `fdroiddata`; watch its CI pipeline and answer reviewers promptly. Expect questions about the licence history (the Commons Clause before 2026-10-20), the libsodium native libraries, and any anti-feature.
3. After the merge, the build server builds and publishes in batches; the app appears in the main repository roughly 24 to 48 hours later.
4. Alternative without writing the metadata: a request for packaging in `https://gitlab.com/fdroid/rfp`. Slower and less in our control; the merge request is preferred.

### Step 10 — Updates

- With `AutoUpdateMode: Version` and `UpdateCheckMode: Tags`, F-Droid finds a new `v…` tag by itself and opens the update; no merge request needed. Releasing is therefore: bump versions, add both changelog files, tag, push the tag (and with option B, attach the signed APK to the GitHub Release under the exact `Binaries` name).
- An update that fails to build or (option B) does not reproduce is skipped; F-Droid users then stay on the previous version. Check the app's build log on F-Droid's monitor after every release.

### After inclusion

- [ ] Add the F-Droid link next to the Play link on the join page `docs/j/index.html` and in the README (FRD FR-DIST-01).
- [ ] Option A: add F-Droid's certificate SHA-256 for this app to `assetlinks.json` (RUNBOOK Step 5c) so App Links verify on F-Droid copies too.
- [ ] Mention F-Droid in the privacy policy's "where to get the app" text if it names stores.

## Common problems

| Symptom | Cause | Fix |
|---|---|---|
| Reviewer: "licence is not free" | An old tag or file still carries the Commons Clause | Releases start after 2026-10-20; point to `license.md`'s history paragraph. |
| `fdroid scanner` reports a non-free or binary dependency | A Google proprietary artifact, often pulled in transitively | `./gradlew :app:dependencies`; exclude or replace it (Step 2). |
| Build on F-Droid differs from the GitHub APK (option B) | Unpinned versions, timestamps, version-control info, different JDK | `diffoscope` on both unsigned APKs; pin and remove the difference (Step 7). |
| Users report "App not installed" when updating | Installed copy and update come from channels with different signatures | Option B, or tell users to stay with one channel; an uninstall deletes local data. |
| New version not picked up | Tag does not match `^v[0-9.]+$`, or the version is not readable in the Gradle file | Tag `v<versionName>`; keep `versionName`/`versionCode` literal in the Gradle file. |

## Sources (2026-10-20)

- F-Droid Inclusion Policy: https://f-droid.org/docs/Inclusion_Policy/
- F-Droid Anti-Features: https://f-droid.org/docs/Anti-Features/
- Submitting to F-Droid, Quick Start Guide: https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/
- Build Metadata Reference: https://f-droid.org/docs/Build_Metadata_Reference/
- Reproducible Builds: https://f-droid.org/docs/Reproducible_Builds/
- All About Descriptions, Graphics and Screenshots: https://f-droid.org/docs/All_About_Descriptions_Graphics_and_Screenshots/
- Category list: https://gitlab.com/fdroid/fdroiddata/-/blob/master/config/categories.yml
- OSI on the Commons Clause: https://opensource.org/blog/modified-agplv3-removes-freedoms-adds-legal-headaches
- FSF (Stallman) on the Commons Clause: https://itwire.com/open-source/stallman-says-commons-clause-is-a-non-free-licence
