# GUI assets: background image (*achtergrondafbeelding*)

The shared background of the welcome and configuration screens, as specified in `design/guiDesign.md` section 5 (decisions D-08 to D-14).

| File | What it is | Ships in the app |
|---|---|---|
| `20260403-194850-MN-8966.jpg` | Source photograph, Nikon Z f, 4032 × 6048, exported from Lightroom. EXIF credits "BOG & MN" as artist and copyright holder; no GPS data. | No |
| `Backdrop_002.pdn` | Paint.NET project, **current** since 2026-10-27: a wider crop of the source in the 1440 × 3200 portrait frame, showing the fire as well as the two children with the lit songbook. Open this to change the portrait image. | No |
| `Backdrop_002.png` | **Portrait master**, 1440 × 3200, RGB, lossless, exported from Paint.NET by the owner (2026-10-27). The reference for the shipped portrait file. | No |
| `Backdrop_002-land.png` | **Landscape master**, 3200 × 1440, RGB, lossless: rows 900–2300 of `Backdrop_002.png` (the fire, the children and the lit songbook) scaled to 1481 × 1440 and placed at the right of a black canvas, built with the command under "Export procedure" (2026-10-27). | No |
| `Backdrop_001.pdn`, `Backdrop_001.png`, `Backdrop_001-land.png` | The previous portrait project and master (2026-10-19, a closer crop without the fire) and the previous landscape master (a band of the source photograph); kept for reference, no longer exported. | No |
| `backdrop-1440x3200.webp` | **Shipped portrait asset**, from `Backdrop_002.png` since 2026-10-27: lossy WebP, quality 80, 52 KB, no metadata. Goes in `res/drawable-nodpi/` on Android as `backdrop.webp` (D-11, D-12). | Yes |
| `backdrop-3200x1440.webp` | **Shipped landscape asset** (D-13), from `Backdrop_002-land.png` since 2026-10-27: lossy WebP, quality 80, 50 KB, no metadata. Goes in `res/drawable-land-nodpi/` on Android as `backdrop.webp`. | Yes |
| `preview.html` | Contact sheet: the portrait image centre-cropped in 16:9 and 20:9 frames and the landscape image in a 2400 × 1080 frame, with the D-14 scrim and sample titles. Open in a browser. | No |
| `leader-mode-sequence.puml`, `.svg`, `.png` | Sequence diagram of leader mode for assessment, from app start to the end of a campfire, in 14 sections: app start and resume after a crash, libraries, songbook, setlists, starting, singers joining, the QR code, leading the songs (*Nu*, re-push, language variant, *Wis*), changing the setlist, requests, an *assistent*, the leader bar with live statistics and warnings, singers leaving, ending with summary, memory and statistics sharing. Participants: the leader, the app's screens and core, phone storage, the library host, the BLE layer, singer apps and an assistent's app. Cites `guiDesign.md` v0.8 decisions, BLE v0.12 sections and FRD v0.10 requirements; a closing note lists what phase 1 deliberately leaves out. Render as the flowchart below, once with `-tsvg` and once with `-tpng`. | No |
| `singer-mode-flow.puml`, `.svg`, `.png` | Flowchart of singer mode for assessment (PlantUML activity diagram; `guiDesign.md` sections 4, 6.1, 6.2, decisions D-19 to D-47). Rendered with PlantUML 1.2024.8 on Java 8: `java -DPLANTUML_LIMIT_SIZE=16384 -jar plantuml.jar -charset UTF-8 -tsvg singer-mode-flow.puml`, then the same command with `-tpng` (PlantUML honours only the last `-t` flag, so one command with both writes only the PNG; the charset flag matters on Java 8, which otherwise reads the file as cp1252 and garbles the dashes). The event loop is drawn as a ladder of independent checks so the diagram stays narrow. The `.puml` is the source; re-render both files after editing it. | No |

## Rights and people

The photograph was taken on 3 April 2026 and is credited in its EXIF to "BOG & MN". It shows two children from behind, reading a songbook by the fire, and silhouettes of others; no face is visible.

