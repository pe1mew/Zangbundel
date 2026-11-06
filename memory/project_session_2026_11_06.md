# Session record 2026-11-04 to 2026-11-06 (library packages and 0.4.0)

Dated record, not a status board. Current state lives in `memory/MEMORY.md`. The session was compacted; the threads come from the commits, the summary and the release checks.

## The ask

A run of the owner's requests: a reply on the library manager's FDS O-09/O-10 and carrying out the approved proposal; issues for Pepe's proposals (#7 name OK button, library repo #4 BCP 47); the options for #6 and then building it ("single source of truth": no version file); "implement #7"; "do you have enough information to implement the new library format with backwards compatibility?"; finishing before a release, D-09 with the owner, the wiki manual; "prepare 0.4.0 and keep german in the release as it is being reviewed"; "040 is assessed, we need to add german to the appstore"; a review build with French and Italian for internal testing; tag, wiki, GitHub release and the issues.

## Threads

| Thread | Outcome | Commit |
|---|---|---|
| Library packages specified (FRD v0.24 to v0.26, song format v0.20 to v0.22, GUI v0.69 D-87, D-88, A-26, SP-39) | Closed; proposal `2026-11-04-library-package-id-and-file-type.md` approved | `f369a1e`, `48568ca`, `b7dd6c8`, `c492b36` |
| Library packages built (reader, Ed25519 signatures, view checks, W013, device memory, Room 6, Share and Open) | Closed; tested on the A52 (`2026-11-06-library-packages.md`) | `692a45a`, `c5d1d03` |
| #6 update check on *Over* (GitHub `releases/latest`, no version file) | Closed in 0.4.0, issue closed with a comment | `a2332df` |
| #7 check mark for the name | Closed in 0.4.0, issue closed with a comment | `efb8230` |
| Library repo #4 BCP 47 language codes | Closed (README and contributing) | library `259f065` |
| D-09 opening a library from other apps | Closed: Share works from WhatsApp, Chrome, Mijn bestanden; a tap opens Zangbundel in no tested app (`2026-11-06-D-09.md`) | `bf510c3` |
| Wiki manual for 0.4.0 | Closed, pushed by the owner | `bf510c3`, wiki `44b6a80` |
| Release 0.4.0 (2026110601) | Closed apart from G-09 and G-04: on Play, D-01 to D-08 on the S23 (owner-reported), tag `v0.4.0` on `85f6fb7`, G-07 identical to CI (`1f26c52e…`), GitHub release with the app-key APK (`2cd16ab0…`, G-08) | `85f6fb7`, `18719b3` |
| German store listing de-DE (Play and fastlane) | Closed; entered in Play Console and submitted by the owner | `18719b3` |
| Review release 0.4.0-review (2026110690) with French and Italian | Closed: uploaded to the internal track by the owner | — |
| G-09 fuzz at the release budget (`packageManifest`, `libraryOpen`, an hour each) | Open: running at curation time; result to go into `2026-11-06-release-checks-0.4.0.md` | — |
| Message to Pep about the session | Closed, in chat only | — |

## The owner's decisions

- Library packages: the M-71 question for an unsigned copy (SP-39); keep the *Uit* switch, which already existed.
- The update check reads GitHub's latest release, no version file, Play's in-app update API or redirect (#6).
- German new texts and the de-DE listing ship while the German reviewer checks them.
- French and Italian stay review-only, on the internal track.
