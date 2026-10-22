# License Information

Four kinds of material meet in this project, and each is licensed differently.

## Code license: Apache License 2.0

The code in this repository (today: `scripts/`; later: the app under `android/` and any other platform directories) is licensed under the **Apache License 2.0**. In short:

- **Use, modify and redistribute** the code, also commercially, under the terms of the Apache License 2.0.
- **Attribution**: keep the licence text, the [NOTICE](NOTICE) file and the copyright notices in any copy or derivative, and mark files you changed.
- **No warranty**: the code is provided as is.

The full text is in the [LICENSE file](LICENSE).

**Why not the Commons Clause any more.** Until 2026-10-20 the code also carried the Commons Clause License Condition v1.0, which withheld the right to sell the software. The app is to be distributed through F-Droid, which includes only free and open-source software, and both the OSI and the FSF regard a licence with the Commons Clause as not free. The condition was therefore removed; `docs/FDROID-RUNBOOK.md` records the reasoning. No contributions from others existed at that date, so the owner could relicense alone.

## Content license: Creative Commons BY-NC 4.0

The writing and other non-code content in this repository, including the design documents under `design/`, this documentation and any graphics other than the app artwork listed in the next section, are licensed under the **Creative Commons Attribution-NonCommercial 4.0 International License (CC BY-NC 4.0)**.

You may:

- **Share**: copy and redistribute the content in any medium or format for non-commercial purposes.
- **Adapt**: remix, transform and build upon the content for non-commercial purposes.

Under these conditions:

- **Attribution**: give appropriate credit, provide a link to the licence and indicate if changes were made, but not in any way that suggests the licensor endorses you or your use.
- **Non-commercial**: the content may not be used for commercial purposes.

Full text: https://creativecommons.org/licenses/by-nc/4.0/

## App artwork: Creative Commons BY-SA 4.0

The artwork that ships inside the app or on its store listings is licensed under the **Creative Commons Attribution-ShareAlike 4.0 International License (CC BY-SA 4.0)**, so the app is free in its assets as well as its code and F-Droid needs no *NonFreeAssets* label (`docs/FDROID-RUNBOOK.md` Step 4). Licensed this way since 2026-10-20; before that these files were CC BY-NC 4.0 with the rest of the content.

| File | What it is |
|---|---|
| `design/icon/zangbundel-icon.svg` | App icon master ("Vuurboek in de kring", since 2026-10-22) |
| `design/icon/ic_launcher_foreground.svg`, `design/icon/ic_launcher_background.svg` | Adaptive launcher icon layers |
| `design/icon/feature-graphic.svg` | Feature graphic master |
| `docs/store-listing/graphics/icon-512.png`, `docs/store-listing/graphics/feature-1024x500.png` | Store icon and feature graphic rendered from the masters |
| `design/gui/20260403-194850-MN-8966.jpg`, `design/gui/Backdrop_001.pdn`, `design/gui/Backdrop_001.png`, `design/gui/Backdrop_001-land.png`, `design/gui/backdrop-1440x3200.webp`, `design/gui/backdrop-3200x1440.webp` | Background photograph (taken by the owner, April 2026), its Paint.NET project, masters and shipped exports; licensed CC BY-SA 4.0 since 2026-10-20 |

Copies and renders of these files, such as the launcher icons in the Android project and the images under `fastlane/metadata/android/`, carry the same licence. The rejected proposals in `design/icon-proposals/` stay under the content licence above.

You may share and adapt this artwork for any purpose, also commercially, under these conditions:

- **Attribution**: credit "Remko Welling, PE1MEW", link to the licence and indicate if changes were made.
- **ShareAlike**: distribute adaptations under the same licence.

Full text: https://creativecommons.org/licenses/by-sa/4.0/

**The background photograph** was taken by the owner, who holds the copyright; the children in it cannot be recognised, so no consent is needed (`design/gui/README.md`, `design/guiDesign.md` D-09). By the owner's decision of 2026-10-20 it follows the licence of the app artwork and is listed in the table above. Other preview and documentation files in `design/gui/` (`preview.html`, `README.md`, the diagrams) stay under the content licence.

## Song lyrics: not in this repository

This repository contains **no song lyrics** and does not accept any. Songs live in separate library repositories in the format defined by `design/song-format.md`. Each library and each song carries its own licence and attribution metadata, which the app shows to its users. The maintainers of a library are responsible for having the right to distribute its contents; this project's licences say nothing about the lyrics.

---

*By using this repository, you agree to comply with the licensing terms described above.*
