# Proposal: a hard bound on the line list, against chorus repeats that double a song

| Field | Value |
|---|---|
| Proposal | A new song error **S013**: the song is refused when its canonical line list would grow beyond **32 768 lines**. Only chorus repeats can reach that, because a 64 KiB file cannot hold that many lines written out. Without the bound a song of a few kilobytes runs any phone that parses it out of memory. |
| Date | 2026-11-12 |
| Status | **The app side is approved by the owner 2026-11-12** ("Yes, fix it", to stopping the expansion at a hard line limit and refusing that song) and built. The format text is the library session's to write: song format 5.5, 7.2 and 11.3. The number and the code are proposed, not yet agreed with the library session. |
| Asked by | Release check G-09 for app 0.5.0: the `songParser` fuzz target ran out of memory on 2026-11-11 at 13:03 (`design/test-results/2026-11-11-release-checks-0.5.0.md`) |
| Affects | `design/song-format.md` 5.5, 7.2, 11.3; the conformance corpus (`songs/chorus-repeat-bound`); the library repository's `validate.py`; the library manager's engine, if it expands repeats |
| Requirements | NFR-SEC-04 (parsers MUST enforce explicit size and time bounds); assessment SP-41 |

## 1. What goes wrong

Song format 7.2 says what `{chorus}` repeats. It does not say what happens when `{chorus}` stands **inside** a chorus definition. The app has always made the repeated lines part of the chorus being defined, so a later `{chorus}` repeats them too:

```
{start_of_chorus}
Een regel.
{end_of_chorus}
{start_of_chorus}
{chorus}
{chorus}
{end_of_chorus}
```

The second chorus is two lines. Each further block of the same four lines doubles the chorus again. 20 blocks fit in 1.3 kB and are 2^21 − 1, about two million, lines; 40 are 2^41. 5.5 bounds the file at 64 KiB and the line list only with a warning (W004) after parsing, so nothing stops the growth.

Every phone that parses the song is hit: on import, in the songbook, and as a singer receiving the current song from the leader over the air (ble-protocol 9.5). Whoever starts a gathering, or hands out a library, can crash the app of everyone who joins it or imports it. The weakness is in every app version up to 0.5.0.

## 2. Proposed format text

**5.5 Limits**, a new row after *Canonical line list*:

| Limit | Value | Rule |
|---|---|---|
| Canonical line list, hard | ≤ 32 768 lines | MUST; the song is refused (S013). A 64 KiB file cannot hold more lines written out, so only chorus repeats (7.2) can pass it. A reader checks it before each repeat is expanded, so its work stays within the bound. |

**7.2 Chorus repeats**, a new bullet:

- A `{chorus}` inside a chorus definition is part of that chorus: the repeated lines are repeated again wherever that chorus is. Each such repeat adds to the canonical line list, which 5.5 bounds (S013).

**11.3 Song level**, a new row:

| Code | Condition |
|---|---|
| S013 | The canonical line list would be longer than 32 768 lines (5.5); only chorus repeats can make it so |

The 7.2 bullet writes down what the app already does, so the line list of every song within the bound is the same as in app 0.4.0 and 0.5.0. Positions sent over BLE stay right in gatherings with mixed versions.

## 3. Why 32 768

- It refuses no song that has no repeats: 64 KiB of lines of one character and a newline is 32 768 lines, before the required metadata.
- It is far above any real song: W004 already warns above 254 lines, the most the BLE `pos` field can point at.
- It bounds the work: the reader checks the count before it copies a repeat, so it never builds more than 32 768 line entries for one song.

A lower bound, such as 1 000, would also work and keep the app's memory smaller. It would refuse written-out songs above it that are accepted today with W004. The app can follow either number; 32 768 changes nothing for any song that is not built to grow.

## 4. What the app does (built 2026-11-12, staged)

- `Limits.CANONICAL_LINES_HARD_MAX = 32 * 1024`; `SongParser`'s chorus repeat checks it before copying and reports S013 once.
- `ReportText` shows S013 as *The song becomes too long once its chorus repeats are written out.* (nl, de; fr and it in the review build). In a package's report S013 stays S013, since the FDS has no SNG code for it yet.
- Tests in `SongParserTest`:
  - a repeat inside a chorus definition stays part of that chorus (the line list as before);
  - 40 levels give S013 and no song, where they ran out of memory before the fix;
  - 14 levels, 32 767 lines, are still read, and 15 levels give S013.
- Conformance vector `songs/chorus-repeat-bound` (20 levels; S013, W002, W004; refused).
- The fuzz finding's input is replayed by `SongFormatFuzz.songParser` from `src/fuzz/resources/…/SongFormatFuzzInputs/songParser/`.

## 5. Asked of the library session

1. Agree the number and the code, or name others; the app follows.
2. Write the three passages of section 2 into the song format (its next version).
3. Check `validate.py` for the same growth, and pass the question on to the library manager's engine.
