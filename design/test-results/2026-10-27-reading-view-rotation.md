# Rotation in the songbook's reading view – 2026-10-27

guiDesign D-42 as built in v0.53; FR-DISP-04. Debug build on the A52, no campfire running. The owner turned the phone; nothing on the screen was touched. **Result: the line at the top stays at the top. The first try found that a rotation sent an idle app home from any screen; fixed.**

| Step | Result |
|---|---|
| *Zangbundel* → *De boer had maar ene schoen*, scrolled into Couplet 2 | Top line: *…klak, weinig genoeg.*, the second line of Couplet 2, wrapped over two lines in portrait |
| Turned to landscape, first build | **Fault:** the home screen. No crash: the activity was made anew, and the effect that follows the gathering's mode (`ZangbundelApp`) took the idle mode for a change and popped back to home. It does so from every screen while no campfire runs; the rotation tests of the leader's and singer's pages passed because there the target was the screen already shown. Fixed: the effect acts only when the mode really changes, remembered through the rotation |
| After the fix: the same song, scrolled to the same place, turned to landscape | Top line: *de boer had maar ene klak, weinig genoeg.*, the same line, now on one line, with Couplet 3 below it; *Toevoegen aan setlist…* stays at the bottom |

**Not tested:** a rotation back to portrait; a rotation on settings, the library or the setlists after the fix (the fix is in the one place that sent them home); a rotation while the song is still loading.
