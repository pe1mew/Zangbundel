# Proposal: the new state within a second or two after *Nu*

| Field | Value |
|---|---|
| Proposal | Why singers fetch the new state record over GATT after a push, and three changes that get a song held ahead on screen faster |
| Date | 2026-10-26 |
| Status | **Options A and C approved by the owner 2026-10-26 and built as BLE protocol v0.33 (7.1, 7.6); B left for later.** Results on the phones in `design/test-results/2026-10-26-state-after-push.md`: A took the S23 to 0.85–1.2 s; C showed no measurable gain on the Huawei in two pushes. |
| Asked by | The owner, 2026-10-26, after `design/test-results/2026-10-26-songs-ahead.md` |
| Affects | `design/ble-protocol.md` 7.1 (option A), 6.1–6.3 and 7.5 (option B), 7.6 (option C); `design/security-privacy-assessment.md` (option B) |
| Evidence | `design/test-results/2026-10-26-songs-ahead.md` (phones), `design/test-results/2026-10-25-D-05.md` run 13 (S23 reception), simulation below |

## 1. What happens now

After *Nu* the leader increments `seq` and every set bursts the changed documents three times (7.4 rule 4). Each pass opens with the whole new state record (240 B: 15 source frames and 30 repair frames on the legacy carrier, 45 in all), followed by the setlist and the song (about 310 more frames). With three legacy sets one pass takes about 12 s, so **the state record goes by once every 12 s**.

A singer needs any 15 of the 45. Hearing a fraction *p* of the frames, it gets 45 · *p* per pass: 5.9 at 13 %, 9 at 20 %, 13.5 at 30 %. Below about a third it cannot complete the state in the first pass, and the second pass comes 12 s later. The rate rule of 7.6 projects the rate it saw while the state was on the air, so it expects to finish; when the state's frames stop, the singer waits for its 3-second silence rule and then connects. The repair connection takes 1.0–1.6 s. Simulated with songs held ahead, 8 seeds each:

| Reception | New state after *Nu* | How |
|---|---|---|
| 13 % | 4.2–4.5 s | always by repair |
| 20 % | 1.3 s | by air |
| 30 % | 0.8 s | by air |
| 40 % | 0.7 s | by air |

The phones matched the 13 % row: the Huawei hears about 13 % with Wi-Fi on (`2026-10-25-D-05.md`), and both singers fetched the state 3–4 s after each push and had it at 4.2–4.9 s.

**The S23** heard the A52's legacy sets and beacon about 13 times a second, roughly a third of what was sent, but its extended set only 0.05 times a second (`2026-10-25-D-05.md`, run 13). The fallback of 7.1 (v0.23) keeps a singer on the extended carrier as soon as it hears **one** extended frame within 3 s of joining. On the phone the S23 caught one and stayed on a carrier it hardly hears. It collected few songs ahead, since those are never repaired, and fetched every state over GATT. Simulated with the same profile and no such frame in the first 3 s, it falls back to legacy every time, holds every song ahead and has the state **by air in 0.9 s**.

**The floor.** At 13 % a singer needs about 115 transmissions to catch 15 frames. Even if all three legacy sets sent nothing but the state, that takes 3.8 s. No broadcast scheme brings the state record to a 13 % singer within 1–2 s; only something much smaller than the state can.

## 2. Options

### A. Carrier choice by rate (singer only; protocol 7.1 wording) — recommended

A singer stays on the extended carrier only while it hears it at a useful rate: at least 3 extended frames of the gathering in the first 3 s (a tenth of what the extended set sends), and afterwards at least 3 in every 10 s. Otherwise it collects the legacy carrier, as v0.23 does now after a silent 3 s.

- **Buys:** the S23 behaves like the 30 % row: state by air in about 0.9 s, songs ahead complete. Any phone that hears a leader's extended set badly gains the same way.
- **Costs:** nothing on the air; a few lines in `SingerEngine.fallBack`.

### B. The beacon names the current song (protocol change; owner decision on a trade-off)

The beacon gets two more bytes, `cdv`: the `dv` of the current song's document 3 (0 when none). The beacon is its own advertising set and repeats every 100 ms, so even a 13 % singer hears a new one within about 0.5 s (90 % within about 1.7 s). A singer that holds a song ahead with that `dv`, whose `dh` is listed in its newest **verified** setlist (9.5 key 2), shows it at once; the signed state confirms it seconds later, or corrects it.

- **Buys:** a song held ahead is on screen within about a second at any reception that hears the beacon, 13 % included.
- **Costs:**
  - Songs ahead must be verified against the signed setlist's `dh` list on arrival. That is the first half of the original songs-ahead proposal, simplified away in v0.32, and only needs the singer to check what the setlist already carries.
  - The beacon is not signed. Someone in range could make a singer show a *different genuine song of the same setlist* until the signed state arrives (a few seconds). They could never show a text the leader did not send.
  - The gathering name in the beacon shrinks by 2 bytes on Android (17 → 15) and by 3 on iOS (10 → 7). The full name stays in Info.
  - A new assessment entry is needed.

### C. Repair the state as soon as its pass has gone by (singer only; protocol 7.6)

For the state record, wait 1 s instead of 3 s with nothing new before repairing; within a burst its frames come about 30 a second, so a second of silence means its pass is over.

- **Buys:** about 0.7–1 s at 13 % (state at about 3.5 s instead of 4.3 s).
- **Costs:** none on the air; it only brings forward connections that singer would make anyway.

## 3. Recommendation

**A and C now**: both are singer-side, need no new frames, and between them take the S23 to about 1 s and the Huawei to about 3.5 s. **B is the owner's call**: it is the only way to get a song held ahead on screen within a second or two at 13 %, at the price of an unsigned pointer that can briefly show the wrong song of the same setlist.

## 4. Open points for the owner

1. Approve A (carrier by rate) and C (state repair after 1 s of silence)?
2. B: accept the unsigned beacon pointer for the speed it buys?
