# Proposal: indented comments and the display text keep the app's line list

| Field | Value |
|---|---|
| Proposal | Two points left open by `design/proposals/2026-11-07-fds-0.8-readings.md` (section 5) are settled the way the app already reads them, so the line list a song gives stays the same as in app 0.4.0. **(a)** A comment is a line whose first character is `#`; an indented `#` line is a lyric line, and the library's checks warn about it (W014). **(c)** The display text drops trailing white space as Unicode defines it, the no-break space included, so a line holding only a no-break space adds no line. The library manager and the library repository's tools follow. |
| Date | 2026-11-07 |
| Status | **Approved by the owner 2026-11-07** ("I agree", after the explanation of both points). Song format v0.25. No change to the app's code. |
| Asked by | The owner, after the two points were found while building song format v0.24 |
| Affects | `design/song-format.md` 5.2, 9.1, 11.3 (W014); the conformance corpus; the library manager's engine (FDS O-11); the library repository's `validate.py` |
| Requirements | FR-LIB-07, FR-BLE-12 |

## 1. Why the line list decides

A song's line list (song format 9) numbers its lyric lines in sung order. Over BLE the leader names the current line by its number (`pos`, ble-protocol), and every phone looks that number up in its own line list.

If two app versions built different line lists for the same song, phones on different versions would mark different lines from the affected point on. Both open points could change the line list. For each, the reading that leaves it as app 0.4.0 builds it is chosen, and the other implementations follow.

## 2. (a) An indented comment

`  # tweede stem` (spaces, then `#`):
- **The app** has always read this as a lyric line, because song format 5.2 says a comment's first character is `#`.
- **The library manager's engine** trimmed first and read a comment.

**Decision.** The first character decides, with no trimming. The line is a lyric line, shown with its leading spaces. The library's checks report **W014** so that the maintainer fixes it, by removing the indentation to make it a comment or the `#` to make it a lyric line.

W014 is a validator warning. The app does not report it, because the person importing a library cannot fix it, and its import report gains nothing. The conformance comparison leaves W014 out.

The app's setlist parser does trim before `#` (song format 13). That stays: a setlist has no line list and is never shown line by line.

## 3. (c) The display text and a no-break space

Since v0.24 a line holding only a no-break space (U+00A0) is not blank: only spaces and tabs count there. It is a content line, and its display text (9.1) is made by:
1. removing the chords;
2. resolving the escapes;
3. replacing tabs by spaces;
4. trimming trailing white space.

**What differed.** The app trimmed white space in the Unicode sense, which includes the no-break space, so the display text was empty and the line added nothing. The library manager's engine trimmed spaces and tabs only, so it got a lyric line holding a no-break space, which looks empty but has its own number.

**Decision.** 9.1 names the characters it trims: trailing spaces, and characters of the Unicode categories Zs, Zl and Zp (the no-break space U+00A0 is Zs). Tabs have become spaces by then, and other control characters are refused (5.1). This is what the app does (Kotlin `trimEnd()`), so the line list stays that of 0.4.0. The other implementations follow.

## 4. Done 2026-11-07

- **Song format v0.25:**
  - 5.2 says an indented `#` line is a lyric line, with W014;
  - 9.1 names the trimmed characters;
  - 11.3 adds W014.
- **Conformance vectors:**
  - `comment-indented`: accepted, W002; the lines are the indented `#` line and the lyric line.
  - `nbsp-display`: accepted, W002; an NBSP-only line and a trailing NBSP add nothing.
  - Both pass on the app unchanged.
- **The library manager** is told to read (a) and (c) like the app; its Editor may show W014.
- **The library repository** is told to have `validate.py` follow, report W014, and leave W014 out of the conformance comparison.
