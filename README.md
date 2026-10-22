# Zangbundel

Zangbundel ("songbook" in Dutch) is a mobile songbook app for singing together around a campfire, for example at scouting events. One phone is the **leader** (*kampvuurleider*): it starts a gathering, advertises a Bluetooth Low Energy beacon and broadcasts the lyrics of the current song to every **singer** (*zanger*) phone around the fire. No internet, Wi-Fi or hotspot is involved. Lyrics come from external, git-hosted song libraries, so copyright and licences are managed outside the app.

**Status: design phase.** The repository holds the specifications. The Android app has not been started; an iOS version is planned to follow and must interoperate over BLE.

## What the app will do

Phase 1 — songbook over BLE:

- Import and update song libraries from git repositories when online; browse, search and filter them offline.
- Start a gathering as leader, or join one by tapping it in the list of gatherings in range. No system pairing, no join code unless the leader sets one.
- The leader pushes a song; every singer sees it within seconds. Singers with the library locally receive only an identifier; others receive the lyrics over the air.
- Day theme (black on white) and night theme (white on black), portrait and landscape, line-by-line scrolling, adjustable text size, optional "follow the leader".
- Singers send song requests; the leader or a delegate accepts, declines or pushes them.
- Leaving and ending: a singer leaves at any time; the leader ends the gathering for everyone. Lyrics received over the air are discarded; the list of titles sung is kept as a memory of the evening.
- Dutch and English UI, more languages by adding a translation file.

Phase 2 adds karaoke mode (timed, synchronised lyric highlighting). Phase 3 adds chords and notation for instrument players.

## Checking a copy of the app

Copies of the app on Google Play, F-Droid and GitHub Releases are all signed with one key. A copy shared from phone to phone is too. The app's About screen shows the short fingerprint of that key; a genuine copy shows:

`57:86:7B:…:9B:10`

The full SHA-256 is `57:86:7B:3F:C1:4D:1F:35:F6:38:40:A8:41:BB:05:F6:DD:FD:A9:C3:6D:C0:58:29:5F:87:F5:46:A2:C8:9B:10`. If a copy shows anything else, uninstall it and install from a store.

## Repository structure

```
design/
  prompts.md                  the original ask, verbatim and dated
  functional-requirements.md  what the app must do (FRD) — the canonical spec
  ble-protocol.md             how leader and singers communicate over BLE
  song-format.md              song file format and library repository layout
scripts/
  check-design.sh             cross-document consistency check
memory/                       working memory for AI coding agents (index + gotcha log)
android/                      the Android app (empty until implementation starts)
CLAUDE.md                     orientation file for AI coding agents
```

## The documents

| Document | Read it when |
|---|---|
| [design/functional-requirements.md](design/functional-requirements.md) | You want to know what is required, in which phase, and what is still open. Requirements are numbered (`FR-AREA-nn`) and never renumbered. |
| [design/ble-protocol.md](design/ble-protocol.md) | You work on anything that goes over the air: beacon, broadcast carousel, GATT service, payloads, security. |
| [design/song-format.md](design/song-format.md) | You write or maintain a song library, or touch the content hash the protocol matches songs by. |
| [design/architecture.md](design/architecture.md) | You are about to write app code: toolkit, modules, run-time structure, storage, dependencies, build and tests. |
| [changelog.md](changelog.md) | You want to know what changed and when. |

Dependency order: requirements → protocol → song format. Change upstream first.

## Getting started

There is nothing to build yet. To work on the specifications:

1. Clone the repository.
2. Read `design/functional-requirements.md` first; the other two documents state which version of it they depend on.
3. After editing a design document, run the consistency check:

   ```bash
   bash scripts/check-design.sh
   ```

   It verifies that requirement IDs are unique, that every cited ID is defined, that section references in the protocol resolve, and that dependency versions match.

If you use an AI coding agent, `CLAUDE.md` is its entry point; it follows the [agent-ready-projects](https://github.com/ducroq/agent-ready-projects) layout.

## Song libraries

This repository contains no lyrics and will not accept any. Songs live in separate library repositories in the format defined by `design/song-format.md`, each with its own licence metadata, which the app shows to users. If you want to contribute songs, contribute to a library, not here.

## Contributing

See [contributing.md](contributing.md) and the [Code of Conduct](code_of_conduct.md). The most useful contributions right now are reviews of the open questions listed at the end of each design document.

## License

See [license.md](license.md) for details.

The code in this repository is licensed under the Apache License 2.0 (see [LICENSE](LICENSE) and [NOTICE](NOTICE)), and the app's graphics (icon, store graphics, background photograph) under CC BY-SA 4.0, so the app can be distributed as free software, including through F-Droid. The documentation and other non-code content are licensed under a Creative Commons Attribution-NonCommercial 4.0 International License (http://creativecommons.org/licenses/by-nc/4.0/) by Remko Welling, PE1MEW (https://github.com/pe1mew).

<a rel="license" href="http://creativecommons.org/licenses/by-nc/4.0/"><img alt="Creative Commons License" style="border-width:0" src="https://i.creativecommons.org/l/by-nc/4.0/88x31.png" /></a><br />This work is licensed under a <a rel="license" href="http://creativecommons.org/licenses/by-nc/4.0/">Creative Commons Attribution-NonCommercial 4.0 International License</a>.

## Disclaimer

This project is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
