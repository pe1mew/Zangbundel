# Proposal: the state first in the burst, and no connections a push does not need

| Field | Value |
|---|---|
| Proposal | Send the new state record's three passes first, as distinct frames; judge the state's repair after 1 s; stop repairing Info after registration; send the setlist before the song |
| Date | 2026-10-26 |
| Status | **Approved by the owner 2026-10-26, all four changes; built as BLE protocol v0.34.** Correction found while building: the song's burst starts about **3 s** later, not 1.3 s (the lead-in of a typical state is 135 frames, 90 more than before), and a singer that does not hold the song ahead then usually fetches it over GATT, whatever its reception (section 3, last paragraph). |
| Follows | `design/proposals/2026-10-26-state-after-push.md` (options A and C built as BLE protocol v0.33; B left for later) |
| Affects | `design/ble-protocol.md` 7.2 and 7.3 (frame index up to 255), 7.4 rule 4 (burst order), 7.6 (state judgement, Info); `core/protocol` `Framer`, `Frame`, `Carousel`, `SingerMachine` |
| Evidence | `design/test-results/2026-10-26-state-after-push.md` (per-frame timing on the Huawei); simulation below |

## 1. What the per-frame timing showed

With the diagnostics added on 2026-10-26 (`SingerEngine.Event.StateFrame`, `RepairDecided`), three pushes on the A52 were logged frame by frame on the Huawei:

- **The 1-second silence of option C never happens on that phone.** After a re-push of the same song the burst is the state record alone, three passes back to back, and its frames came in every 0.1–0.6 s for 7 s. Once the state completed by air (4.7 s); once a repair was decided at 2.6 s.
- **After a real push the rate rule decides, at 2.5 s.** The state's frames stopped at about 1.9 s, when the pass moved on to the song, with 7 of the 18 needed. The rule of 7.6 makes its first estimate 2 s after the new `seq`, so the repair was decided at 2.55 s. With the 0.2–0.4 s random wait and a 1.3–1.8 s connection, the state arrived at 4.4 s.
- **The A52 sends slower than designed.** Set S's source frames of the state came about 7 a second, not 10, which matches the open note that a burst pass takes 9–10 s on the A52.

The simulation adds two more findings:

- **One pass decides it.** A singer must catch `k` of the state's `n` = 3`k` frames in the single pass at the head of the burst; the next copy is about 12 s away, behind the song. Even at 40 % that sometimes fails, and then it connects.
- **Every push costs every singer one or two extra connections.** A 40 % singer that had the state and song by air at 0.9 s still connected at +4 s for the **setlist**, which changes with every push and comes after the song's 256 frames, and at +7 s for **Info**, which changes with the singer count and which a registered singer no longer needs. With 25 singers that is up to 50 connections per push, against FR-BLE-15. On the phone the S23 made the setlist connection 5–6 s after each push.

## 2. Proposed changes

1. **State lead-in (7.4 rule 4, 7.2, 7.3).** On a push, every legacy set first sends three passes' worth of the new state record as **distinct** frames: its `k` source frames on Set S, then repair frames up to `3 · n` in all (at most 256), shared over the sets. Then the other changed documents three times, as now. Frame indices may then go up to 255 for any document; a v0.33 singer drops frames above its usual `n`, so all phones must run the new version (no release has gone out).
2. **State judgement after 1 s against 3 s (7.6).** For the state record the rate rule makes its first estimate 1 s after the new `seq` and repairs when `k` is not expected within 3 s, so a singer that cannot catch it by air connects at once instead of at 2.5 s.
3. **No repair of Info after registration (7.6).** A registered singer has the leader's key; a changed Info (the singer count) is not worth a connection.
4. **Setlist before the song in the burst (7.4 rule 4).** The setlist (60–100 frames) goes before the song in each pass, so it completes by air with the state; most singers hold the song ahead anyway.

## 3. Simulation

Time from *Nu* until the pushed song is on screen, song held ahead, median and 90th percentile over 30 pushes; changes 1 and 2 only.

| Reception | As built (v0.33) | Judge after 1 s (2) | State lead-in (1) | **Both (1 + 2)** |
|---|---|---|---|---|
| 13 % | 2.9 s (3.2), always repair | 2.0 s (2.9), always repair | 3.1 s (3.4), ⅓ repair | **2.2 s (3.0)**, ⅔ repair |
| 20 % | 3.2 s (3.4), always repair | 2.8 s (3.4), always repair | 2.1 s (2.2), by air | **2.1 s (2.2)**, by air |
| 30 % | 1.3 s (3.2) | 1.3 s (3.2) | 1.3 s (1.4) | **1.3 s (1.4)** |
| 40 % | 1.1 s (1.1) | 1.1 s (1.1) | 1.1 s (1.1) | **1.1 s (1.1)** |

The simulation sends at the designed 10 frames a second per set; the A52 sends about 7, which stretches the air times by about a third. Changes 3 and 4 were not simulated; they remove the setlist and Info connections described above. The song's own burst starts about 3 s later than before (corrected after approval: 8.7 s instead of 5.7 s by air with no loss on three sets, 13 s instead of 8.5 s on two). A singer that does not hold the song ahead hears none of it for 4.5 s, so the 3-second rule of 7.6 sends it to GATT whatever its reception; with songs ahead that is the exception, but it costs one connection per such singer per push.

## 4. Open points for the owner

1. Approve changes 1 to 4?
2. Under 2 s at 13 % still needs option B of the earlier proposal (the beacon names the current song).