- [x] **Rights.** The owner, Remko Welling, took the photograph in April 2026 and holds the copyright (recorded 2026-10-20 on the owner's statement). Every file in this folder derives from it.
- [x] **Consent.** Not needed: the children are seen from behind and cannot be recognised, which is the owner's judgement under D-09 as amended in `guiDesign.md` v0.9 (consent only for recognisable people). Recorded 2026-10-20.

- [x] **Licence.** The photograph and every file derived from it in this folder (`20260403-194850-MN-8966.jpg`, `Backdrop_001.pdn`, `Backdrop_001.png`, `Backdrop_001-land.png`, `Backdrop_002.pdn`, `Backdrop_002.png`, `Backdrop_002-land.png`, `backdrop-1440x3200.webp`, `backdrop-3200x1440.webp`) follow the licence of the app artwork: **CC BY-SA 4.0**, credit "Remko Welling, PE1MEW" (owner's decision 2026-10-20; `license.md`, section "App artwork"). F-Droid therefore needs no *NonFreeAssets* label for it.

The photograph may ship in the app in every channel.

## Measurements (2026-10-27, from `Backdrop_002.png`)

Measured with Pillow on a copy downscaled by 4; relative luminance per WCAG (0 = black, 1 = white); the D-14 scrim applied per row for the contrast.

| Measure | Value | Rule | Result |
|---|---|---|---|
| Average luminance | 0.013 | below 0.35 (D-10) | pass |
| Top third / middle third / bottom third | 0.003 / 0.034 / 0.003 | top third the darkest part (D-10) | pass (equal darkest with the bottom third; both are black canvas) |
| Bright rows (the fire, the lit songbook page) | fire rows 1040–1360, songbook rows 2000–2240 | inside the centre band (5.3) | pass |
| Contrast of white text under the D-14 scrim, lightest point (the fire, about column 1180, row 1160) | 5.7 : 1 | ≥ 4.5 : 1 (AA), 7 : 1 SHOULD | pass; below the SHOULD, as `Backdrop_001` (5.6 : 1) |
| Contrast, darkest point | 21 : 1 | | pass |
| Shipped file size | 52 KB | under 300 KB (D-12) | pass |

Body text keeps the solid 85 % panel of D-14; on the home screen the fire sits behind the search panel.

## Measurements, landscape (2026-10-27, from `Backdrop_002-land.png`)

| Measure | Value | Rule | Result |
|---|---|---|---|
| Average luminance | 0.014 | below 0.35 (D-10) | pass |
| Top 180 rows (status bar) | 0.008 | kept free of detail (D-13) | pass: dark silhouettes only |
| Light source inside columns 320–2880 (D-13) | the lit songbook at about column 2240; the fire at columns 2650–2950 | fire or lit songbook inside | pass, by the songbook |
| Contrast of white text under the D-14 scrim, lightest point (the fire, about column 2930, row 270) | 4.9 : 1 | ≥ 4.5 : 1 (AA), 7 : 1 SHOULD | pass; below the SHOULD (`Backdrop_001-land` had 7.9 : 1) |
| Contrast, darkest point | 21 : 1 | | pass |
| Shipped file size | 50 KB | under 300 KB (D-12) | pass |

Two other bands of `Backdrop_002.png` were measured and not taken: rows 1300–2380 (the children and the songbook only, as the previous landscape) reaches only 4.3 : 1 at the lit hair at the top edge, below the AA MUST; rows 900–1980 (fire and children) passes at 5.2 : 1 but cuts off the songbook.

The landscape text sits on the black left part, so in use the lightest point is never behind text.

## Export procedure

1. Edit the `.pdn` in Paint.NET; keep the canvas at 1440 × 3200 (portrait) or 3200 × 1440 (landscape) and the guard zones of `guiDesign.md` section 5.3 and D-13.
2. Save the PNG master next to it.
3. Export the WebP without metadata:

```bash
python -c "from PIL import Image; Image.open('design/gui/Backdrop_002.png').convert('RGB').save('design/gui/backdrop-1440x3200.webp','WEBP',quality=80,method=6)"
```

   The landscape master is built from the portrait master (since 2026-10-27; before, from a band of the source photograph) and exported the same way:

```bash
python -c "from PIL import Image; s=Image.open('design/gui/Backdrop_002.png').convert('RGB'); b=s.crop((0,900,1440,2300)).resize((1481,1440),Image.LANCZOS); c=Image.new('RGB',(3200,1440),(0,0,0)); c.paste(b,(3200-1481,0)); c.save('design/gui/Backdrop_002-land.png','PNG',optimize=True); c.save('design/gui/backdrop-3200x1440.webp','WEBP',quality=80,method=6)"
```

4. Re-measure (average luminance, top third, white-text contrast under the D-14 scrim at the lightest point) and update the table above; check `preview.html` by eye.
5. Copy both WebP files into the app as `android/app/src/main/res/drawable-nodpi/backdrop.webp` and `drawable-land-nodpi/backdrop.webp`.
6. Add a line to `changelog.md`.
