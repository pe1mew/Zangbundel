# Swiping between songs in the songbook – 2026-10-28

guiDesign D-85 (v0.61) on the debug build of the working tree, on the A52 (Android 14), app in Dutch, the songbook holding the *Zangbundel bibliotheek* and a demo library. Swipes were made with `adb shell input swipe` across the middle of the screen, 250 ms. **Result: pass.**

| Check | How | Result |
|---|---|---|
| Build | `./gradlew assembleDebug lintDebug` | **Pass** |
| Next and previous | Songbook A–Z, no filter; *Advocaatje* opened; swipe left, left, right, right, right | **Pass.** *Advocaatje* → *Daar was laatst een meisje loos* → *De boer had maar ene schoen* → *Daar was laatst een meisje loos* → *Advocaatje* → *Advocaatje*: the first song stops there |
| The list as shown | Search *koning*, one song left; it opened; swipe left and right | **Pass.** It stays on *De koning van Siam*: the swipe walks the filtered list only |
| Opened from a memory | *Herinneringen* → *Kampvuur 28 okt* (five songs) → *Advocaatje*; swipe left and right | **Pass.** It stays on the song: a memory's song is one page |
| D-53's button | After the swipes | *Toevoegen aan setlist…* below the song on screen |

**Not tested:** the last song stopping (the same rule as the first, by the pager's page count); a rotation while swiping; the button naming a setlist being edited across a swipe; the Huawei.
