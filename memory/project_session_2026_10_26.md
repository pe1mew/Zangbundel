# Session record 2026-10-25 (continued) to 2026-10-26

Dated record, not a status board. Current state lives in `memory/MEMORY.md`.

## The ask

Continued from the 2026-10-25 curation (`f9d547b`): "next step" through the protocol core, then the two-phone test on the owner's phones and the reception problem it showed, the owner's choice of erasure coding ("Approve the erasure-coding proposal as written: move it into the BLE protocol and implement it in core/protocol"), GATT registration and repair ("plan and build GATT registration and repair (ble-protocol 8–10) in AndroidTransport and SimulatedAir, with the singer's repair path, and measure delivery on the phones"), the open protocol questions it raised, a reception bar for singers, and then `core/songformat` section by section on "continue".

## Threads

| Thread | Outcome | Commit |
|---|---|---|
| Carousel scheduler, shared legacy burst (protocol v0.18) | Closed | `5aa8a59` |
| Singer, leader and slot state machines | Closed | `dc86ea0` |
| Beacon encodings and join-link codec (v0.19 link size) | Closed | `1c8afcc` |
| `AndroidTransport`, two-phone test, reception experiments (D-05 runs 1–10: filters, PC count with bleak, Wi-Fi off) | Closed: receivers hear 13–40 %; Wi-Fi roughly halves it | `8cd91a0` |
| Design options, erasure-coding proposal, FRD Q14 on NFR-PERF-01 | Closed: owner chose erasure coding on both carriers with GATT repair; protocol v0.21 implemented; FRD Q14 stays open | `8cd91a0` |
| Extended carrier on the S23 and A52 (runs 12–16), extended-only switch and probe | Closed: the A52's extended set is not usable; which side is at fault needs a third extended sender | `db22da8` |
| GATT registration and repair, `SimulatedAir`, `AndroidGatt.kt` (run 17) | Closed; v0.22 (manifest watch, 509-byte chunks) accepted | `fd1f589` |
| Owner's decisions Q22–Q26, simulations S-01 to S-06 | Closed: v0.23 and v0.24; S-03 passes, S-02 close | `c00ecd7` |
| v0.24 on the phones (run 18), one repair connection takes everything (v0.25) | Closed | `64f7080` |
| Reception bar: FR-SING-05, D-81, built and checked on the Huawei | Closed; shown in the two-phone test until singer mode exists | `d4a995c` |
| Song parser and song vectors | Closed | `116a408` |
| Library file and import check across files | Closed | `894136f` |
| Release archives and snapshots on an own ZIP reader, archive vectors | Closed | `fc42e96` |
| Setlists, setlist vectors | Closed: song-format 3 to 13 all in code | `41eb28c` |
| Galaxy S9 as a fourth phone | Closed: no extended support; cannot settle the A52 question | none |
| A third extended sender for the A52 question | Open: needs another phone that sends extended advertising | none |
| Request and Control (delegate) commands, document 8, `stay`, relays, LeaderMachine in LeaderSession | Open: not built; Request answers `0x81`, Control `0x89` except leave | none |
| App storage, library screens, leader and singer mode | Open: next | none |
| Jazzer fuzz targets | Open: needs JUnit 5 | none |

## Decided by the owner

- Erasure coding across frames on both carriers, GATT repair as the fallback, the library as the fast path; the proposal approved as written (protocol v0.21).
- v0.22 as written: 7.6 watches manifest documents from the state's application; DATA chunks at most 509 bytes.
- Q22: the HEADER spans notifications. Q23: a singer falls back to the legacy carrier after 3 s without an extended frame. State and unheard documents on one connection (v0.23).
- Q24: the whole state record opens a burst. Q25: large documents keep the code as it is. Q26: repair when `k` is not expected within 5 s of the group's start (v0.24).
- One repair connection fetches everything still being collected, state first (v0.25).
- FR-SING-05 and D-81, the reception bar: off at every start, switched on in settings, a floating island at the bottom, hidden by a tap; orange means missed; one block per source frame.

## Measured

- D-05 run 11 (erasure code, Wi-Fi on): Huawei 4 of 4 in 38–40 s, where none arrived before.
- D-05 run 17 (GATT repair): Huawei median 5.5 s, S23 9.0 s, all by repair. Run 18 (v0.24): S23 fell back to legacy after 3.3 s, median 4.6 s; Huawei 5.8 s.
- Simulations with v0.24 and v0.25 (`design/test-results/2026-10-25-S-01-06.md`): S-02 median 3.8 s, S-03 0.5 s, 100 singers on three legacy sets 5.9 s, extended 2.6 s at any size; songs on the legacy carrier now go mostly over GATT, even at full reception.
