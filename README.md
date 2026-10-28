# Zangbundel

Zangbundel ("songbook" in Dutch) is a mobile songbook app for singing together around a campfire, for example at scouting events. One phone is the **leader** (*kampvuurleider*): it starts a campfire, advertises a Bluetooth Low Energy beacon and broadcasts the lyrics of the current song to every **singer** (*zanger*) phone around the fire. No internet, Wi-Fi, hotspot or pairing is involved. Lyrics come from external, git-hosted song libraries, so copyright and licences are managed outside the app.

## Status (28 October 2026)

**Phase 1 is built for Android and in testing; the app is not public yet.**

- Version 0.1.1 runs on Google Play's internal-testing track. The closed test, which Play requires before a public release, has been submitted for review.
- The release checks for permissions, backups, network use, logs, dependencies, reproducible builds, signing and the security and privacy assessment passed in a run before release; they are repeated on the release commit. Every parser of library or over-the-air input has been fuzzed for an hour without a finding.
- F-Droid's build container produces the same APK, byte for byte, as the project's CI and the owner's PC. The app goes to F-Droid together with the first public release.
- Still to do before the public release: field tests on real phones around a real fire (range, group size, battery, recovery), and the 14-day closed test with at least 12 testers.
- An iOS version is planned for later and must interoperate over BLE.

The [changelog](changelog.md) lists what changed and when.

## What the app does

Phase 1, the songbook over BLE:

