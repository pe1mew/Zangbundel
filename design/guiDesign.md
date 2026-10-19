# Zangbundel – GUI Design

| Field | Value |
|---|---|
| Document | GUI design: screen families, welcome screen, backgrounds, themes, typography |
| Project | Zangbundel – campfire songbook app |
| Document version | 0.5 (draft) |
| Date | 2026-10-19 |
| Author | Remko Welling |
| Status | Draft for review |
| Depends on | `design/functional-requirements.md` v0.8, `design/icon/` (app icon decided 2026-10-18) |

| Version | Date | Change |
|---|---|---|
| 0.1 | 2026-10-19 | First draft: the two screen families, the welcome screen, the shared background image and its size, themes, scrim and contrast rules, typography, system bars, version display. Decisions D-01 to D-18, open questions Q1–Q5. |
| 0.2 | 2026-10-19 | Section 4: the home screen after the welcome screen (join prominently when gatherings are in range, start discreetly) and the two top-level modes, leader and singer, that the rest of the GUI is written for. Decisions D-19 to D-26. Later sections renumbered. |
| 0.3 | 2026-10-19 | The background image exists: a photograph by the owner, master `design/gui/Backdrop_001.png` (Paint.NET project alongside), shipped export `backdrop-1440x3200.webp` (64 KB). Measurements in `design/gui/README.md`. D-09 amended: people seen from behind or as silhouettes are allowed, faces are not; children only with recorded parental consent. D-13 amended: a separate 3200 × 1440 landscape export, made the same day (`Backdrop_001-land.png`, `backdrop-3200x1440.webp`, Q9 resolved). Q1 resolved (photograph). Q8 records the consent and credit still to be written down. |
| 0.4 | 2026-10-19 | Section 6.1, singer mode lyric view: maximum screen use, wrapping and fit-to-width (FR-DISP-06 evaluated), swipe scrolling through the whole setlist with the previous and next song at the ends, three round buttons (previous song, songlist, next song), songlist sheet with jump-to-song and likes, likes sent to the leader as grouped requests, leaving singer mode. D-27 to D-34; D-17 amended (tap halves scroll, the toolbar opens from the header strip). Q10–Q13, Q13 being the magnifier mode to investigate. |
| 0.5 | 2026-10-19 | Section 6.2, the rest of singer mode: joining from a QR code, the fingerprint in the header strip, reconnecting and ended states, the leader's download instruction, the request tab and the free-text request, one heart and one star, the library while singing, variants, rotation, the new-song cue, display name, the song-info sheet, the delegate role as a singer sees it, room for karaoke. D-35 to D-47; D-47 fixes that a singer sees only the songs sent over BLE in the running session (D-29, D-30, D-31 adjusted). Q14–Q17; leader mode is still to be written (Q14). Flowchart `design/gui/singer-mode-flow.puml` with SVG and PNG renders. D-48, the design objective that the phone is not the attraction, placed at the head of section 2 with the table of features it holds back; D-38, D-39, D-40 and D-41 trimmed accordingly. |

---

## 1. Introduction

### 1.1 Purpose

This document records the **visual design decisions** of the Zangbundel app: which screens look alike, what the welcome screen shows, how the background image is used and sized, how the two themes behave, and the rules for text, contrast and system bars. It is the document the FRD's section 19 lists as `design/ui-flows.md` ("screen flows for leader and singer, day/night theme guidelines"), under its final name; screen-by-screen flows will be added to it in a later version.

Every decision is numbered **D-nn** so that later documents, code comments and reviews can refer to it. A decision that turns out wrong is replaced by a new one and the old one is marked *Superseded*, as the FRD does with requirements.

### 1.2 Scope

In scope: the welcome screen, the home screen and the two top-level modes, the configuration screens (first-use introduction, permission explanations, privacy screen, settings, library management, About), the gathering screens (lyric view and everything shown at the fire), the background image, themes and colours, typography, contrast, touch targets, system bars.

Out of scope: the content of each screen's texts (localisation files), the architecture (which UI toolkit), and the app icon, which is decided in `design/icon/`.

### 1.3 Conventions

MUST / SHOULD / MAY as in RFC 2119. Requirement IDs in brackets point to the FRD. Dutch UI terms in parentheses. Sizes in **dp** (density-independent pixels) for layout and **sp** for text, as on Android; an iOS implementation maps them to points one to one.

---

## 2. Two screen families

The app has two kinds of screens that look deliberately different, so a user can tell at a glance whether they are *setting up* or *singing*.

| Family | Screens | Background | Where it is used |
|---|---|---|---|
| **Configuration** (*instellen*) | Welcome screen, first-use introduction [FR-GEN-06], permission explanations [FR-GEN-05], privacy screen [FR-GEN-08], home screen (join or start, section 4), settings, library management and import reports, About [FR-GEN-07] | One shared background image under a dark scrim (section 5), white text | Before and between gatherings, mostly with light around |
| **Gathering** (*samenzingen*) | Lyric view, setlist, song requests, gathering list and join dialogs, leader controls, delegate screens, "what changed" after an update | No image. Night theme: white text on pure black. Day theme: black text on pure white [FR-DISP-01] | At the fire, often in the dark |

**D-48 — The phone is not the attraction.** *(Added in v0.5; a design objective that ranks above every later decision in this document.)* Singers are children at a campfire, and the app's job is to put the words in front of them and then get out of the way. Every feature on a singer screen is judged by one question: does it give a child a reason to look at the phone instead of singing? Features that invite fiddling, collecting, competing or exploring are held back or left out, however small: no counters, scores, badges or streaks; no animations beyond the one-second cue for a new song; no sounds; no social feedback (a singer never sees how many others liked a song); no customisation beyond what reading in the dark needs; and nothing to browse or zoom that the leader did not send. When a requirement of the FRD can be met in a plainer or a richer way, the plainer way wins; a richer way needs a reason written next to it. The table below lists what this objective has already held back. *Why:* the owner's objective for the singer side, 2026-10-19: minimise the incentives for a child to start playing with the app and the phone, and keep the attention on the singing. A feature that is fun to build is the easiest way to lose that.

| Held back by D-48 | Instead |
|---|---|
| "+1" on other singers' requests (FR-REQ-07, MAY) | Not in phase 1; the request tab shows only the singer's own requests (D-38). |
| Favourites star in singer mode (FR-LIB-32, SHOULD) | The star exists only in the library outside a gathering; the *Liedboek* tab shows no star while joined (D-39, D-40). |
| Variant chip on the song title (D-41) | The variant switch lives in the song-info sheet (D-45), one layer down. |
| Like counts, "most liked" lists, request leaderboards | Never shown to singers; the leader sees the counts (D-32). |
| Magnifier mode (Q13) | Zooming and panning is play; the investigation must show a readability gain that the text-size steps cannot give, or the idea is dropped. |
| Themes, colours, fonts to choose from | Day and night only (D-15); the system font (D-18). |
| Reading ahead in the setlist | Only songs the leader sent (D-47). |

