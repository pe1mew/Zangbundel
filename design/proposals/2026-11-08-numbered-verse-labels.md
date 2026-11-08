# Proposal: verse numbers that keep the original numbering

| Field | Value |
|---|---|
| Proposal | A verse label that holds only a number is the verse's number. The app shows it with its own word for "verse", in the app language: `{start_of_verse: 6}` reads *Strophe 6*, *Couplet 6* or *Verse 6*. Verses without a label count on from the last number. Song format 7.1, a new warning W015. |
| Date | 2026-11-08 |
| Status | **Awaiting the owner's decision.** |
| Written by | The library session, which is responsible for the format. |
| Asked by | Pepe, library issue [pe1mew/Zangbundel-bibliotheek#5](https://github.com/pe1mew/Zangbundel-bibliotheek/issues/5) ("Pepe's Proposal #12") |
| Affects | `design/song-format.md` 7.1 and the warning table of 11; the app's label generation; the library repository's `validate.py` and `conformance.py`; the conformance corpus; the library manager's engine (SNG rules), if it shows or checks labels |
| Requirements | FR-SING-10 |

## 1. The question

A hymn has eight verses, of which today only 1, 2, 6 and 8 are sung. The song file holds those four. Without labels the app numbers them 1, 2, 3 and 4 (song format 7.1: generated labels count the verses in file order). How can the file keep the original numbers?

Today there are two ways, and both have a cost:

- **Labels written in the file**, `{start_of_verse: Strophe 6}`. The app shows a written label exactly as written, so a Dutch or English user sees the German word. A song must then label every verse: a verse without a label still counts by position, so the second verse of Pepe's example shows *Couplet 2* next to *Strophe 1*.
- **Two songs**, a shortened one and the complete one, with titles and ids that say which is which. This doubles the song in the library.

Pepe proposes variables for the words *Couplet* and *Refrain* inside a label, so the word follows the app language.

## 2. Proposal

In 7.1, a verse label made of digits only is the verse's **number**, not text:

| File | App shows (Dutch / English / German UI) |
|---|---|
| `{start_of_verse}` | Couplet 1 / Verse 1 / Strophe 1 |
| `{start_of_verse}` | Couplet 2 / Verse 2 / Strophe 2 |
| `{start_of_verse: 6}` | Couplet 6 / Verse 6 / Strophe 6 |
| `{start_of_verse: 8}` | Couplet 8 / Verse 8 / Strophe 8 |

The rules, for 7.1:

1. A **number label** is a `{start_of_verse}` label of 1 to 3 digits without a leading zero, so 1 to 999, after the trimming of 5.3. The app shows it as its generated label with that number, in the UI language.
2. **Counting.** The app keeps a verse counter that starts at 0. For each verse environment and each implicit verse (7.3), in file order:
   - with a number label N, the verse is number N and the counter becomes N;
   - otherwise the counter goes up by one, and a verse without a label gets the generated label with the counter's value. A verse with a text label shows its text, as today, and still counts.

   So only the verses where the numbering jumps need a label. Without any number label, the result is what 7.1 gives today.
3. **W015 (validator only), *verse number does not go up*:** a number label that is not greater than the number of the verse before it. The song is shown as written; the warning catches typing errors.
4. Only verses get number labels. A chorus or bridge label of digits stays text, shown as written.
5. Anything else, such as `06`, `6a` or `Strophe 6`, is a text label as today.

Pepe's song then reads:

```
{start_of_verse}
Gott ist gegenwärtig. Lasset uns anbeten
…
{end_of_verse}

{start_of_verse}
Gott ist gegenwärtig, dem die Cherubinen
…
{end_of_verse}

{start_of_verse: 6}
Du durchdringest alles; lass dein schönstes Lichte,
…
{end_of_verse}

{start_of_verse: 8}
Herr, komm in mir wohnen, lass mein' Geist auf Erden
…
{end_of_verse}
```

## 3. Why this form

- **No new syntax.** The label already exists, and a label of digits only has no other useful meaning today. A variable such as `%verse% 6` needs a placeholder syntax and an escape for it, and a placeholder could appear anywhere in any label.
- **It degrades gently.** An app from before this version shows a written label exactly as written, so it shows *6*: the right number without the word. A placeholder would show the raw `%verse% 6`.
- **The format version stays 1.1.** The line list does not change, because labels are not lyric lines (9.1), and every valid file stays valid.
- **The phones agree.** Nothing language-dependent goes over Bluetooth (BLE protocol v0.12). Each phone generates labels from the same file, so leader and singers show the same numbers, each in their own language.
- **It answers Pepe's wish for the verse word.** The word comes from the app, as it already does for verses without a label. Pepe's proposal also names *Refrain*. A chorus without a label already gets the app's word for it, so a chorus needs no variable.

## 4. Not chosen

| Option | Why not |
|---|---|
| Variables in labels (`%verse% 6`, Pepe's idea) | New syntax with escaping. Older apps show the placeholder. It isn't needed for choruses, whose generated label is already in the app language. |
| A song-level list, `{meta: verses 1 2 6 8}` | The numbers sit far from the verses they belong to. Adding or removing a verse means editing two places, and a list that disagrees with the file needs its own error. |
| A new directive, `{verse_number: 6}` | A second way to say what a label can say. Older apps report it as unknown (I001) and show nothing. |
| ChordPro attributes (`label="…"`) | Not part of the profile (5.3). |

## 5. If approved

| Where | Change | Owner |
|---|---|---|
| Song format | v0.29: 7.1 rules 1 to 5 above; W015 in the warning table; the 7.1 table's "n counts verses in the file" points to the counting rule. | Library session |
| App | Generated verse labels follow the counting rule. Tests for each rule. | App session |
| Corpus | Song vectors `verse-number-labels` (labels 6 and 8 between verses without labels: numbers 1, 2, 6, 8) and `verse-number-down` (W015). Song vectors don't record labels today, so `expected.json` gains an optional `verse_numbers` list that both the app and `conformance.py` compare. | App session, with the library session for `conformance.py` |
| Library repository | `validate.py`: W015, and the verse numbers for `conformance.py`. README: how to keep original verse numbers. | Library session |
| Library manager | Its SNG rules don't check labels. If the Editor previews labels later, it uses the counting rule. | Library manager session |
