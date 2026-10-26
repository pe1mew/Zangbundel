# Session record 2026-10-26 (the app day)

Dated record, not a status board. Current state lives in `memory/MEMORY.md`. Continues `memory/project_session_2026_10_26.md` from the curation commit `db5fa4d`; the session was compacted several times, so the threads come from the commits and the summaries.

## The ask

"Next step" from the library pipeline into the app's screens, then the campfire on the phones, then a run of the owner's requests, each named in the commit it landed in: join by QR code with the camera app (D-04), the join button per mode and then into the drawer, *Zangbundel* for *Liedboek* and the owner's privacy text, songs broadcast continuously ("I expect that the songs and the setlist are continously being broadcasted during the campfire"), the state after a push within a second or two, the static setlist, the leader's send rate, the hidden reception grid, D-82, D-61, D-30, D-69, and finally the owner's order: "Requests and likes is postponed to later phases … Do radio protocol first followed by follow the leader and resume after crash. then the singers toolbar".

## Threads

| Thread | Outcome | Commit |
|---|---|---|
| Library install, online and file import; library screen, default library (FRD v0.14) | Closed | `1de68cb`, `6f18ce6` |
| Library release 2026.10.2 (`min_app_version` 0.1.0) | Closed in `Zangbundel-bibliotheek` (`35e9c07`); no tag 2026.10.2 seen locally | `b6a30f1` |
| Songbook, setlists, import and export (FRD v0.15) | Closed | `393edf4` |
| Campfire in the app: start, join, push, leave, end, memories; help bar and song bar | Closed | `c16f1eb` to `7d7a980` |
| First use, privacy, settings, About | Closed | `9a3c926` |
| Join by QR with the camera app (D-04) and the join page for test builds | Closed: passed through the browser step; the direct route waits for App Link verification | `af7f695`, `98e43f3` |
| Join button per mode, then into the leader's drawer | Closed | `617a8c3`, `aa4afe3` |
| *Zangbundel* in Dutch, the owner's privacy text | Closed | `ec1f95b` |
| Songs ahead, reception grid, carrier by rate (BLE v0.32–v0.33, FRD v0.16) | Closed | `bf5a7f0` |
| State first after *Nu*, static setlist with `cpos` and `sung` (BLE v0.34–v0.35) | Closed | `9c23641` |
| Fixed send rate; reception grid hidden (FRD v0.17) | Closed | `3f32d62` |
| Start needs songs (D-82), leader's drawer (D-61), round buttons and songlist sheet (D-30, D-31, D-57) | Closed | `4796113`, `aa4afe3`, `6b87fba` |
| Closing bar and summary (D-69), phase 2 for requests, likes and delegates (FRD v0.18), stale singers option B (BLE v0.38), singer toolbar with follow the leader (D-17, D-70, guiDesign v0.38) | Closed | `37168f3` |
| Option B of the state-after-push proposal (the beacon names the current song) | Open: for later, the owner's call | none |
| Fit-to-width (D-28), toolbar and info sheet in the songbook reading view, day colours for sheets | Open | none |
| Resume after a crash (D-67, FR-GATH-13) | Open: next in the owner's order | none |
| The reception grid appeared once on both singers unasked | Open: unexplained, probably a long press | none |

## Decided by the owner

- Requests, likes and the delegate role move to phase 2 (FRD v0.18).
- Stale singers: option B, no expiry; the count is labelled *Aangemeld*; a swipe-away sends the leave notice (BLE v0.38).
- The setlist does not change during a campfire; the state carries `cpos` and `sung`; late joiners see earlier songs from their songs ahead (BLE v0.35).
- The reception grid is a hidden feature: long press on the singer's top line (FRD v0.17, guiDesign v0.30).
- One join control per protection mode; then join code and QR in the leader's drawer.
- The toolbar is built with follow the leader inside it (the owner's choice "With the toolbar").

## Measured

- From *Nu* to the state on the singer: S23 0.85–1.3 s by air after BLE v0.33/v0.34; Huawei 2.6–3.9 s, mostly by repair (`design/test-results/2026-10-26-state-after-push.md`).
- The leader's send loop ran 7.4 frames/s per set instead of 10; fixed rate measured 10.0/s.
- Swipe-away leave notice out 0.8 s after the swipe; the leader's count dropped (`design/test-results/2026-10-26-singer-presence.md`).
- Toolbar, follow and tap-to-scroll on the S23 and Huawei (`design/test-results/2026-10-26-singer-toolbar.md`).
