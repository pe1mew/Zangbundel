# The reading view's round buttons, and English on a phone – 2026-10-28

guiDesign D-85 as amended in v0.62 (three of D-30's round buttons at the bottom of the songbook's reading view) and the app in English, on the 0.2.0 working tree. **Result: pass.**

| Check | How | Result |
|---|---|---|
| The buttons | A52 (Android 14), debug build, app in Dutch; songbook A–Z, *Advocaatje* opened | **Pass.** At the bottom, centred over the lyrics: chevron left, ⓘ, chevron right, translucent; the chevron left is greyed on the first song; the ⓘ beside the title is gone |
| Next, previous, info | Taps on the buttons | **Pass.** Next: *Daar was laatst een meisje loos*, then *De boer had maar ene schoen*; previous: back to *Daar was laatst…*; ⓘ opens the song-info sheet (*Tekst*, *Muziek*, *Licentie*), and Back closes it on the same song |
| Dimming | No touch for three seconds | **Pass.** The buttons dim and the lyrics show through, as the singer's column does (D-30) |
| English, chosen in the app | Huawei (Android 10, phone in Dutch), debug build: *Instellingen* → *Taal* → *English* | **Pass.** Settings, home, songbook and library screens in English (*Settings*, *Language*, *Songbook*, *Add Zangbundel bibliotheek*, *Release found*) |
| The two words in English | The A52's `fieldtest` build leading *Gtestvuur* in Dutch (*knoflook-koper*); the Huawei in English on its home screen, then joined | **Pass.** The Huawei lists *Gtestvuur* as *garlic-copper · Close by* and shows *garlic-copper* in its bar after joining: word 225 and word 58 in each language |
| Generated labels | The pushed Dutch song on the English singer | **Pass.** The refrain is headed *Chorus*, in the app's language over a Dutch song (D-77) |

The Huawei was set back to *Automatisch* and its test name removed afterwards.

**Not tested:** the buttons on the Huawei: it has no library in its debug build, and adding one asks to accept the library's licence, which is the owner's to accept; the English fallback of a phone set to a language the app lacks (the owner reports it through an issue).