**D-01 — Two visual families.** Configuration screens share one background image; gathering screens never show an image. The border between the families is the moment a gathering starts or is joined, and the moment it ends. *Why:* at the fire the display must not dazzle [FR-DISP-03], must read at arm's length [FR-DISP-05] and must spend as little battery as possible [NFR-PERF-02]; a photograph works against all three. Before the gathering, the image gives the app a face and makes the setup screens recognisable.

**D-02 — Night is the campfire default.** When a gathering starts or is joined, the gathering screens open in the night theme unless the user set the theme to day or to "follow the device" [FR-DISP-02]. The one-action theme switch stays in the lyric view [FR-DISP-01]. *Why:* a campfire is dark; a white screen is the most common complaint about phone songbooks.

**D-03 — The theme setting also reaches configuration screens, but only as a scrim level.** In the day theme the background image is shown under a light scrim; in the night theme the configuration screens drop the image entirely and use the gathering family's black background. *Why:* FR-DISP-03 forbids large bright areas in the night theme, and a singer who opens Settings at the fire must not be dazzled. A photo "dimmed to 15 %" still shows highlights; dropping it is simpler, consistent, and cheaper on an OLED screen.

---

## 3. Welcome screen (*welkomstscherm*)

**D-04 — The welcome screen shows the app name, the app version and the icon artwork, nothing else.** Content, top to bottom, centred: the icon artwork from `design/icon/zangbundel-icon.svg` (the five singers around the songbook, without the launcher's rounded background) at 96 dp; the name **Zangbundel** in the display size; the version line `versie 1.0.0` / `version 1.0.0` in the body size, using `versionName` from the build (`docs/RUNBOOK.md` Decisions). No tagline, no tips, no progress bar, no "powered by".

**D-05 — Short and skippable.** The welcome screen is shown for at most **1.5 seconds** after the app is ready, or until the user taps it, whichever is first. It is never shown when the app is opened to resume a gathering from its resume data or from the join link, and never a second time while the app stays in memory. *Why:* a splash that delays the user is a cost; this one only makes the start recognisable and carries the version for support questions ("which version do you have?" is answered by restarting the app).

**D-06 — The system splash comes first.** Android 12 and later show a system splash with the launcher icon before any app content. The welcome screen is designed to follow it without a jump: the system splash background is the icon's night blue (`#0B2545`), and the welcome screen opens with the icon artwork at the same position, then fades in the background image, name and version over 300 ms. Users with reduced motion enabled get the final frame at once. *Why:* two unrelated splashes in a row look broken.

**D-07 — First use continues from the welcome screen.** On the first start, the welcome screen is followed by the first-use introduction [FR-GEN-06], which links to the privacy screen before any permission is requested [FR-GEN-08]. On later starts it is followed by the home screen (section 4). The introduction and the privacy screen are configuration screens and use the same background.

---

## 4. Home screen and the two modes

### 4.1 Leader and singer

**D-19 — At the top level the app is in one of two modes, leader (*kampvuurleider*) or singer (*zanger*), or in neither.** Every installation can take either role [FR-GEN-01]; a device is never in both at once [FR-GEN-02]. The state the app shows at all times [FR-GEN-04] is one of: *idle* (home screen, no gathering), *searching* (home screen, scanning), *leading*, *joined*, *reconnecting* [FR-SING-04], *importing a library*. The rest of this document, and every screen flow added to it later, is written per mode: a gathering screen exists in a **leader variant** and a **singer variant**. The two variants share the lyric view [FR-LEAD-04], the setlist and the theme, text-size and brightness controls (D-17); they differ in the controls that act on the gathering. The leader variant adds push, next, skip, requests and the singer count [FR-LEAD-03, FR-GATH-12]; the singer variant adds leave [FR-GATH-07] and the request form. *Why:* one mental model for users and one navigation tree per role for implementers; a screen that is "almost the same" in both roles is one screen with role-dependent controls, never two screens.

**D-20 — The mode is always visible.** Every gathering screen carries a one-line header strip in the secondary text colour: in leader mode the gathering name and the number of singers (`Kampvuur 18 okt · 12 zangers`), in singer mode the gathering name and the connection state (`Kampvuur 18 okt · verbonden` / `· opnieuw verbinden…` / `· beëindigd`) [FR-SING-04, FR-GATH-09, FR-GATH-10]. In the lyric view this strip is the header of FR-DISP-11. The strip is the only place the mode is spelled out; the role is otherwise shown by which controls exist.

### 4.2 Home screen (*startscherm*): join or start

The home screen follows the welcome screen (D-05) and is the root of the configuration family: it is where the user leaves to become a leader or a singer, and where they return when a gathering ends.

**D-21 — Two actions, and nothing competes with them.** The home screen offers exactly two primary actions: **join a gathering** (*meezingen*) and **start a gathering** (*kampvuur starten*). Library, settings and About are reachable from a small row of icons at the bottom and never take space from the two actions. *Why:* FR-GEN-01 says the user chooses the role when starting or joining; this screen is that choice and nothing else.

**D-22 — Scanning starts on the home screen, and what it finds is the screen.** On entering the home screen the app scans for gatherings [FR-GATH-03] and keeps scanning while the screen is shown. The layout depends on the result:

| State | Join area (top, large) | Start area (bottom) |
|---|---|---|
| **Gatherings in range** | One card per gathering, the strongest signal first: name, signal strength or distance indication [FR-GATH-04], a lock icon when a join code is needed [FR-GATH-05]. Cards are tall (72 dp minimum) and fill the width; tapping one joins it. The header reads *Kampvuren in de buurt*. | A single small text button, *Zelf een kampvuur starten*, in the secondary text colour. Starting is refused while a gathering is listed, see D-23. |
| **None found yet** (first 5 seconds and later) | A quiet scanning indicator, *Zoeken naar kampvuren…*, and one line of help: scan the leader's QR code with the camera app to join [FR-GATH-23]. No empty list, no error. | The start button is large and primary (fire orange), since starting is the likely intention. |
| **Bluetooth off or permission missing** | A card that explains in plain language why Bluetooth (and, on Android 11 and lower, location) is needed and offers the system dialog [FR-GEN-05]; nothing is requested before the user taps. | Start button present but disabled with the same explanation. |
| **Resume available** [FR-GATH-13] | A card above the list: *Kampvuur 18 okt hervatten?* with resume and discard. | As above. |

QR-only gatherings [FR-GATH-21] are not in the list by design; they are joined through the QR code only, and a phone that opens the join link skips the home screen and goes straight to joining. *Why:* a singer at a fire should need one tap; a leader setting up usually has no gathering around and gets the start button where the thumb is. The two states are the same screen with the proportions reversed, not two screens.

**D-23 — The one-gathering rule is enforced on this screen.** FR-GATH-16 requires listening for at least 5 seconds before a gathering may start. Because the home screen is already scanning, the start action simply reads the current list: if the screen has been open for less than 5 seconds the start button shows the remaining time (*even luisteren… 3 s*); if a gathering is listed, starting is refused with the gathering's name and a join button, exactly as the requirement says. There is no separate "checking" dialog.

**D-24 — A device that cannot lead says so, once.** When the platform reports no BLE peripheral (advertising) support (FRD constraint 1), the start area shows *Dit toestel kan meezingen, maar geen kampvuur starten* instead of the button. The join area is unchanged.

**D-25 — Start leads into leader mode through one screen.** Tapping start opens the gathering settings screen (a configuration screen): the proposed neutral name [FR-GATH-01], the optional join code and the QR-only switch [FR-GATH-05, FR-GATH-21], and the setlist to begin with [FR-LEAD-02]. Its primary button *Starten* switches the app into leader mode and the gathering family: the lyric view (empty until the first song is pushed) with the leader controls and the join QR code one tap away [FR-DIST-02].

**D-26 — Join leads into singer mode through at most one screen.** Tapping a gathering card joins it directly when it is open; when it needs a join code, a dialog asks for the code first. The first join ever also asks for the optional display name [FR-SING-01] on the same dialog, and never again unless the user changes it in settings. After joining, the app switches into singer mode and the gathering family: the current song is shown at once [FR-GATH-06].

---

## 5. Background image (*achtergrondafbeelding*)

### 5.1 One image, one asset

**D-08 — One image for every configuration screen.** The welcome screen, the first-use introduction, the permission explanations, the privacy screen, the home screen, settings, library management and About all draw the same image, placed identically (centre-cropped to the full screen, section 5.3). The image carries no information: every screen reads the same with the image replaced by a flat colour. *Why:* one asset keeps the app small, which matters for offline sharing of the package [FR-DIST-05], and a single placement makes the screens feel like one app.

### 5.2 Content of the image

**D-09 — Subject, palette and people.** The image shows a campfire scene at dusk or night, in the palette of the app icon: night blue (`#0B2545` to `#13315C`) and fire orange (`#FF8A3D`, `#FFB36B`), with the dark surround taking most of the area and the fire as the light source. It contains **no recognisable faces**: people may appear seen from behind or as silhouettes, as singers at a fire do, but never with a face that can be matched to a person; no group names, badges, flags or logos, and no text. Where the people are children, the owner records the parents' consent in `design/gui/README.md` before the image is published. It is a photograph the project holds the rights to, or an illustration made for the project; the licence and credits are recorded in `design/gui/README.md`. *Why:* privacy by design (FRD 2.6) rules out identifiable minors, and faces are what makes a person identifiable; singers seen from behind are what the app is about; a neutral scene fits every scouting group; text in an image cannot be localised [FR-LANG-01]; the palette ties the screens to the icon. *(Amended in v0.3; v0.1 excluded people altogether.)*

**D-10 — The image is dark by itself.** Average luminance of the image is below 35 % and the top third of the frame, where titles sit, is the darkest part. *Why:* the scrim (D-13) then only has to guarantee contrast, not create it, and the image survives being dropped in the night theme without the screens changing character.

### 5.3 Size and format

The question to answer is: **what size should the background image be?** Phones in use range from 16:9 (older, 1080 × 1920) through 19.5:9 (1080 × 2340) and 20:9 (1080 × 2400, 1440 × 3200) to 22:9; tablets are 16:10 and 4:3. The image is always *centre-cropped* to fill the screen (Android `ContentScale.Crop` / `centerCrop`, iOS `scaledToFill`), so the one asset only has to be tall enough that no common phone scales it up.

**D-11 — The shipped image is 1440 × 3200 pixels, portrait, 9:20.** That is the largest common phone resolution (QHD+ at 20:9). On a 1080 × 2400 phone it is scaled down by 0.75; on a 1080 × 1920 phone the sides are kept and 320 rows top and bottom fall off; on a 1440 × 3200 phone it is shown 1:1. No phone scales it up. It is shipped **once, without density variants**: on Android in `res/drawable-nodpi/` (so the platform does not rescale it per density; the view crops it), on iOS as a single-scale image in the asset catalog. *Why:* a photograph behind a scrim does not benefit from per-density copies, and one file is the smallest download.

**D-12 — Format and weight.** The shipped file is lossy **WebP** at quality 80 (Android 8.0, the minimum SDK, decodes it natively; iOS 14 and later as well), target **under 300 KB**, hard limit 500 KB. The master is kept outside the app at its native resolution, in `design/gui/` with its licence note (`design/gui/README.md`): the source photograph, the Paint.NET project that holds the crop and edit, the 1440 × 3200 PNG master, and the WebP export derived from it by the documented procedure so it can be re-exported. If the image is an illustration rather than a photograph, it MAY be shipped as a vector (Android `VectorDrawable`, iOS PDF) instead and then has no pixel size; the composition rules below still apply.

**Safe area and composition.** Because the frame is cropped on wide, short and landscape screens, the subject is composed within guard zones:

| Zone (of the 1440 × 3200 frame) | Rule |
|---|---|
| Centre 1440 × 2560 (rows 320–2880) | Everything that matters is here: the fire, the horizon. This is what a 16:9 phone shows. |
| Top 400 rows and bottom 520 rows | Only sky and ground: may be cut on short screens; on tall screens the status bar and the bottom navigation or buttons cover them. |
| Centre band 1440 × 650 (rows 1275–1925) | What a landscape phone shows (section 5.4). The fire or the horizon must be visible here. |
| Left and right 180 columns | May be cut on tablets in portrait and in some landscape crops. |

**Tablets.** A 1600 × 2560 (10-inch, 16:10) tablet in portrait shows the image scaled to width 1600 (factor 1.11, a slight upscale that is invisible behind the scrim). Landscape on tablets, if a tablet layout is ever in scope (FRD Q7), gets its own 2560 × 1600 export from the same master (Q2 below).

### 5.4 Orientation

**D-13 — Configuration screens work in both orientations, with a separate landscape image.** *(Amended in v0.3.)* Landscape gets its own export, **3200 × 1440 pixels (20:9)**, composed from the same source photograph so both orientations show the same scene, shipped like the portrait one (WebP, quality 80, under 300 KB, no metadata) as `backdrop-3200x1440.webp` in `res/drawable-land-nodpi/` on Android and as the landscape trait variant in the iOS asset catalog. Its guard zones mirror the portrait ones: everything that matters within the centre 2560 × 1440 (columns 320–2880), the light source (fire or lit songbook) inside those columns, and the top 180 rows free for the status bar. The landscape composition puts the photograph at the right and leaves the left part black, so titles and buttons sit on black and the picture is never covered. Should the landscape export ever be missing, the portrait image is centre-cropped (a 2400 × 1080 screen then shows the central 1440 × 650 band, scaled by 1.67). *Why:* v0.1 accepted that crop to save one asset, but with the chosen photograph the centre band shows the dark middle of the scene rather than the fire, and a second 60 KB asset is cheaper than a poor landscape welcome screen. Portrait-locking the setup screens stays out of the question: it is hostile to tablets and to a mounted phone.

### 5.5 Scrim and contrast

**D-14 — A scrim guarantees contrast; the image never does.** Over the image lies a vertical gradient from `#0B2545` at 60 % opacity at the top to `#000000` at 75 % opacity at the bottom, plus, behind any block of body text (the privacy screen, an import report), a solid panel of `#0B2545` at 85 %. All text on configuration screens is white (`#FFFFFF`) or off-white (`#CFE6FF` for secondary text); the primary button is fire orange (`#FF8A3D`) with dark text (`#0B2545`). The contrast of white body text over the darkest and the lightest point of the scrimmed image MUST both exceed **4.5 : 1** (WCAG AA) and SHOULD exceed 7 : 1; the check is part of adding or replacing the image. *Why:* FR-DISP-10 demands AA in the lyric view; the configuration screens should not be worse, and a scrim with fixed opacities makes the result independent of the photograph.

---

## 6. Gathering screens

**D-15 — Pure black and pure white, nothing in between for backgrounds.** Night theme: background `#000000`, lyric text `#FFFFFF`, secondary text `#BFBFBF`, dividers `#2A2A2A`. Day theme: background `#FFFFFF`, lyric text `#000000`, secondary `#444444`, dividers `#DDDDDD`. Dialogs, lists, buttons and the system bars follow the theme [FR-DISP-03]. No image, no gradient, no card shadows. *Why:* FR-DISP-01 defines the themes by these colours; pure black switches OLED pixels off, which NFR-PERF-02 relies on for a four-hour evening.

**D-16 — The current line is marked by weight and a side bar, not by a bright area.** The current line [FR-DISP-09] is bold, and a 4 dp bar in fire orange (`#FF8A3D`) runs along its left edge; the text colour does not change, so the highlight survives colour-blindness and dimmed screens. In the night theme the bar is the only coloured element on the screen. The header and footer of FR-DISP-11 use the secondary text colour at the smallest text size.

**D-17 — The theme switch, text size and brightness are one tap away.** *(Amended in v0.4.)* A tap on the header strip (D-20), or a long press anywhere on the lyrics, shows a translucent toolbar for three seconds with: theme toggle [FR-DISP-01], text size smaller/larger [FR-DISP-05], fit-to-width toggle (D-28), brightness slider [FR-DISP-08], keep-screen-on toggle [FR-DISP-07], follow-the-leader toggle [FR-SING-12], and song info [FR-SING-15]. A single tap on the lyrics themselves is reserved for scrolling: lower half one line down, upper half one line up [FR-SING-11]; v0.1 had the toolbar on a single tap, which conflicted with that requirement. The toolbar is dark in both themes, so it never dazzles.

**QR code at the fire.** The join QR code is the one exception to the night theme: it is shown black on a white panel with the brightness raised [FR-DIST-02]. The panel is as small as the scanning distance allows, and the rest of the screen stays black.

### 6.1 Singer mode: the lyric view (*liedtekst*)

The singer's lyric view is the screen the app exists for. It is one continuous page that holds the whole setlist of the session, not one song at a time, and it gives every pixel it can to the words.

**D-27 — Maximum screen use.** In singer mode the lyric view is edge to edge: no app bar, the status bar hidden (section 7), the system navigation bar in the theme's background colour, and margins of 16 dp left and right. The only fixed element is the header strip of D-20, 24 dp high at the top, with the song title, the position in the setlist, the connection state and, when switched on, the follow-the-leader mark [FR-DISP-11, FR-SING-04]. The three round buttons (D-30) float over the text. Nothing else is on screen. *Why:* legibility from arm's length at the fire [FR-DISP-05] is the first requirement; everything that is not a lyric costs lines.

**D-28 — Lines always wrap; landscape adds fit-to-width.** The FRD makes wrapping a MUST in both orientations: horizontal scrolling must never be needed [FR-DISP-06]. The evaluation for this document: at the default 28 sp a 1080-pixel portrait screen holds about 30 characters per line, so most lyric lines (30 to 50 characters, at most 120 by the song format) wrap once; a 2400-pixel landscape screen holds about 70, so most lines fit unwrapped. Wrapping therefore stays on in both orientations and is rarely visible in landscape. On top of that the view offers **fit-to-width** (*passend maken*): when on, the text size is reduced by at most two steps of D-18 so that the longest line of the song fits on one line; when even that is not enough, the line wraps. Wrapped continuation lines get a hanging indent of 1 em so a wrapped line is never mistaken for a new line of the song [FR-SING-10]. Default: fit-to-width off in portrait, on in landscape (Q10). *Why:* "no wrapping in landscape" as a hard rule would break FR-DISP-06 for long lines and very large text sizes; fit-to-width gives the same result where it is possible and degrades gracefully where it is not.

**D-29 — One page for the whole setlist; previous and next song at the ends.** The lyric view scrolls by swipe for continuous scrolling and by tap for one line (D-17) [FR-SING-11]; the volume keys scroll one line when enabled in settings. The page is not one song: above the current song's title stands the previous song of the setlist, below its last line the next one, each separated by a divider with the song's title and its position (*3/12*). Scrolling past a divider makes that song the one the header strip names. Which songs are on the page is fixed by D-47: only the songs the leader has pushed in this session, in the order pushed; there is no upcoming song and no placeholder. Any manual scroll switches follow-the-leader off [FR-SING-12]; while the singer is away from the current song a chip *Terug naar het huidige lied* sits at the bottom centre, and one tap returns [FR-SING-14]. When the leader pushes a new song the page scrolls to it with the cue of FR-SING-02, wherever the singer was. Jumping to the previous or next verse or chorus [FR-SING-13] is a horizontal swipe within the song. *Why:* at a fire the next song is the natural question; reading ahead or back is part of singing along, and it must never require leaving the view.

**D-30 — Three round buttons, bottom right, stacked.** Over the lyrics, 16 dp from the right and bottom edges, a vertical column of three round buttons of 56 dp with 12 dp between them, top to bottom: **previous song** (chevron up), **songlist** (list icon), **next song** (chevron down). Previous and next move the page to the start of the neighbouring song among the songs received in this session (D-47); next is disabled on the most recently pushed song. They navigate the singer's own view and never control the leader. The buttons are dark translucent in both themes (night: `#1A1A1A` at 80 % with white icons; day: `#E6E6E6` at 80 % with black icons) so they do not dazzle [FR-DISP-03], dim to 40 % opacity after three seconds without touch and return to full on any touch. In landscape the column stays at the bottom right. A left-handed setting mirrors the column to the left (Q12). *Why:* thumb reach on a phone held in one hand, in the dark, with the other hand around a mug; 56 dp exceeds the 48 dp minimum of NFR-ACC-01 on purpose.

**D-31 — The songlist is the session's setlist, current song first.** The middle button opens the songlist (*liedjeslijst*) as a bottom sheet in the theme's colours: the current song pinned at the top with an orange bar, then the songs of this session in the order the leader pushed them, earlier ones with a check mark [FR-SING-03]; nothing the leader has not sent yet (D-47). Tapping a song closes the sheet and jumps the lyric view to the start of that song; tapping the pinned current song is the one-action return of FR-SING-14. Each row carries the like control of D-32. The sheet's last row is *Kampvuur verlaten* (D-34). The singer's own request list [FR-REQ-05] is a second tab of the same sheet, so the sheet is the one place for everything about the session that is not the lyrics.

**D-32 — Likes: a heart on every song, sent to the leader as a request.** A heart (*vind ik leuk*) sits at the right of every songlist row and at the start of every song on the lyric page, next to its title. Tapping it sends a like for that song to the leader; tapping again withdraws it [FR-REQ-04]. One like per singer per song per gathering. On the leader's side a like is a request for that song [FR-REQ-01]: likes for the same song are grouped and counted [FR-LEAD-12], so the request list [FR-LEAD-10] shows the most-liked songs on top and the leader can prioritise. A like for the current song counts as "sing it again later". Likes travel encrypted to the leader like any request [FR-BLE-17]; the leader sees the count, not who liked (Q11). Likes do not count against the pending-request limit of FR-REQ-06, since they name songs the leader already has. *Why:* a one-tap, anonymous signal is the request mechanism children will actually use; typing a title is for songs that are not on the list.

**D-33 — Song info stays one tap away but off the page.** The metadata of the song (author, licence, source, library) [FR-SING-15] opens from the toolbar (D-17) or from a long press on the song's title; it is not printed on the lyric page.

**D-34 — Leaving singer mode is deliberate.** The exit from singer mode is the row *Kampvuur verlaten* at the bottom of the songlist sheet (D-31), and the system back gesture, both followed by the confirmation that FR-GATH-07 requires (*Je ziet de liedteksten dan niet meer.* with *Verlaten* and *Blijven*). It is not one of the three round buttons, so it cannot be hit by accident in the dark. Leaving returns to the home screen (section 4), ends the singer mode header, and discards the lyrics received over the air [FR-DATA-01]; the gathering memory of FR-DATA-03 is written at that moment. When the leader ends the gathering, the lyric view stays readable with the header *beëindigd* [FR-GATH-09] and the same row reads *Terug naar start*.

**D-47 — A singer sees only what the leader has sent.** The setlist page (D-29), the *Lijst* tab (D-31) and the previous/next buttons (D-30) contain exactly the songs received over BLE in the running gathering, in the order the leader pushed them: the current song and the ones before it. No upcoming song, no title of a song still to come, no placeholder for a text that has not arrived, even when the singer's own library holds that song. The singer's local library is a different thing: it is browsed in browse state (D-40), is never merged into the session page, and a song read there is not "part of the session" until the leader pushes it. When the gathering ends, the received songs are discarded [FR-DATA-01] and only their titles stay in the gathering memory [FR-DATA-03]. *Why:* the leader decides what the fire sings and when; reading ahead undermines that and tempts singers to request what is coming anyway. It also keeps a singer without the library from ever holding more than what was broadcast, which is the spirit of FR-LIB-22. FR-SING-03's optional "upcoming songs if the leader shares them" is therefore not used in phase 1 (Q17).

**To investigate: a magnifier mode (*vergrootglas*).** Besides scrolling a page of text at one size (D-28, D-29), a different way of reading is to be evaluated before the lyric view is built: the lyrics and the songlist are laid out at a smaller, fixed size that shows a whole song or a long stretch of the setlist at once, and the singer zooms in and out with a pinch and moves over the text like a magnifying glass, by dragging. The purpose is readability of both the lyrics and the setlist: an overview of the song at a glance, and large text where the eyes are. When the magnifier mode is on, the lyrics and the setlist are rendered at a size chosen to work with zooming (a sub-optimal size for plain reading, optimal under the glass); when it is off, D-28 applies. The investigation has to answer, with real songs on a real phone in the dark: whether the glass can be moved reliably with cold fingers and one hand (FR-SING-11 asks for simple, reliable gestures); whether it can respect FR-DISP-06, which forbids needing horizontal scrolling to read a line, for example by snapping the glass to whole lines or by zooming line height only; how follow-the-leader (FR-SING-12) and the current-line mark (D-16) behave under the glass; and how the mode interacts with the text-size steps of FR-DISP-05 and the system font scaling of NFR-ACC-02. Outcome options: adopt it as a third way of reading alongside the text-size steps and fit-to-width, limit it to the songlist, or drop it. D-48 weighs against it: zooming and panning is a reason to play with the phone, so the investigation has to show a readability gain the text-size steps cannot give. Open question Q13; no decision yet.

---

### 6.2 Singer mode: around the lyric view

Section 6.1 is the page the singer reads. This section is everything else a singer meets between joining and leaving: the way in, the states of the connection, requests, the library, and the delegate role.

#### Joining and the connection

**D-35 — Joining from a QR code is one screen that reports what it does.** When the camera app opens the join link, the app skips the welcome and home screens (D-05, D-22) and shows the **joining screen** (*deelnemen*), a configuration screen: the gathering name when the code carries one, the fingerprint words (D-36), and one status line that changes as the app works: *Kampvuur zoeken…* while scanning for the identifier in the code, *Gevonden, controleren…* while the gathering state is verified against the key pinned from the code, then straight into singer mode and the lyric view, with the join code from the QR code supplied without asking [FR-GATH-20]. If nothing signed by that key is in range, the screen keeps looking for 60 seconds with *Ga dichter bij de kampvuurleider staan* and then offers *Opnieuw zoeken* and *Naar start*; it never gives up silently. If a gathering with the identifier is found but its state is not signed by the key in the code, the screen says *Dit is niet het kampvuur van de QR-code* and offers only *Naar start* [FR-GATH-19]. The first join ever, by QR code or from the list, is preceded by the display-name dialog of D-26, which can be skipped. *Why:* the QR code is the trust anchor against a fake gathering; the singer should see the check happen and see it fail, in words a child understands.

**D-36 — The fingerprint is always in the header strip.** The two fingerprint words [FR-GATH-18] stand after the gathering name in the singer header strip (D-20): `Kampvuur 18 okt · appel-vuur · verbonden`. When the strip is too narrow the gathering name is truncated with an ellipsis, never the fingerprint or the state. On the home screen (D-22) every gathering card shows its fingerprint under the name, and when two gatherings in range have the same or a very similar name, the join area shows one line above the cards: *Twee kampvuren met bijna dezelfde naam: vraag de leider naar de twee woorden.* *Why:* the fingerprint only helps if it is visible without looking for it, both when choosing and after joining.

**D-37 — Losing the leader, and the end of a gathering, are banners, never dialogs.** While contact with the leader is lost the lyric page stays as it is and the header strip reads *opnieuw verbinden…* [FR-GATH-10]; nothing covers the text. When the gathering cannot be found for the configured time (default five minutes) [FR-GATH-11], or the leader ends it [FR-GATH-09], the strip reads *beëindigd* and a banner slides in above the round buttons: *Het kampvuur is niet meer te vinden* or *De leider heeft het kampvuur beëindigd*, with *De teksten blijven zichtbaar tot je vertrekt* and one button, *Terug naar start*. The lyrics received over the air are discarded and the gathering memory is written when the singer takes that step or leaves (D-34) [FR-DATA-01, FR-DATA-03]; queued requests are discarded with a line in the banner [FR-REQ-03]. The leader's library download instruction [FR-LEAD-30] arrives the same way: a banner *De leider vraagt je bibliotheek "Kampvuurliederen" te downloaden (12 MB)* with *Downloaden* and *Niet nu*; the download runs only after that tap, only with internet, and the lyric view stays usable meanwhile. *Why:* at the fire a modal dialog over the lyrics is the worst possible interruption; a banner informs and waits.

#### Requests

**D-38 — The request tab shows the singer's own requests and their state.** The songlist sheet (D-31) has three tabs: *Lijst* (the setlist), *Verzoeken* (requests) and *Liedboek* (the library, D-40). The *Verzoeken* tab lists the singer's own requests, newest on top, each with a state chip: *in afwachting*, *geaccepteerd (plek 5)* when the position is known, *afgewezen*, *gezongen*, and *wachtrij* while the singer is not connected, with the note that it is sent on reconnection [FR-REQ-03, FR-REQ-05]. A pending or queued request has a cross to withdraw it [FR-REQ-04]. The tab header shows the count against the limit, *2 van 3* [FR-REQ-06], and the button *Ander lied aanvragen* opens the free-text form: one field of at most 100 characters with a counter, *Verzenden*, nothing else [FR-REQ-02]. Requesting a song that is in the local library goes through the *Liedboek* tab (D-40) [FR-REQ-01]. The optional "+1" on other singers' requests [FR-REQ-07] is held back by D-48: the tab shows the singer's own requests only. *Why:* the singer must always know what became of a request; one tab that is also the way to make one keeps the mental model small, and other people's requests are something to watch rather than to sing.

**D-39 — One heart, one star.** A **heart** always talks to the leader: on a song in the setlist page or the *Lijst* tab it is a like (D-32), a request on the leader's side, grouped and counted [FR-LEAD-12], withdrawn by tapping again. A **star** always talks to the singer themselves: it marks a favourite in the library [FR-LIB-32], stored on the device and never sent. By D-48 the star is only offered in the library outside a gathering, where it is a filter; while joined, the *Liedboek* tab shows neither stars nor a favourites filter. The lyric page shows the heart (D-32); neither control appears where its meaning would be ambiguous. *Why:* three controls with the same icon and three meanings is how children end up requesting songs by accident.

#### The library while singing

**D-40 — The library is the third tab of the songlist sheet, and browsing never loses the current song.** The *Liedboek* tab is the whole combined songbook [FR-LIB-30]: a search field at the top that matches titles and fragments of lyrics [FR-LIB-31], a row of filter chips (library, language, tag), and the list. It is reachable at any time while joined [FR-LIB-33] and the sheet expands to full height when the search field has focus, so the keyboard does not hide the results (Q16). Tapping a song opens it on the lyric page in **browse state**: the header strip reads *bladeren* instead of the connection state, the song's text is shown exactly as the setlist page shows songs, and the chip *Terug naar het huidige lied* of D-29 is the one-action return [FR-SING-14]. The toolbar (D-17) gains *Dit lied aanvragen* in browse state, which sends the request [FR-REQ-01] and returns to the current song. A song pushed by the leader while browsing pulls the view back to it with the cue of D-43 [FR-SING-02]. *Why:* singers look things up while others sing; the sheet is already the place for everything that is not the lyrics, and a separate full screen would make the way back a two-step affair.

**D-41 — Variants are switched from the song-info sheet.** When a song belongs to a variant group [FR-LANG-06] and another member is in a local library, the song-info sheet (D-45) lists the variants as *Toon in: English*; choosing one swaps the text for the variant on this singer's device only, keeping the position at the same canonical line where the variants line up and at the top of the song otherwise. The leader is not involved; the entry is absent when no variant is available locally. Nothing on the lyric page itself announces a variant (held back by D-48; v0.5 first drew a chip on the title). *Why:* a Dutch leader can push the Dutch song while an English-speaking singer reads the English words, which is what the variant group exists for; the singer who needs it will look, the others should not be tempted.

#### Display rules and settings

**D-42 — Rotation keeps the line.** When the device is rotated the line at the top of the viewport, or the current line when following the leader, stays in view at the same position in the song [FR-DISP-04]; fit-to-width is re-evaluated for the new width (D-28). The round buttons and the header strip keep their places (D-27, D-30). *Why:* a rotation at the fire is usually accidental; the singer must not lose the line.

**D-43 — A new song is announced, not interrupted.** When the leader pushes a song the page scrolls to its start, its title row carries the orange bar of D-16 for one second, and the device vibrates twice briefly when *Trillen bij nieuw lied* is on in settings (default on) [FR-SING-02]. No sound, no dialog. The same cue plays when the singer is in browse state (D-40). *Why:* the cue must be noticed in the dark and in the noise, and must not need a tap to dismiss.

**D-44 — Display name and vibration live in settings.** Settings has the group *Zingen* with: *Naam voor de leider* (the display name of FR-SING-01: at most 24 characters, hint *Alleen je voornaam of bijnaam*, never prefilled, empty allowed and shown to the leader as *Zanger n*), *Trillen bij nieuw lied* (D-43), *Volumeknoppen scrollen* [FR-SING-11], *Tijd tot "kampvuur niet gevonden"* [FR-GATH-11], and the magnifier switch should Q13 adopt it. A changed display name applies at the next join; while joined the setting says so. *Why:* FR-SING-01 asks the name once; it should still be findable without searching.

**D-45 — The song-info sheet shows the metadata of the song file, nothing invented.** From the toolbar or a long press on the title (D-33) the info sheet lists, in this order and only when present: title and subtitle; lyricist, composer, melody ("op de wijs van …"), year; copyright with the © sign; licence as its identifier or *licentie van bundel X* when the song carries none; source; tags with their labels; the library's name and the revision installed; the variant group's other members as links (D-41) [FR-SING-15, FR-LIB-20]. For a song received over the air without the library, the sheet says *Ontvangen van de leider* in place of the library line. The sheet is dark in the night theme and closes with a swipe down. *Why:* FR-LIB-20 and FR-LIB-23: show the metadata as it is, never alter it.

#### The delegate role

**D-46 — A singer who becomes a delegate gets one more tab, and nothing of the leader's.** When the leader grants the delegate role (*hulpleider*, Q15) [FR-LEAD-40], a banner says *De leider heeft je hulpleider gemaakt*, the header strip shows *hulpleider* after the state, and the songlist sheet gains a fourth tab, *Leiden* [FR-LEAD-45]. That tab holds the leader's request list with the same grouping and count (title, requester name, time; accept, push now, decline) [FR-LEAD-41], and the setlist with *Volgende zingen*, *Toevoegen uit liedboek* and, on any song of the *Liedboek* tab, *Nu zingen* [FR-LEAD-42]. Every action goes through the leader's device; when it cannot be reached the action is refused at once with *De leider is niet bereikbaar, probeer het straks opnieuw* and is not retried later [FR-LEAD-43]. The tab has no ending, no join code, no delegates, no download instruction and no hand-over [FR-LEAD-44]. During *opnieuw verbinden…* the tab stays and its actions are disabled; the role survives the reconnection [FR-LEAD-46]. When the role is revoked or the gathering ends, a banner says so and the tab disappears [FR-LEAD-45]. The delegate's own lyric view, likes and requests are unchanged. *Why:* the delegate is a singer with extra buttons, not a second leader; keeping the extras in one tab keeps the lyric view identical for everyone and makes FR-LEAD-44 a matter of what the tab does not contain.

#### Room for phase 2

**Karaoke (P2).** The lyric page already carries the current-line mark (D-16) and the line numbering of the song format's canonical line list; karaoke highlighting [FR-KAR-03] will drive that mark from the gathering clock and add a thin progress line under the current line, with the next lines visible as they are now. The toolbar (D-17) reserves a *Meelopen* toggle for the local switch-off of FR-KAR-09 that appears only in phase 2. Nothing in section 6.1 or 6.2 needs to change for it.

---

## 7. Typography and sizes

**D-18 — System font, five lyric sizes, dp targets.** The app uses the platform's default UI font (Roboto on Android, San Francisco on iOS); no font files are shipped. Lyric text sizes are five steps, **18, 22, 28, 36 and 48 sp**, remembered per device, with 28 sp as the default [FR-DISP-05]; the sp unit lets the platform's font scaling apply on top [NFR-ACC-02]. Lines wrap at the view width [FR-DISP-06]. Configuration screens use the platform's standard type scale: display 34 sp for the app name on the welcome screen, title 22 sp, body 16 sp, caption 12 sp. Every tappable control is at least **48 × 48 dp** [NFR-ACC-01], and on gathering screens the main controls (theme, text size, next song) are 56 dp, since they are operated in the dark.

**System bars.** Configuration screens are drawn edge to edge: the status bar is transparent over the image, the navigation bar is transparent over the scrim, and the content keeps the system insets. Gathering screens colour both bars in the theme's background colour; the status bar is hidden in the lyric view while a gathering runs and the keep-screen-on setting is active, so the full height goes to the lyrics.

**Version display.** The welcome screen shows `versionName`. The About screen [FR-GEN-07] shows `versionName (versionCode)`, the supported song-format, archive and BLE protocol versions [NFR-MAINT-01], and the version and revision of each installed library with the date of its release archive.

---

## 8. Colour reference

| Name | Value | Used for |
|---|---|---|
| Night blue dark | `#0B2545` | System splash, scrim top, panels, text on orange buttons |
| Night blue | `#13315C` | Icon gradient, accents on configuration screens |
| Fire orange | `#FF8A3D` | Primary button, current-line bar, icon |
| Fire orange light | `#FFB36B` | Secondary accent, tagline on the feature graphic |
| Sky blue | `#4FC3F7` | Bluetooth state indicator, links |
| Off-white | `#CFE6FF` | Secondary text on configuration screens |
| White | `#FFFFFF` | Text on configuration screens; night-theme lyric text; day-theme background |
| Black | `#000000` | Night-theme background; day-theme lyric text |

The values come from `design/icon/zangbundel-icon.svg` and `design/icon/feature-graphic.svg`, so the icon, the store listing and the app share one palette.

---

## 9. Requirements traceability

| Requirement | Where satisfied |
|---|---|
| FR-GEN-01, FR-GEN-02, FR-GEN-04 | Two modes and visible state (D-19, D-20) |
| FR-GEN-05 | Permission card on the home screen (D-22); configuration screen family (2) |
| FR-GEN-06, FR-GEN-08 | First-use sequence (D-07) |
| FR-GEN-07, NFR-MAINT-01 | Version display (7) |
| FR-GATH-01, FR-GATH-05, FR-GATH-21, FR-LEAD-02 | Gathering settings screen before leading (D-25) |
| FR-GATH-03, FR-GATH-04 | Gathering cards on the home screen (D-22) |
| FR-GATH-06, FR-SING-01 | Join dialog and first song (D-26) |
| FR-GATH-07, FR-GATH-12, FR-LEAD-03, FR-LEAD-04 | Role-dependent controls on shared screens (D-19) |
| FR-GATH-09, FR-GATH-10, FR-SING-04 | Header strip with connection state (D-20) |
| FR-GATH-13 | Resume card (D-22) |
| FR-GATH-16 | Start refused while a gathering is listed (D-23) |
| FR-GATH-23 | QR hint when nothing is in range (D-22) |
| FR-LANG-01 | No text in the background image (D-09) |
| FR-DISP-01, FR-DISP-02, FR-DISP-03 | Themes, night default, scrim level (D-02, D-03, D-15) |
| FR-DISP-05, FR-DISP-06, NFR-ACC-02 | Typography (D-18) |
| FR-DISP-07, FR-DISP-08 | Toolbar (D-17) |
| FR-DISP-09, FR-DISP-11 | Current-line mark, header and footer (D-16) |
| FR-DISP-10 | Contrast rule for both families (D-14, D-15) |
| FR-DIST-02 | QR panel exception (6), QR one tap away in leader mode (D-25) |
| FR-SING-02, FR-SING-03, FR-SING-11, FR-SING-12, FR-SING-13, FR-SING-14 | Continuous session page, scrolling, follow-the-leader, return to current song (D-29, D-31) |
| FR-DATA-01, FR-DATA-03, FR-LIB-22 | Only songs received in the running session are on the page (D-47) |
| FR-SING-10 | Hanging indent for wrapped lines, song structure on the page (D-28) |
| FR-SING-15 | Song info from the toolbar or the title (D-33) |
| FR-REQ-01, FR-REQ-04, FR-REQ-05, FR-REQ-06, FR-LEAD-10, FR-LEAD-12, FR-BLE-17 | Likes as grouped, encrypted requests; request tab in the songlist sheet (D-31, D-32) |
| FR-GATH-07, FR-GATH-09, FR-DATA-01, FR-DATA-03 | Leaving singer mode (D-34), ended-state banner (D-37) |
| FR-GATH-18, FR-GATH-19, FR-GATH-20 | Joining screen from a QR code, fingerprint in the strip and on the cards (D-35, D-36) |
| FR-GATH-10, FR-GATH-11, FR-LEAD-30, FR-REQ-03 | Reconnecting, ended after the configured time, download instruction, queued requests (D-37, D-38) |
| FR-REQ-02, FR-REQ-05, FR-REQ-06, FR-REQ-07 | Request tab, free-text form, limit, +1 on others' requests (D-38, D-39) |
| FR-LIB-30, FR-LIB-31, FR-LIB-32, FR-LIB-33 | Library tab with search, filters and favourites; browse state (D-39, D-40) |
| FR-LANG-06 | Variant chip on the title (D-41) |
| FR-DISP-04 | Rotation keeps the line (D-42) |
| FR-SING-01, FR-SING-02 | Display name and vibration in settings, new-song cue (D-43, D-44) |
| FR-LIB-20, FR-LIB-23, FR-SING-15 | Song-info sheet (D-45) |
| FR-LEAD-40, FR-LEAD-41, FR-LEAD-42, FR-LEAD-43, FR-LEAD-44, FR-LEAD-45, FR-LEAD-46 | Delegate tab in singer mode (D-46) |
| FR-KAR-03, FR-KAR-09 | Room reserved for karaoke (6.2, "Room for phase 2") |
| FR-DIST-05 | One small image asset (D-08, D-12) |
| NFR-ACC-01 | Touch targets (D-18) |
| NFR-PERF-02 | Pure black night theme, no image at the fire (D-01, D-15) |

---

## 10. Open questions

| # | Question | Impact |
|---|---|---|
| Q1 | *Resolved 2026-10-19:* a photograph, taken by the owner at a campfire on 3 April 2026; files in `design/gui/`. Rights and the people in it are tracked under Q8. | Resolved |
| Q2 | Is a tablet layout in phase 1 (FRD Q7)? If so, a 2560 × 1600 landscape export is added and D-13 gets a tablet rule. | D-11, D-13 |
| Q3 | Is 1.5 seconds the right maximum for the welcome screen, or should it be shown only until the app is ready, with no minimum? | D-05 |
| Q4 | Should the day theme also be allowed on configuration screens with a *light* scrim and dark text, for use in bright daylight, or is the white-on-dark-scrim design enough? | D-03, D-14 |
| Q5 | Should the FRD's section 19 row `design/ui-flows.md` be renamed to this document, or should screen flows get a separate document after all? | FRD section 19 |
| Q6 | Should the home screen keep scanning indefinitely while shown (simplest, costs battery while the phone lies on the table) or stop after a few minutes and offer a "search again" tap? | D-22, NFR-PERF-02 |
| Q7 | When a gathering in range needs a join code, should the card say so before the tap (lock icon, as D-22) or only ask after the tap, so children are not drawn to locked gatherings? | D-22, D-26 |
| Q8 | *Decided 2026-10-19:* the photograph with the two children seen from behind is kept and D-09 amended to allow it. Still to be written into `design/gui/README.md` before the image is published: the parents' consent and the photographer's permission ("BOG & MN" in the EXIF). | Privacy by design (FRD 2.6), D-09 |
| Q10 | Fit-to-width (D-28): is "off in portrait, on in landscape" the right default, or should the setting be one switch regardless of orientation? To be answered with real songs on a real phone. | D-28 |
| Q11 | Likes (D-32) need a place in the BLE protocol: a Request with a "like" flag, or a message of its own? Should the leader ever see who liked, or only the count? The protocol document decides; the privacy assessment reviews it. | D-32, `design/ble-protocol.md`, assessment |
| Q12 | Should the round-button column mirror to the left for left-handed singers (a setting), or stay at the right for everyone? | D-30 |
| Q13 | Magnifier mode (section 6.1, "To investigate"): do we want a zoom-and-pan way of reading the lyrics and the setlist, with the text laid out at a size tuned for zooming, in addition to or instead of scrolling at one size? To be prototyped and tried at a fire: gesture reliability in the dark and with one hand (FR-SING-11), compatibility with the no-horizontal-scrolling rule (FR-DISP-06), follow-the-leader and the current-line mark under the glass, interaction with text-size steps (FR-DISP-05) and system font scaling (NFR-ACC-02). | D-28, D-29, lyric view prototype |
| Q14 | Leader mode is still to be written: the leader's lyric view and control view [FR-LEAD-04], setlist control, request handling, the singer list, the QR screen, delegates, ending the gathering and hand-over. Sections 4.1 and D-25 only open the door. | Next version of this document |
| Q15 | Dutch UI term for the delegate role: *hulpleider* (used provisionally in D-46), *gedelegeerde*, or *assistent*? | D-46, locale files |
| Q16 | Is the library better as the third tab of the songlist sheet (D-40, one place for everything that is not the lyrics) or as a full screen of its own, given the search keyboard and the filter chips? To be judged on a phone with the first prototype. | D-40 |
| Q17 | D-47 leaves FR-SING-03's optional sharing of upcoming songs unused in phase 1. If a later phase wants it, it would be titles only, never texts, and it needs a leader-side switch; the FRD wording ("if the leader shares it") already allows the omission. | D-47, FRD section 20 |
| Q9 | *Resolved 2026-10-19:* the landscape export exists, `design/gui/backdrop-3200x1440.webp` from master `Backdrop_001-land.png`: the full-width band of the source photograph with the two children, scaled to 1920 × 1440 and placed at the right of a black 3200 × 1440 canvas. Same people as the portrait image, so Q8's consent covers both. Measurements in `design/gui/README.md`. | Resolved |