- **Libraries.** Install song libraries from their release archives when online, update them by hand or with a daily check on Wi-Fi, and browse and search them offline. The default library is [Zangbundel-bibliotheek](https://github.com/pe1mew/Zangbundel-bibliotheek).
- **Setlists.** Prepare a setlist from the songbook or import one; change it during the campfire.
- **Leading.** Start a campfire that is open to everyone nearby, needs a four-digit join code, or can only be joined through its QR code. Push a song and every singer sees it; the songs of the setlist are broadcast ahead, so a push usually only switches the song.
- **Singing.** Join from the list of campfires nearby, with a join code, or by scanning the leader's QR code with the camera app. Read in night or day theme, at five text sizes, fitted to the screen width if wanted, and follow the leader from song to song.
- **Trust.** Every campfire shows two words made from its key; leader and singers compare them aloud.
- **Afterwards.** Received lyrics are deleted when the campfire ends; the list of titles sung stays as a memory. A leader can opt in to anonymous campfire statistics that never leave the phone unless shared by hand.
- **Around it.** Resume after a crash, the app shared to a nearby phone without internet, Dutch and English.

Phase 2 adds song requests, likes, delegates and karaoke (timed lyric highlighting). Phase 3 adds chords and notation for instrument players.

The user manual is on the wiki, in [Dutch](https://github.com/pe1mew/Zangbundel/wiki/Handleiding) and [English](https://github.com/pe1mew/Zangbundel/wiki/Manual). The privacy policy is at [pe1mew.github.io/Zangbundel/privacy](https://pe1mew.github.io/Zangbundel/privacy/).

## Checking a copy of the app

Copies of the app on Google Play, F-Droid and GitHub Releases are all signed with one key. A copy shared from phone to phone is too. The app's About screen shows the short fingerprint of that key; a genuine copy shows:

`57:86:7B:…:9B:10`

The full SHA-256 is `57:86:7B:3F:C1:4D:1F:35:F6:38:40:A8:41:BB:05:F6:DD:FD:A9:C3:6D:C0:58:29:5F:87:F5:46:A2:C8:9B:10`. If a copy shows anything else, uninstall it and install from a store.

## Repository structure

```
design/                       the specification: requirements, protocol, song format, GUI, architecture, tests
  test-results/               one file per test run
  proposals/                  dated change proposals for the owner's approval
android/                      the Android app: Gradle project with core, platform and app modules
docs/                         Play and F-Droid runbooks, privacy policy, store listing, the GitHub Pages join page
fastlane/metadata/android/    store texts and graphics for F-Droid, generated from docs/store-listing/
wiki/                         sources of the GitHub wiki (the user manual)
scripts/                      design consistency check, translation check, fuzzing, store metadata, F-Droid container build
memory/                       working memory for AI coding agents (index, gotcha log, session records)
CLAUDE.md                     orientation file for AI coding agents
```

## The documents

| Document | Read it when |
|---|---|
| [design/functional-requirements.md](design/functional-requirements.md) | You want to know what is required, in which phase, and what is still open. Requirements are numbered (`FR-AREA-nn`) and never renumbered. |
| [design/ble-protocol.md](design/ble-protocol.md) | You work on anything that goes over the air: beacon, broadcast carousel, GATT service, payloads, security. |
| [design/song-format.md](design/song-format.md) | You write or maintain a song library or a setlist, or touch the content hash the protocol matches songs by. |
| [design/guiDesign.md](design/guiDesign.md) | You change a screen, a control, a theme or what a leader or singer sees. Decisions are numbered `D-nn`. |
| [design/architecture.md](design/architecture.md) | You write app code: toolkit, modules, storage, dependencies, build and tests. |
| [design/test-plan.md](design/test-plan.md) | You write tests, prepare a release, or want to help with a field test: what is tested, how, and what counts as a pass. |
| [design/security-privacy-assessment.md](design/security-privacy-assessment.md) | You touch personal data, a permission, the network or bytes received over the air. The users are under 18. |
| [docs/RUNBOOK.md](docs/RUNBOOK.md), [docs/FDROID-RUNBOOK.md](docs/FDROID-RUNBOOK.md) | You release the app on Google Play or F-Droid. |

Dependency order: requirements → protocol → song format → GUI design. Change upstream first.

## Getting started

To build the app you need JDK 17 and an Android SDK with platform 36; the app runs on Android 8.0 (API 26) and later. From `android/`:

```bash
./gradlew check
```

runs the unit tests of every module and lint, and `./gradlew assembleDebug` builds a debug APK; `android/README.md` has the rest. Other checks, from the repository root:

```bash
bash scripts/check-design.sh
```

verifies the design documents: requirement IDs unique, every cited ID defined, section references resolved, dependency versions in step. `bash scripts/fuzz.sh` fuzzes the parsers, and `bash scripts/fdroid-container-test.sh` builds the app as F-Droid does (Docker needed).

If you use an AI coding agent, `CLAUDE.md` is its entry point; it follows the [agent-ready-projects](https://github.com/ducroq/agent-ready-projects) layout.

## Song libraries

This repository contains no lyrics and will not accept any. Songs live in separate library repositories in the format defined by `design/song-format.md`, each with its own licence metadata, which the app shows to users. If you want to contribute songs, contribute to a library, such as [Zangbundel-bibliotheek](https://github.com/pe1mew/Zangbundel-bibliotheek), not here.

## Contributing

See [contributing.md](contributing.md) and the [Code of Conduct](code_of_conduct.md). Translators and reviewers of the Dutch, English and German texts start with [docs/TRANSLATING.md](docs/TRANSLATING.md). The most useful help right now is testing: adults with an Android phone who join the closed test or a field test, and reports of problems through [GitHub issues](https://github.com/pe1mew/Zangbundel/issues) or zangbundel@rfsee.nl.

## License

See [license.md](license.md) for details.

The code in this repository is licensed under the Apache License 2.0 (see [LICENSE](LICENSE) and [NOTICE](NOTICE)), and the app's graphics (icon, store graphics, background photograph) under CC BY-SA 4.0, so the app can be distributed as free software, including through F-Droid. The documentation and other non-code content are licensed under a Creative Commons Attribution-NonCommercial 4.0 International License (http://creativecommons.org/licenses/by-nc/4.0/) by Remko Welling, PE1MEW (https://github.com/pe1mew).

<a rel="license" href="http://creativecommons.org/licenses/by-nc/4.0/"><img alt="Creative Commons License" style="border-width:0" src="https://i.creativecommons.org/l/by-nc/4.0/88x31.png" /></a><br />This work is licensed under a <a rel="license" href="http://creativecommons.org/licenses/by-nc/4.0/">Creative Commons Attribution-NonCommercial 4.0 International License</a>.

## Disclaimer

This project is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
