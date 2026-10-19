# GUI assets: background image (*achtergrondafbeelding*)

The shared background of the welcome and configuration screens, as specified in `design/guiDesign.md` section 5 (decisions D-08 to D-14).

| File | What it is | Ships in the app |
|---|---|---|
| `20260403-194850-MN-8966.jpg` | Source photograph, Nikon Z f, 4032 × 6048, exported from Lightroom. EXIF credits "BOG & MN" as artist and copyright holder; no GPS data. | No |
| `Backdrop_001.pdn` | Paint.NET project: the crop and edit of the source into the 1440 × 3200 portrait frame. Open this to change the portrait image. | No |
| `Backdrop_001.png` | **Portrait master**, 1440 × 3200, RGB, lossless, exported from Paint.NET. The reference for the shipped portrait file. | No |
| `backdrop-1440x3200.webp` | **Shipped portrait asset**: lossy WebP, quality 80, 64 KB, no metadata. Goes in `res/drawable-nodpi/` on Android (D-11, D-12). | Yes |
| `Backdrop_001-land.png` | **Landscape master**, 3200 × 1440, RGB, lossless: the full-width band of the source (rows 1700–4724) scaled to 1920 × 1440 and placed at the right of a black canvas, as composed by the owner in Paint.NET and rebuilt with the command under "Export procedure". | No |
| `backdrop-3200x1440.webp` | **Shipped landscape asset** (D-13): lossy WebP, quality 80, 50 KB, no metadata. Goes in `res/drawable-land-nodpi/` on Android. | Yes |
| `preview.html` | Contact sheet: the portrait image centre-cropped in 16:9 and 20:9 frames and the landscape image in a 2400 × 1080 frame, with the D-14 scrim and sample titles. Open in a browser. | No |

## Rights and people

The photograph was taken on 3 April 2026 and is credited in its EXIF to "BOG & MN". It shows two children from behind, reading a songbook by the fire, and silhouettes of others; no face is visible (D-09 as amended in guiDesign.md v0.3). Before the image is published (pushed to the public repository or shipped), the owner records here:

- [ ] the project holds the right to use the photograph under the repository's content licence (CC BY-NC 4.0, see `license.md`) or a licence from the photographer: *not yet recorded*;
- [ ] the consent of the parents of the children in the picture: *not yet recorded*.

## Measurements (2026-10-19, from `Backdrop_001.png`)

Measured with Pillow on a downscaled copy; relative luminance per WCAG (0 = black, 1 = white).

| Measure | Value | Rule | Result |
|---|---|---|---|
| Average luminance | 0.019 | below 0.35 (D-10) | pass |
| Top third | 0.004 | darkest part of the frame (D-10) | pass |
| Middle third / bottom third | 0.039 / 0.016 | | |
| Bright rows (mean luminance above 0.05: the fire and the lit songbook page) | rows 1060–2410 | fire visible in the centre band rows 1275–1925 (5.3 table) | pass; the band shows the fire's lower half and the silhouettes, which is why the landscape export (D-13) replaces this crop |
| Contrast of white text under the D-14 scrim, lightest point | 5.6 : 1 | ≥ 4.5 : 1 (AA) | pass; below the 7 : 1 SHOULD |
| Contrast, darkest point | 21 : 1 | | pass |
| Shipped file size | 64 KB | under 300 KB (D-12) | pass |

The lightest point is the lit page of the songbook in the lower half; a title placed over it would read at 5.6 : 1, which is why body text gets the solid 85 % panel of D-14.

## Measurements, landscape (2026-10-19, from `Backdrop_001-land.png`)

| Measure | Value | Rule | Result |
|---|---|---|---|
| Average luminance | 0.008 | below 0.35 (D-10) | pass |
| Top 180 rows (status bar) | 0.005 | kept free of detail (D-13) | pass |
| Left third / centre 1440 columns / right third | 0.000 / 0.014 / 0.005 | light source inside columns 320–2880 (D-13) | pass: the lit songbook sits at about column 1900 |
| Contrast of white text under the D-14 scrim, lightest point (top edge, column ≈ 2765) | 7.9 : 1 | ≥ 4.5 : 1 (AA), 7 : 1 SHOULD | pass |
| Contrast, darkest point | 21 : 1 | | pass |
| Shipped file size | 50 KB | under 300 KB (D-12) | pass |

With the band starting 100 rows higher in the source (row 1600) the lit hair at the top right met the lightest part of the scrim at 4.3 : 1, which is why the composition starts at row 1700.

## Export procedure

1. Edit the `.pdn` in Paint.NET; keep the canvas at 1440 × 3200 (portrait) or 3200 × 1440 (landscape) and the guard zones of `guiDesign.md` section 5.3 and D-13.
2. Save the PNG master next to it.
3. Export the WebP without metadata:

```bash
python -c "from PIL import Image; Image.open('design/gui/Backdrop_001.png').convert('RGB').save('design/gui/backdrop-1440x3200.webp','WEBP',quality=80,method=6)"
```

   The landscape master can also be rebuilt from the source without Paint.NET, then exported the same way:

```bash
python -c "from PIL import Image; s=Image.open('design/gui/20260403-194850-MN-8966.jpg').convert('RGB'); b=s.crop((0,1700,4032,4724)).resize((1920,1440),Image.LANCZOS); c=Image.new('RGB',(3200,1440),(0,0,0)); c.paste(b,(1280,0)); c.save('design/gui/Backdrop_001-land.png','PNG',optimize=True); c.save('design/gui/backdrop-3200x1440.webp','WEBP',quality=80,method=6)"
```

4. Re-measure (average luminance, top third, white-text contrast under the D-14 scrim at the lightest point) and update the table above; check `preview.html` by eye.
5. Add a line to `changelog.md`.
