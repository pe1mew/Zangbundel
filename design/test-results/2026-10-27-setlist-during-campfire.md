# Changing the setlist during the campfire – 2026-10-27

guiDesign D-58, D-55 and D-65 as built in v0.40; BLE protocol 7.5, 9.4, 9.5 and 10.5 as of v0.40; FR-LEAD-01, FR-LEAD-03, FR-LEAD-06. The A52 led a campfire started **without a setlist**; the S23 and the Huawei sang. Driven over adb. **Result: every action of the sheet and *Wis* did what the proposal says, on the leader and on both singers; two faults found in the sheet were fixed and retested. No crash on any phone.**

| Step | Time | Result |
|---|---|---|
| Campfire started without a setlist | 08:48 | The leader's page: *Kies een lied uit de zangbundel.* and *Zangbundel openen* (D-55) |
| *Nu zingen* on *Hoog laait het vuur* (songbook tab) | 08:48:55 | The setlist became 1/1 with *Wis* on the current song; both singers showed *Hoog laait het vuur* |
| *Toevoegen* on *Advocaatje* and *De koning van Siam* | 08:49 | Both at the end of the setlist, with a handle; the current song without one |
| *De koning van Siam* dragged above *Advocaatje* | 08:49 | Order changed |
| *Advocaatje* swiped away | 08:49 | Removed; the bar *Advocaatje verwijderd · Ongedaan maken* |
| *Nu* on *De koning van Siam* (added during the campfire) | 08:50:40.1 | S23 delivered state and song at 08:50:43.3 by air (3.2 s); Huawei fetched the state by repair and took the song from its songs ahead at 08:50:44.7 (4.6 s): the added song had gone out ahead |
| *Wis* | 08:51:01 | Leader: both songs *gezongen*, nothing current; both singers *gewist* in the strip, lyrics kept, no orange bar |
| Huawei left and joined again | 08:51–08:52 | Two `EarlierSong` events: both sung songs shown, from the renumbered setlist |
| Leader app killed by a reinstall, *Hervatten* | 08:54 | The setlist built during the campfire came back: two songs, both sung, nothing current; later also with *Advocaatje* added |
| *Kampvuur beëindigen* | 08:55 | Ended |

**Faults found and fixed during the test:**

- **Undo removed the song again.** The list keeps a row's saved swipe state by its key, and the song put back has the same line, so it came back dismissed and went again. The swipe state is now set back to settled after the removal is recorded. The first fix set it back *before* recording, which cancelled the effect (its own key had changed), so a swipe removed nothing; it now runs last. Retested: swipe, *Ongedaan maken*, the song stays.
- **Titles in the songbook tab were cut to a dozen characters** by the two buttons beside them. The buttons are now stacked at the right and titles get two lines.

**Not tested:** the 6-second limit of the undo bar on purpose (it ran out once during the test, as designed); moving a song across a sung one; a setlist of more than a screen.
