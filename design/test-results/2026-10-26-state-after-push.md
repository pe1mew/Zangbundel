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

## Per-frame timing on the Huawei (16:21–16:22)

A build that logs the engine's repair decisions (`SingerEngine.Event.RepairDecided`) and every state frame heard (`StateFrame`) was installed on the Huawei only; the A52's campfire kept running. The agent pushed three times on the A52: song 3 again twice (16:21:26.5 and 16:22:06.7) and, through a mis-tap, song 2 at 16:22:15.2.

| *Nu* | State frames on the Huawei | Repair decided | New state |
|---|---|---|---|
| 16:21:26.5, re-push | new ones every 0.1–0.6 s from +0.1 s; `newseq` seen at +1.3 s | +2.58 s, by the rate rule (last new frame 0.4 s before) | +4.1 s by repair |
| 16:22:06.7, re-push | 18 new ones between +0.1 and +4.7 s | none | **+4.7 s by air** |
| 16:22:15.2, song 2 | 7 new ones up to +1.9 s, then none | +2.55 s, by the rate rule | +4.4 s by repair, song with it |

**Why option C did not show:** after a re-push the burst carries only the state record, so its frames keep coming for seconds; after a real push the rate rule fires at 2.5 s, before 1 s of silence matters. Set S's source frames arrived about 7 a second, against the designed 10. Simulation of the next steps and a proposal: `design/proposals/2026-10-26-burst-state-first.md`.

## BLE protocol v0.34 on the phones (16:41–16:48)

All four changes of `design/proposals/2026-10-26-burst-state-first.md` (state lead-in, state judged after 1 s against 3 s, no Info repair after registration, setlist before the song in the burst), on all three phones. Campfire started 16:41:33; both singers held songs 1–4 ahead after four minutes (the S23 all eight). The agent pushed with `nu.py` on the A52; the owner pushed song 1 once before.

| *Nu* | S23 | Huawei |
|---|---|---|
| 16:45:39.9, song 1 again | **1.4 s**, state by air | **2.9 s**: repair decided at +1.3 s |
| 16:46:29.6, song 2 | **1.2 s**, by air, song from ahead | **2.8 s**: repair decided at +1.4 s, song from ahead |
| 16:47:19.4, song 3 | **2.4 s**: repair decided at +1.2 s, song from ahead | **3.9 s**: repair decided at +2.1 s, song from ahead |

**What it shows.**

1. **The Huawei now has a pushed song in 2.8–3.9 s** (v0.33: 4.4–5.9 s), the earlier judgement sending it to GATT at 1.2–2.1 s. The S23 stays at 1.2–2.4 s.
2. **Info is no longer repaired**: the S23 received it by air at +8.6 s.
3. **The setlist is still fetched over GATT after every push.** Its frames now come after the 4.5-s lead-in, so the 3-second rule of 7.6 fires first: the S23 made a separate connection for it at +4.3 s each time, while the Huawei took it on its state connection. Putting the setlist before the song did not help, because the lead-in comes before both. The owner asked at this point why the *Lijst* row turns white and then green again on every push: the setlist document carries every sung and current song with its status, so every push makes a new version that the singer collects again.

## BLE protocol v0.35, the static setlist (16:56–17:08)

The setlist document now carries only the order (`dh` per position) and the state names `cpos` and `sung` (`design/proposals/2026-10-26-static-setlist.md`). Campfire started 16:56:36; the Huawei joined at once; the agent pushed song 1 (16:59:39) and song 2 (17:00:29), the owner song 6 (17:03:20).

| Check | Result |
|---|---|
| Huawei, song 2 | **2.7 s**: state by repair, song from ahead; the connection fetched the state only, no setlist |
| Huawei, song 6 | **2.6 s**: repair decided at +1.1 s, song from ahead; again no setlist |
| S23, song 2 (joined 12 s after song 1) | **1.1 s** by air, song from ahead |
| Reception grid | *Lijst* green throughout; titles on the pushed rows, numbers on the others, the orange bar on the current one |
| **Late joiner**: the S23 left at 17:04:35 and joined again at 17:04:47 | registered at 17:04:53; songs 1 and 2 reported as sung before it arrived at 17:04:57 and 17:05:19 (from its songs ahead, verified by the setlist's `dh`); its page showed *1 · Hoog laait het vuur*, *2 · Advocaatje*, then the current *3 · Ik heb een tante in Marokko*, with full lyrics |

**Result:** the setlist no longer goes out or is fetched after a push, and a late joiner shows what was sung before it came (guiDesign D-47 as noted in v0.29).
