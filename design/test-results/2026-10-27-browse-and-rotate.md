# Verse jumps, browsing and rotation on the singer's page – 2026-10-27

guiDesign D-29, D-40 and D-42 as built in v0.43 and v0.44; FR-SING-13, FR-SING-14, FR-DISP-04. The A52 led with the setlist *Voorbeeldavond*; the S23 sang. **Result: each behaved as the decisions describe; one layout fault was fixed during the test. No crash on any phone.**

| Step | Result |
|---|---|
| *De boer had maar ene schoen* pushed; three swipes to the left, one to the right, one more to the right | *Couplet 1*, *Couplet 2* at the top, back to *Couplet 1*, then the song's start with its title (FR-SING-13) |
| *Advocaatje* pushed; the S23 scrolled back up into the previous song | The chip *Terug naar het huidige lied* appeared; a tap brought *Advocaatje* back to the top and the chip went (FR-SING-14) |
| Songlist sheet, *Zangbundel* tab, with no library on the S23 | Empty at first; now the songbook's text *Nog geen liederen…* |
| Default library added on the S23 (the owner accepted its licence), *Drie schuintamboers* opened from the tab | The song filled the page, the strip read *bladeren*, the chip showed |
| *Daar was laatst een meisje loos* pushed while browsing | The S23 went back to the campfire's page on the new song by itself; the strip read *verbonden* again |
| Page scrolled to *die wou gaan varen als lichtmatroos.* at the top; the owner turned the S23 to landscape | The same line at the top, now on one line, *Couplet 2* below it (FR-DISP-04, D-42) |

**Fixed during the test:** the chip, centred at the bottom as D-29 says, touched the round *next* button on the S23's 1080-pixel width; it is now centred in the width left of the buttons (guiDesign v0.44).

**Not tested:** turning back to portrait, rotation while browsing, rotation on the leader's page (not built there).
