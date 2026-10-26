# The singer's toolbar, tap-to-scroll and follow the leader – 2026-10-26

guiDesign v0.38, D-17 and D-70 as built; FR-SING-11, FR-SING-12, FR-SING-15, FR-DISP-01, FR-DISP-05, FR-DISP-07, FR-DISP-08. The A52 led a campfire with the setlist *Voorbeeldavond*; the S23 and the Huawei sang. Driven over adb. **Result: every control did what D-17 and D-70 describe; no crash on any of the three phones.**

| Step | Phone | Result |
|---|---|---|
| Tap on the header strip | S23, Huawei | The toolbar opened under the strip and closed 3 s after the last touch |
| *Groter* twice | S23 | 28 sp to 48 sp; *Groter* greyed at the largest size |
| *Dag*, then *Kleiner* twice | S23 | White page, black text, dark status-bar icons, light round buttons; the toolbar stayed dark; back to 28 sp |
| *Liedinfo* | S23 | The sheet showed title, *Tekst*, *Muziek*, *Auteursrecht* with ©, *Licentie*, *Bron* from the song file |
| Push of *Advocaatje*, follow on | S23, Huawei | Both pages went to the new song |
| Huawei: *Leider volgen* off; S23: scrolled by hand; push of *Daar was laatst een meisje loos* | S23 | Went to the new song: the push resumes a paused follow (D-70 as built) |
| | Huawei | Stayed on *Advocaatje*; the orange bar moved off it |
| Two taps on the lower half, one on the upper | S23 | Two lines down, one line up |
| Reinstall and rejoin | S23 | The settings came back: night theme, 28 sp; the toolbar's sun drawn as a plain glyph after the fix below |

**Found and fixed during the test:** the sun on the theme button was drawn as a yellow colour emoji, a bright spot at the fire (D-15); it now asks for the text glyph (U+FE0E).

**Seen, not a fault:** taps on the lyrics while the toolbar had already closed scrolled the page, which paused following, as FR-SING-12 requires. The reinstall killed the S23 without a leave notice, so the leader counted it twice (*3 zangers*), as option B of `design/proposals/2026-10-26-singer-presence.md` accepts.

**Not tested:** brightness slider by drag (only shown), *Scherm blijft aan* off with the screen timeout, landscape.
