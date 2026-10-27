# The install panel – 2026-10-27

guiDesign D-83 as added in v0.42; FR-DIST-01 to FR-DIST-04. On the A52, debug build. **Result: the panel shows and shares the install address from both places, and its QR code decodes to that address.**

| Step | Result |
|---|---|
| *Over* → *Zangbundel op een andere telefoon* | Full-screen white panel at full brightness: *Zangbundel installeren*, the QR code, `https://pe1mew.github.io/Zangbundel/j/` as text, the note on internet and installing before the trip, *Link delen* |
| The QR code, decoded from the screenshot with ZXing core 3.5.4 | `https://pe1mew.github.io/Zangbundel/j/` |
| *Link delen* | The system share sheet with the address and the page title *Zangbundel – meezingen* |
| Back, twice | The share sheet closes, then the panel; *Over* again |
| Campfire started, drawer → *App installeren* | The same panel, under the join rows |

**Not tested:** scanning the code with another phone's camera app (the join page without join data was checked in the code: it shows only the install section, and loads nothing from other hosts), and the Play link on the page, since the app is not on Play yet.
