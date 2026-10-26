# The state after *Nu*: carrier by rate and faster state repair – 2026-10-26

Options A and C of `design/proposals/2026-10-26-state-after-push.md`, approved by the owner and built as BLE protocol v0.33 (7.1, 7.6), on the same three phones as `2026-10-26-songs-ahead.md`. **Result: A works. The S23 left the extended carrier it hardly hears, held all eight songs ahead within four minutes, and showed each pushed song 0.85–1.2 s after *Nu*, state by air and song from ahead, against 4.8–4.9 s by repair before. C showed no measurable gain on the Huawei in two pushes (state at 4.8 and 5.9 s against 4.2–5.2 s before); it still started its repair about 3 s after *Nu*.**

| Field | Value |
|---|---|
| Build | `0.1.0`, build type `debug`, BLE protocol v0.33 |
| Participants | The owner pushed the songs on the A52 (and the Huawei left and rejoined once, 16:07:38–43); the agent drove the singers over adb |
| Conditions | As in `2026-10-26-songs-ahead.md`: desk, USB, screens on |
| Leader | Samsung Galaxy A52, setlist *Voorbeeldavond* (9 songs, the 9th not broadcast), started 16:07:07 |
| Singers | Samsung Galaxy S23 (can receive extended); Huawei Mate 20 Lite (legacy only, about 13 %); *Ontvangst tonen* on |

## Before the first push

At 16:10:51 the S23's grid showed every song 1–8 complete, with legacy-sized *Lijst* blocks: it had fallen back to the legacy carrier. In the v0.32 run it had parts of four songs after four minutes. The Huawei, joined again at 16:07:43, held songs 1–3 and was collecting 4–8 (orange among green).

## Pushes

| *Nu* (A52) | S23 | Huawei |
|---|---|---|
| 16:08:54.5, song 1 | **+1.2 s**: state by air, song taken from ahead | +5.9 s: repair started +3.3 s, state +5.9 s, song with it (not yet held ahead) |
| 16:10:05.0, song 2 | **+0.85 s**: state by air, song taken from ahead | +4.8 s: repair started about +3 s, state +4.8 s, song with it (not yet held ahead) |

Each S23 push was followed by one repair connection 5–6 s later for the rest of the burst (the setlist); it did not hold up the song.

## What it shows

1. **Option A does what it was for.** A singer that hears the legacy carrier well is no longer kept on an extended set it hears now and then; on legacy the S23 completes the state in the first pass and has every song ahead.
2. **Option C did not show on the Huawei.** The rule is in place: simulated at 13 % the state now arrives in under 4 s, against 4.2–4.5 s in v0.32 (`SongsAheadTest`). On the phone the repair still began about 3 s after *Nu*. The engine's state is logged only once a second, so the start time is known to ±1 s, and EMUI may hand scan results over late or in batches, which would keep "something new" arriving after the pass. Two pushes are too few to tell; it needs per-frame timing on the Huawei.
3. **At 13 % only option B gets below 2 s**, as the proposal says: even a perfect repair rule leaves about 2.5 s to notice the pass is over plus 1.5 s for the connection.
