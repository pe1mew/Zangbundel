# The beacon names the current song – 2026-10-27

BLE protocol v0.43 (6.1–6.3, 7.5; option B of `design/proposals/2026-10-26-state-after-push.md`); assessment SP-31; NFR-PERF-01 (a song the singer has, within 2 s). Debug build. The A52 leads with the setlist *Voorbeeldavond* (nine songs, eight broadcast); the S23 (battery saver on) and the Huawei sing, side by side on a desk. The singers joined from the list and had about eight minutes to collect the songs ahead before the first push. **Result: every push was on both singers' screens from the beacon within 0.07–1.66 s, against 1.2–4.3 s for the signed state. No hint was withdrawn.**

**Clocks.** Each phone's clock was read over adb against the PC three times (round trip 87–122 ms): against the A52, the S23 runs 37 ms behind and the Huawei 25 ms behind, corrected below; the times are good to about ±50 ms. *Push* is the A52's log line `leader push`; *hint* is the singer's `singer hint` (the song on screen); *state* is the singer's `singer delivered doc=3`, logged when the verified state named the song and the copy kept ahead was taken as document 3.

| Push | S23 hint | S23 state | Huawei hint | Huawei state |
|---|---|---|---|---|
| 1 · *Hoog laait het vuur* | 0.07 s | 1.19 s | 1.33 s | 3.78 s |
| 2 · *Advocaatje* | 1.66 s | 4.32 s | 0.72 s | 3.21 s |
| 3 · *Daar was laatst een meisje loos* | 1.54 s | 4.03 s | 1.02 s | 3.34 s |
| 5 · *Drie schuintamboers* | 0.96 s | 3.78 s | 0.27 s | 2.57 s |

(The push of song 4 did not happen: the script's tap missed its row.)

| Check | Result |
|---|---|
| Hints withdrawn | None on either phone (`singer hint withdrawn` absent from both logs) |
| What the singers showed after the last push | *4 · Drie schuintamboers* on both, the fourth song pushed, with the lyrics from the copy kept ahead |
| Simulation, 13 % reception (`SongsAheadTest`) | The beacon hint within 2 s; the state confirms it; then the song is delivered as document 3 |
| Simulation, a forged beacon (`SongsAheadTest`) | A `cdv` that no song of the setlist has: nothing hinted. The `cdv` of a song in the setlist: that song hinted, never another; every forged hint withdrawn; the signed state still names the pushed song |
| Beacon encodings (`BeaconTest`) | 12 field bytes with `cdv`; 15 name bytes in encoding A, 6 in B; malformed and random input refused without throwing |

**Observed on the way:** the S23's state times (1.2–4.3 s) are well above the about 1 s of `2026-10-26-state-after-push.md`; it had battery saver on this time. Not investigated. **Not tested:** a forged beacon on the phones; a hint while the setlist is being changed (the singer then waits for the state, 7.5); the time a singer needs to collect the songs ahead, which the hint depends on; the field (F-09, F-10).
