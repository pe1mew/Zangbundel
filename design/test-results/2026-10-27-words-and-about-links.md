# The two words explained, and links on *Over* – 2026-10-27

guiDesign v0.56: D-36 (a tap on the two words in the singer's strip explains them) and section 7 (the repository link in the version tile, the manual button). Debug build of the working tree on the Huawei (`HYFDU19311003605`), Dutch UI; the A52 (Play build 0.1.0) led *spinazie-borstel*. `./gradlew assembleDebug check` passed. **Result: pass.**

| Check | Result |
|---|---|
| The words in the strip | The Huawei joined from the list; the strip read *Kampvuur 27 okt · spinazie-borstel · verbonden* with the words underlined. **Pass** |
| Tap on the words | The dialog *De twee woorden* opened with the four paragraphs and *Sluiten*; *Sluiten* closed it. Five taps across the strip's height: on the words (y 126 and 140 of 2340) it opened each time; at y 70, 100 and 108, the top of the enlarged touch area, it did not, because those taps land in the status bar's area, which Android keeps. A finger aimed at the words hits them. **Pass** |
| Tap elsewhere, long press | Not retested: the strip's own tap (toolbar) and long press (reception grid, D-81) are unchanged in the code; only the words' text got its own click |
| Repository link | *Over*: the version tile ends with `github.com/pe1mew/Zangbundel`, underlined in orange; a tap opened Chrome on the repository's page. **Pass** |
| *Handleiding* | Below *Wat gebeurt er met mijn gegevens?*, with *Opent in je browser, op GitHub.* A tap opened Chrome on `https://github.com/pe1mew/Zangbundel/wiki/Handleiding`; the wiki has no pages yet, so GitHub answers `302` to `/wiki` and the browser showed the repository. The English page `wiki/Manual` behaves the same (`curl`). The English UI was not run on a phone. **Pass** for what can be checked before the wiki exists |

**Owner's wording not used:** the request described the two words as identifying a one-time encryption of all of the campfire's traffic, making a fake campfire or a break-in impossible. The protocol signs the lyrics and does not encrypt them, only personal data is sealed, and the words carry 16 bits (BLE 6.6), so the dialog says what holds instead (guiDesign D-36 v0.56).

**For the owner:** create the wiki pages `Handleiding` (Dutch) and `Manual` (English) with exactly these names, and restrict wiki editing to collaborators (assessment SP-32).
