# Songs ahead on the phones (FR-BLE-18, BLE protocol v0.32) – 2026-10-26

The first run of songs sent ahead: the leader broadcasts every setlist song from the start, singers keep them hidden, *Nu* only switches. **Result: the mechanism works on both singers (a song held ahead is shown in the same millisecond as the state record that pushes it, without fetching the song), and the reception grid of guiDesign D-81 (v0.28) fills as designed. The time from *Nu* to the song did not drop much (4.2–4.9 s held ahead against 4.8–6.4 s without), because on these phones the state record itself arrives only by a repair connection, 4–5 s after the push.** The S23 collected few songs ahead, because it stays on the extended carrier, which it hears only rarely from the A52.

| Field | Value |
|---|---|
| Scenario | F-02 style, two singers, songs ahead (not yet a numbered test-plan scenario) |
| Build | `0.1.0`, build type `debug`, BLE protocol v0.32, guiDesign v0.28 |
| Participants | The owner pushed the songs on the A52; the agent drove the singers over adb |
| Conditions | Indoors, phones side by side on the desk, on USB, screens on |
| Leader | Samsung Galaxy A52, Android 14, setlist *Voorbeeldavond* (9 songs, the 9th, *Vader Jacob*, not in any installed library) |
| Singers | Samsung Galaxy S23, Android 16 (collects the extended carrier); Huawei Mate 20 Lite, Android 10 (legacy only); *Ontvangst tonen* on |

## Before the first push (15:45:56–15:47:37)

- Both singers registered within 11 s of the start.
- The grid showed *Lijst* complete and rows 1–9, row 9 dimmed (not broadcast, the song is not on the leader).
- After 80 s: the Huawei had blocks of every song 1–8 (green among orange, at its usual low reception); the S23 had three blocks of song 4 and nothing else.
- After 4 minutes: the Huawei held songs 5 and 8 complete and the others partly; the S23 still only partly held songs 3, 5, 6 and 7.

## Pushes

| *Nu* (A52) | Song | S23 | Huawei |
|---|---|---|---|
| 15:47:37.4 | 1 *Hoog laait het vuur* | +5.1 s: state and song by one repair connection | +5.2 s: state by repair, then the song by a second one |
| 15:48:04.3 | 2 *Advocaatje* | +4.8 s: state and song by repair | **+4.2 s: state by repair, song taken from ahead at once** |
| 15:48:39.6 | 4 *De boer had maar ene schoen* | **+4.9 s: state by repair, song taken from ahead at once** | +6.4 s: state, then the song, by repair |

After the pushes the grid showed the titles of the pushed songs and the orange bar on the current one; upcoming rows kept their numbers only (D-47).

## What it shows

1. **Songs ahead work end to end.** Where a singer held the song, the log shows `delivered doc=3 repair=false` in the same millisecond as the state record: no song transfer, no burst waited for.
2. **The state record is now the bottleneck.** Both singers fetched the new state over GATT 3–4 s after the push (the repair rule of 7.6), and the connection took 1.0–1.6 s. Until the state arrives faster, songs ahead save the song's share of the repair connection, not the wait.
3. **The S23 gets few songs ahead.** It hears an extended frame within 3 s of joining, so the fallback of 7.1 (v0.23) keeps it on the extended carrier, which it hears only rarely from the A52; songs ahead are never repaired, so they trickle in. The legacy-only Huawei collected far more.

## Follow-up

- Faster state delivery after a push (the burst sends the whole state first; the singers still repaired it).
- The carrier choice: a singer that hears the extended set only rarely should collect the legacy carrier, or both.
