# Zangbundel – Song Format and Library Layout

| Field | Value |
|---|---|
| Document | Song file format and library repository layout |
| Project | Zangbundel – campfire songbook app |
| Format version | 1.0 (draft) |
| Document version | 0.1 (draft) |
| Date | 2026-10-18 |
| Author | Remko Welling |
| Status | Draft for review |
| Depends on | `design/functional-requirements.md` v0.3, `design/ble-protocol.md` v0.2 |

| Version | Date | Change |
|---|---|---|
| 0.1 | 2026-10-18 | First draft. Answers FRD open question Q4 (ChordPro). |

---

## 1. Introduction

### 1.1 Purpose

This document specifies the files a Zangbundel **library** consists of: the layout of the git repository, the library-level metadata file, and the format of a **song file**. It defines how a song file is turned into the lines the app shows, how a song file is canonicalised and hashed so that leader and singers can recognise the same song over BLE, and how the format grows in phase 2 (karaoke timing) and phase 3 (chords and notation) without breaking phase 1 apps.

It is written for two audiences: **library maintainers**, who edit song files with a text editor and git, and **app developers**, who implement the parser, the validator and the import.

### 1.2 Scope

In scope: the repository layout, `zangbundel.json`, the `.cho` song file, canonical form and content hash, the canonical line list, validation and import reporting, and the extension rules for later phases.

Out of scope: how the app downloads a repository (FR-LIB-10 to 17, architecture document), the on-device storage model, the BLE transport of a song (`design/ble-protocol.md`), and the UI that renders a song.

### 1.3 Conventions

- MUST / SHOULD / MAY follow RFC 2119. Rules marked MUST bind library maintainers (the validator enforces them) and app implementations alike.
- Requirement IDs in brackets, e.g. [FR-LIB-04], point to the FRD requirement a rule satisfies.
- "P1 app", "P2 app", "P3 app" mean an app implementing the format up to that phase.
- Directives are written as `{name}` or `{name: value}`. Chords are written as `[C]`.

### 1.4 Definitions

| Term | Meaning |
|---|---|
| **Library** | A git repository with a `zangbundel.json` at its root and song files under `songs/`. |
| **Library id** | The stable identifier of a library, from `zangbundel.json`. Sent over BLE as `ref.lib`. |
| **Song file** | One `.cho` file holding exactly one song: metadata directives plus the lyrics body. |
| **Song id** | The stable identifier of a song within its library. Equals the file name without extension. Sent over BLE as `ref.song`. |
| **Canonical form** | The byte sequence of a song file after the normalisation in section 8. The content hash is computed over it. |
| **Content hash** | First 16 bytes of SHA-256 over the canonical form. Sent over BLE as `ref.hash`. |
| **Lyric line** | A line of the song body that carries sung text and is shown in the lyric view. |
| **Canonical line list** | The ordered list of lyric lines in sung order, with chorus repeats expanded (section 9). The BLE `pos` field is a 1-based index into it. |
| **Environment** | A block of lines between `{start_of_<x>}` and `{end_of_<x>}`: verse, chorus, bridge, or another kind. |
| **Zangbundel profile** | The subset of ChordPro this document requires plus the extensions it defines. |

---

## 2. Format decision: a ChordPro profile

FRD open question Q4 asks whether to adopt ChordPro or define a Markdown/YAML format. This document adopts **ChordPro**, restricted and extended as the *Zangbundel profile*.

### 2.1 Comparison

| Criterion | ChordPro (profile) | Custom Markdown + YAML front matter |
|---|---|---|
| Plain text, editable in any editor, diff-friendly in git [FR-LIB-03] | Yes | Yes |
| Song structure (verse, chorus, bridge, chorus repeat) | Built in: `{start_of_verse}`, `{start_of_chorus}`, `{chorus}` … | None; would need a convention on headings or markers |
| Metadata [FR-LIB-04] | Built in: `{title}`, `{composer}`, `{copyright}` …, extensible with `{meta: name value}` | YAML front matter is excellent for metadata |
| Chords inline with lyrics [FR-INST-01, P3] | Native: `[G]Hello [D]world` | Must be invented; nothing standard in Markdown |
| Notation [FR-INST-01, P3] | ChordPro 6 has `{start_of_abc}` for embedded ABC | Must be invented |
| Timing data [FR-KAR-01, P2] | Not standard; added with reserved `x_` directives, which ChordPro defines for custom use | Must be invented |
| Forward compatibility [FR-LIB-06] | ChordPro rule: unknown directives are ignored. Custom environments can be skipped as a block | Markdown has no ignore rule; unknown constructs render as text |
| Existing songs and tools | Thousands of existing `.cho` files, editors, the `chordpro` reference tool for previews and PDF songbooks | None; converters would be needed |
| Parser effort on Android and iOS | A line-oriented parser for the profile is a few hundred lines; no library dependency needed | Markdown parser plus a YAML parser on both platforms |
| Pitfalls | Brace syntax is slightly less familiar than Markdown | YAML type coercion: the language code `no` (Norwegian) parses as boolean `false`; `1:05` parses as a number in YAML 1.1 |
| Readability without the app | Good; labels and lyrics are readable as is | Very good |

### 2.2 Decision

ChordPro wins on the two criteria that matter most for this project: the song structure and the phase 3 chords are already defined, and the ignore rule for unknown directives gives the required forward compatibility for free. The Markdown/YAML option would have to invent all of this and would carry two parsers and YAML's coercion traps across two platforms.

The price is that Zangbundel implements its **own** parser for the profile (section 5 to 10). The app MUST NOT depend on the behaviour of the ChordPro reference implementation; it is a convenience for maintainers, not a dependency. Where this document is silent, ChordPro 6 behaviour is a reasonable guide but not binding.

A song file written to this profile renders acceptably in the ChordPro reference tool: standard directives are understood, `{meta}` entries are accepted, and `x_zb_*` directives are ignored. Maintainers can therefore preview or print songbooks with existing tooling.

---

## 3. Library repository layout

### 3.1 Files and directories

```
<repository root>/
├── zangbundel.json         library metadata (section 4)            MUST
├── LICENSE                 full licence text of the collection    MUST
├── README.md               for humans: what, who, how to contribute  SHOULD
├── .gitattributes          forces LF line endings (section 3.4)   SHOULD
├── .editorconfig           editor hints (section 3.4)             SHOULD
├── songs/                  song files, any depth of subdirectories MUST
│   ├── nl/
│   │   └── vuur-in-de-nacht.cho
│   └── en/
│       └── fire-in-the-night.cho
└── notation/               optional, P3: MusicXML and other attachments
```

- The app reads `zangbundel.json`, every `*.cho` file under `songs/` (recursively), and the licence file named in `zangbundel.json`. Everything else is ignored.
- The subdirectory structure under `songs/` carries **no meaning**. The example uses one directory per language, which is the recommended convention, but a flat `songs/` directory or a split by theme is equally valid. Language comes from the song's metadata, not from its path [FR-LANG-05].
- Files under `songs/` that do not end in `.cho` are ignored without a message, so maintainers can keep notes or images next to the songs.

### 3.2 Song file naming

- File name: `<song id>.cho`. The extension is `.cho`, lower case. Other ChordPro extensions (`.chopro`, `.crd`, `.chordpro`) are **not** read.
- A song id MUST match `^[a-z0-9]+(-[a-z0-9]+)*$` and be at most 64 characters: lower-case ASCII letters, digits and single hyphens. Recommended derivation from the title: lower case, accents removed (`é` → `e`), everything that is not a letter or digit becomes a hyphen, hyphens collapsed. "Zeg ken jij de mosselman?" → `zeg-ken-jij-de-mosselman`.
- A song id MUST be unique within the library regardless of directory. `songs/nl/kanon.cho` and `songs/en/kanon.cho` in one library is an error.
- A song id MUST never change once the song has been published in the library. The id is what gathering memories [FR-DATA-03], setlists [FR-LEAD-02] and the BLE song reference carry. If a title changes, the file keeps its name. Moving a file to another subdirectory is allowed.
- The pair (library id, song id) is globally unique (FRD constraint 7).

### 3.3 One song per file

A song file holds exactly one song. The ChordPro directive `{new_song}` (`{ns}`), which starts a second song in the same file, is an error in this profile. One file per song keeps content hashes, song ids and git diffs one-to-one.

### 3.4 Line endings and editor settings

Song files are hashed byte for byte (section 8), so the repository SHOULD pin line endings:

`.gitattributes`
```
*       text=auto eol=lf
*.cho   text eol=lf
*.json  text eol=lf
```

`.editorconfig`
```
root = true

[*]
charset = utf-8
end_of_line = lf
insert_final_newline = true
trim_trailing_whitespace = true
```

---

## 4. Library metadata: `zangbundel.json`

### 4.1 Why JSON

The library file is edited rarely and is small. JSON parses identically on Android and iOS with platform libraries, has no type coercion surprises, and keeps the number of formats in a library at two (ChordPro and JSON). The lack of comments is accepted; `README.md` is the place for prose.

### 4.2 Fields

| Field | Type | Req. | Meaning |
|---|---|---|---|
| `format` | string | MUST | Fixed value `"zangbundel-library"`. Marks the repository as a Zangbundel library. |
| `format_version` | string | MUST | `"<major>.<minor>"` of this specification the library is written to. `"1.0"` for this document. Section 10.4 gives the rules. [FR-LIB-05, NFR-MAINT-01] |
| `id` | string | MUST | Library id, same syntax as a song id (section 3.2), at most 64 characters. Globally unique by convention; choose something specific, e.g. `scouting-regio-noord-kampvuur`, not `songs`. [FRD constraint 7] |
| `name` | string | MUST | Display name, at most 80 characters. [FR-LIB-05] |
| `description` | string | MUST | One to a few sentences; shown on the library screen. [FR-LIB-05] |
| `maintainer` | object | MUST | `{ "name": string, "email": string, "url": string }`; `name` plus at least one of `email` or `url`. [FR-LIB-05] |
| `license` | object | MUST | `{ "id": string, "file": string, "summary": string }`. `id` is an SPDX identifier (e.g. `CC-BY-SA-4.0`), or `proprietary`, or `mixed` when songs carry their own licences. `file` is the path, relative to the repository root, of the full licence text the app shows on first import [FR-LIB-21]. `summary` is one sentence shown before the full text. |
| `min_app_version` | string | MUST | Semantic version `major.minor.patch` of the oldest app that can use this library. [FR-LIB-05] |
| `homepage` | string | MAY | URL for humans (repository page, website). |
| `languages` | array of string | MAY | BCP 47 codes of the song languages in the library, informational; the app derives the real list from the songs. |
| `tags` | object | MAY | Localised labels for tags used in the songs: `{ "<tag>": { "<lang>": "<label>", … } }`. A tag without an entry is shown as is. [FR-LIB-30] |

Unknown fields MUST be ignored by the app, so a library can carry extra fields for other tools.

### 4.3 Rules

- The app MUST refuse to import a library whose `zangbundel.json` is missing, is not valid JSON, lacks a MUST field, has a `format_version` major it does not implement, or has a `min_app_version` newer than the app. The message names the reason and, for the last two, tells the user to update the app [FR-BLE-07 by analogy, NFR-SEC-01].
- The app MUST refuse to add a library whose `id` equals that of an already configured library with a different repository URL, and MUST say which library owns the id. Two URLs for the same library (a mirror) are a user decision; the app does not detect mirrors.
- `min_app_version` is for maintainers who deliberately rely on a newer feature in every song, e.g. a karaoke-only library. Raising it locks out older apps, so it SHOULD stay as low as the content allows.
- When a song carries no `license` of its own (section 6), the library `license.id` applies to it, and the song's licence display [FR-LIB-20] says so.

### 4.4 Example

```json
{
  "format": "zangbundel-library",
  "format_version": "1.0",
  "id": "zangbundel-voorbeeld",
  "name": "Zangbundel voorbeeldbundel",
  "description": "Kampvuurliederen voor scouting. Vrij te gebruiken en te verspreiden; zie de licentie per lied.",
  "maintainer": {
    "name": "Zangbundel-project",
    "email": "bundel@example.org",
    "url": "https://example.org/zangbundel-voorbeeld"
  },
  "license": {
    "id": "CC-BY-SA-4.0",
    "file": "LICENSE",
    "summary": "Creative Commons Naamsvermelding-GelijkDelen 4.0, tenzij een lied anders aangeeft."
  },
  "min_app_version": "1.0.0",
  "homepage": "https://example.org/zangbundel-voorbeeld",
  "languages": ["nl", "en"],
  "tags": {
    "kampvuur": { "nl": "Kampvuur", "en": "Campfire" },
    "avond":    { "nl": "Avond",    "en": "Evening" },
    "kerst":    { "nl": "Kerst",    "en": "Christmas" }
  }
}
```

---

## 5. Song file: encoding and syntax

### 5.1 Encoding

- A song file is **UTF-8** text without byte order mark. Unicode is normalised to **NFC**.
- Line separator is **LF** (`0x0A`). The file ends with exactly one LF.
- No line has trailing spaces or tabs.
- Control characters other than LF are not allowed; a tab is a warning and is shown as a space.

Section 8 defines how the app canonicalises a file that deviates and what it reports.

### 5.2 Line types

The parser works line by line. Each line is exactly one of:

| Line type | Rule | Example |
|---|---|---|
| **Blank** | Empty after trimming. Separates implicit verses (section 7.3); otherwise a visual gap. | |
| **File comment** | First character is `#`. Not shown. Not a lyric line. | `# Melodie: Vader Jacob` |
| **Directive** | First character is `{`, last character is `}`, no other `{` or `}` unless escaped (section 5.4). | `{start_of_chorus: Refrein}` |
| **Lyric or content line** | Anything else. Shown in the lyric view when it is in a verse, chorus, bridge or outside any environment and has text left after chord removal (section 9). | `De [G]zon is weg,` |

A line that starts with `{` but does not end with `}` (or the reverse) is a malformed directive and an error; it is never shown as lyrics.

### 5.3 Directive syntax

```
{name}
{name: value}
```

- `name` is `[a-z_]+`. Parsers MUST match names case-insensitively; the validator warns on anything but lower case.
- The colon is required when there is a value. Spaces around the colon and around the value are trimmed.
- Short forms from ChordPro (`{t}`, `{soc}`, `{eoc}` …) are accepted and listed in sections 6 and 7. The validator does not warn on them, but the long forms are recommended for readability.
- `{meta: name value}`: the value is split at the first run of whitespace into a *meta name* and a *meta value*. `{meta: tag kampvuur}` sets meta `tag` to `kampvuur`. For the standard names (title, subtitle, artist, composer, lyricist, copyright, album, year, key, time, tempo, duration, capo, sorttitle) `{meta: title X}` and `{title: X}` are equivalent.
- The ChordPro 6 attribute form `{start_of_verse: label="Couplet 1"}` is **not** part of the profile. Write `{start_of_verse: Couplet 1}`.
- Unknown directives are ignored (section 10.1). The validator lists them once per file as an informational message so typos are noticed.

### 5.4 Chords, annotations and escapes

- A chord is `[` … `]` inside a content line: `[G]`, `[D/F#]`, `[Am7]`. The chord grammar is defined in phase 3; phase 1 treats everything between the brackets as opaque.
- `[*text]` is an annotation (ChordPro): a short remark positioned like a chord, e.g. `[*zachter]`. Phase 1 removes it like a chord.
- A backslash escapes the next character: `\[`, `\]`, `\{`, `\}`, `\\` produce the literal character in the shown text. A backslash before any other character is kept as is. This is the only way to show a literal bracket or brace in lyrics.

### 5.5 Limits

| Limit | Value | Rule |
|---|---|---|
| File size | ≤ 64 KiB | MUST. Matches the upload bound in the BLE protocol (section 9.8 there). |
| File size, recommended | ≤ 8 KiB | SHOULD; a warning above. A typical song is 1–4 kB [FR-BLE-05]. |
| Title length | ≤ 60 characters | SHOULD; the BLE state carries at most 60. |
| Lyric line length | ≤ 120 characters | SHOULD; lines wrap on screen [FR-DISP-06] but very long lines read badly. |
| Canonical line list | ≤ 254 lines | SHOULD; the BLE `pos` field cannot point beyond line 254. |

---

## 6. Song metadata

Metadata lives in directives, normally at the top of the file. Metadata directives MAY appear anywhere in the file, but maintainers SHOULD keep them together before the first lyric line.

### 6.1 Fields

| Field | Directive | Req. | Values | FRD |
|---|---|---|---|---|
| Song id | `{meta: id <id>}` | MUST | Same syntax as section 3.2. MUST equal the file name without `.cho`. | FR-LIB-04 |
| Title | `{title: …}` or `{t: …}` | MUST | Free text, ≤ 60 characters recommended. Exactly one. | FR-LIB-04 |
| Language | `{meta: language <tag>}` | MUST | BCP 47 language tag: `nl`, `en`, `de`, `fy`, `nl-BE`, … Exactly one; a bilingual song takes the language of its first verse. | FR-LANG-05 |
| Subtitle | `{subtitle: …}` or `{st: …}` | MAY | Free text, e.g. "canon in vier groepen". | |
| Sort title | `{sorttitle: …}` | MAY | Overrides the title for sorting. Without it the app drops a leading article of the song language (`De`, `Het`, `Een`, `The`, `A`, `An`). | FR-LANG-07 |
| Lyricist | `{lyricist: …}` | SHOULD | Who wrote the words. Repeat the directive for several people. `traditioneel` / `traditional` / `onbekend` / `unknown` are accepted values. | FR-LIB-04 |
| Composer | `{composer: …}` | SHOULD | Who wrote the melody. Repeatable, as above. | FR-LIB-04 |
| Artist | `{artist: …}` | MAY | Performer the song is known from. Repeatable. | |
| Year | `{year: …}` | MAY | Four-digit year of the words or first publication. | |
| Copyright | `{copyright: …}` | SHOULD | Copyright notice or holder, e.g. `2026 Zangbundel-project` or `Public domain`. The app prefixes `©` when the value does not already contain it. | FR-LIB-04 |
| Licence | `{meta: license <id>}` | SHOULD | SPDX identifier (`CC-BY-SA-4.0`, `CC0-1.0`, …), or `public-domain`, or `proprietary`. Absent: the library licence applies (section 4.3). | FR-LIB-04, FR-LIB-20 |
| Source | `{meta: source …}` | SHOULD | Where the text came from: a book, a website, "overgeleverd bij Scouting X". One directive; free text. | FR-LIB-04 |
| Tag | `{meta: tag <tag>}` | SHOULD | One tag per directive; repeatable. Tag syntax as a song id (`kampvuur`, `welpen`, `kerst`). Labels come from `zangbundel.json` (section 4.2). | FR-LIB-04, FR-LIB-30 |
| Melody | `{meta: melody …}` | MAY | "Sung to the tune of …": the title of the melody when it differs from the song, e.g. `Oh My Darling, Clementine`. | FR-LIB-04 |
| Variant group | `{meta: variant-group <id>}` | MAY | Songs in one library with the same value are translations or variants of each other (same melody, other words). Syntax as a song id. The app offers "show this song in …" among the members. | FR-LANG-06, FR-LEAD-07 |
| Redistribute | `{meta: redistribute yes|no}` | MAY | Default `yes`. `no` marks the song *display only*: the leader pushes only the song reference, never the file, and singers need the library installed. | FR-LIB-22 |
| Key | `{key: …}` | MAY | Musical key, e.g. `G`, `Am`. Shown in the info screen in P1; used by the musician view in P3. | FR-INST-03 |
| Capo | `{capo: …}` | MAY | Integer fret. P3. | FR-INST-03 |
| Tempo | `{tempo: …}` | MAY | Beats per minute. | |
| Time | `{time: …}` | MAY | Time signature, e.g. `3/4`. | |
| Duration | `{duration: …}` | MAY | `m:ss`, length of the song. Used with timing data in P2. | |

### 6.2 Rules

- Single-valued fields (id, title, language, subtitle, sorttitle, copyright, license, source, melody, variant-group, redistribute, key, capo, tempo, time, duration, year) MUST occur at most once; a second occurrence is an error.
- Multi-valued fields (lyricist, composer, artist, tag) are repeated, one value per directive. A comma inside a value is part of the value.
- Language is validated for **syntax** only (RFC 5646 well-formedness: `^[a-zA-Z]{2,3}(-[a-zA-Z0-9]{1,8})*$`) and normalised to the usual case (`nl-BE`). Unknown but well-formed tags are accepted.
- The app MUST NOT alter any of these values; it displays them [FR-LIB-23]. Display of the licence and attribution [FR-LIB-20, FR-SING-15] uses copyright, license, lyricist, composer, source and melody.
- **Licence summary for BLE.** The leader sends a `lic` string of at most 200 characters with a song it broadcasts (BLE section 9.5). It is built as: copyright (with `©`), licence id (or "licentie van bundel <library name>" when absent), lyricist and composer values, and source, joined by ` · `, in that order, truncated at a character boundary to 200 characters.

---

## 7. Lyrics body

### 7.1 Environments

| Environment | Start | End | Shown as |
|---|---|---|---|
| Verse | `{start_of_verse}` / `{sov}` | `{end_of_verse}` / `{eov}` | Verse with label "Couplet n" / "Verse n" (n counts verses in the file) |
| Chorus | `{start_of_chorus}` / `{soc}` | `{end_of_chorus}` / `{eoc}` | Chorus with label "Refrein" / "Chorus", set off from verses (indent or weight) [FR-SING-10] |
| Bridge | `{start_of_bridge}` / `{sob}` | `{end_of_bridge}` / `{eob}` | Bridge with label "Brug" / "Bridge" |
| Tab | `{start_of_tab}` / `{sot}` | `{end_of_tab}` / `{eot}` | Not shown in P1 (P3 musician view) |
| Grid | `{start_of_grid}` / `{sog}` | `{end_of_grid}` / `{eog}` | Not shown in P1 (P3) |
| ABC | `{start_of_abc}` | `{end_of_abc}` | Not shown in P1 (P3 notation) |
| Other | `{start_of_<name>}` | `{end_of_<name>}` | Unknown environment: the whole block is skipped (section 10.1) |

Rules:

- Environments MUST NOT nest. `{start_of_x}` inside an open environment, or `{end_of_x}` without a matching open `{start_of_x}`, is an error. An environment still open at the end of the file is an error.
- An environment MAY carry a label: `{start_of_verse: Couplet 1}`, `{start_of_chorus: Refrein (2x)}`. The label is shown exactly as written. **Without a label the app generates one in the UI language** (see the table). Maintainers SHOULD omit labels unless the song needs a specific one, so Dutch and English users each see labels in their own language.
- Verse numbering for generated labels counts verse environments and implicit verses (7.3) in file order, starting at 1.

### 7.2 Chorus repeats

- `{chorus}` repeats the most recently defined chorus at that point in the song. The app shows the chorus text again (not just a "Refrein" marker), so lyrics are complete and in sung order [FR-SING-10, FR-SING-13].
- `{chorus: <label>}` repeats the chorus whose definition carried exactly that label, if one exists; otherwise the most recent chorus. The repeat is shown with the given label.
- `{chorus}` before any chorus has been defined is an error.
- Each `{chorus}` directive expands to **one** repetition in the canonical line list. A chorus sung twice in a row is either written as two `{chorus}` lines, or as one `{chorus: Refrein (2x)}` whose "(2x)" is a display hint only. Songs with timing data (P2) SHOULD use the explicit form so each repetition has its own line numbers and times.

### 7.3 Implicit verses and blank lines

Content lines outside any environment are allowed and form **implicit verses**: each run of content lines separated by blank lines is one verse. This keeps the simplest possible file valid:

```
{title: Vader Jacob}
{meta: id vader-jacob}
{meta: language nl}

Vader Jacob, Vader Jacob,
slaapt gij nog, slaapt gij nog?
```

Inside an environment a blank line is a visual gap, not a verse boundary. Blank lines are never lyric lines.

### 7.4 Repeats and markings inside the text

- Repeat counts are written in the text as the maintainer wants singers to read them: `(2x)` at the end of a line or in a label. The app does not expand them.
- `{comment: …}` / `{c: …}`, `{comment_italic: …}` / `{ci: …}` and `{comment_box: …}` / `{cb: …}` are **shown** as a remark in the lyric view at that position (e.g. "gesproken", "langzamer", "canon: tweede groep begint hier"). They are not lyric lines and do not count in the canonical line list.
- `#` file comments are never shown. Use them for maintainer notes.

### 7.5 Layout directives

ChordPro layout directives (`{new_page}`, `{np}`, `{column_break}`, `{columns}`, `{textfont}`, `{textsize}`, `{textcolour}`, `{chordfont}` …, `{titles}`, `{image}`) are accepted and ignored. The app owns the layout [FR-DISP-05, FR-DISP-06].

---

## 8. Canonical form and content hash

The BLE protocol identifies a song by the first 16 bytes of SHA-256 over "the song file bytes exactly as stored in the library (canonicalised as defined by the song-format spec)" (BLE section 5). This section defines that canonicalisation. The intent is that a file written as section 5.1 requires is **already** canonical, so canonical bytes equal repository bytes and git stores exactly what is hashed.

### 8.1 Canonicalisation steps

Applied to the file bytes in this order:

1. Decode as UTF-8. Invalid UTF-8 is an error; the file is rejected (section 11).
2. Remove a leading byte order mark (`U+FEFF`) if present.
3. Normalise to Unicode NFC.
4. Replace every CRLF and every lone CR with LF.
5. On every line, remove trailing spaces and tabs.
6. Remove all trailing empty lines, then append exactly one LF, so the file ends with a single LF (an empty file canonicalises to a single LF, which then fails validation for lack of content).
7. Encode as UTF-8.

Nothing else changes: comments, directive order, blank lines inside the body, chords and unknown directives are all part of the canonical form and of the hash. Editing a comment therefore changes the hash; this is accepted, since the only effect is that singers with the previous revision receive the song once more over BLE.

### 8.2 Content hash

```
hash = SHA-256(canonical form)[0 .. 16)
```

The leader broadcasts the canonical form as the `file` field of the BLE `song` document, and serves the same bytes on repair; the singer verifies the hash over the bytes it receives (BLE section 9.5). The singer then stores and parses exactly those bytes.

### 8.3 Non-canonical files in a repository

The app MUST accept a file that is not in canonical form, canonicalise it on import, hash and store the canonical form, and report a warning naming the deviation (BOM, CRLF, trailing whitespace, missing or multiple final newlines, NFD). This keeps a library usable when a maintainer's editor slips, while the warning gets the repository fixed. Two apps that import the same non-canonical file produce the same canonical form, so hashes still match across devices.

### 8.4 What the hash is used for

| Use | Reference |
|---|---|
| Match leader and singer copies of a song over BLE, from any library revision | FR-BLE-12, BLE section 5 and 10.3 |
| Verify a song received over BLE before showing it | FR-BLE-04, BLE section 9.5 |
| Detect changed songs between two library revisions for the "what changed" report | FR-LIB-11 |
| Hash-verify a delegate's upload | BLE section 9.8 |

---

## 9. Canonical line list

The BLE `pos` field (BLE section 5) is "1-based line index into the current song's canonical line list". Leader and singers compute the list from the same canonical bytes with the algorithm below, so position 7 means the same line everywhere. The same numbering is used by timing data (section 10.2).

### 9.1 Algorithm

Input: the canonical form, split on LF into source lines. State: `env` (none, verse, chorus, bridge, or other), `choruses` (ordered list of (label, lines)), `current_chorus_lines`, output list `L`.

For each source line:

1. **Blank line** → nothing. (Ends an implicit verse for labelling purposes; no effect on `L`.)
2. **File comment** (`#…`) → nothing.
3. **Directive**:
   - `{start_of_verse}`, `{start_of_chorus}`, `{start_of_bridge}` (and short forms) → `env` becomes verse / chorus / bridge. For a chorus, start collecting `current_chorus_lines` and remember the label.
   - `{start_of_<anything else>}` → `env` becomes *other*.
   - matching `{end_of_…}` → for a chorus, append (label, `current_chorus_lines`) to `choruses`; `env` becomes none.
   - `{chorus}` or `{chorus: label}` → select the chorus (section 7.2) and append **each of its lines** to `L` again.
   - any other directive → nothing.
4. **Content line** while `env` is *other* → nothing (tab, grid, abc, unknown).
5. **Content line** otherwise:
   - compute the *display text*: remove every `[…]` token (chords and annotations), resolve escapes (section 5.4), replace tabs by spaces, trim trailing whitespace;
   - if the display text is empty (a chord-only line) → nothing;
   - else append the display text to `L`; if `env` is chorus, also to `current_chorus_lines`.

Output: `L`, numbered from 1. Position `n` refers to `L[n]`. `pos` 0 means "no position"; `pos` 255 means line 255 or beyond.

### 9.2 Properties

- Only lines a P1 lyric view shows are numbered: labels, comments, remarks, blank gaps and chord-only lines are not in the list. A P3 musician view shows more lines but keeps the same numbers for the lyric lines.
- Chorus repeats are expanded, so the leader can point at the second chorus and karaoke timing can give it its own times. The expansion is deterministic because `choruses` is built in file order.
- Unknown environments contribute nothing, which is what makes P3 notation blocks safe for P1 apps (section 10.1).
- The display text is what the lyric view shows and what lyric search indexes [FR-LIB-31].

### 9.3 Worked example

For the Dutch example in section 12.1 the list has 18 entries:

| n | Section (generated label) | Display text |
|---|---|---|
| 1 | Couplet 1 | De zon is weg, de lucht wordt grijs, |
| 2 | Couplet 1 | de eerste ster staat al te stralen. |
| 3 | Couplet 1 | We schuiven dichter om het vuur, |
| 4 | Couplet 1 | hier kan vanavond niemand verdwalen. |
| 5 | Refrein | Vuur in de nacht, vonken in de lucht, |
| 6 | Refrein | zing maar mee, zing maar mee. |
| 7 | Refrein | Vuur in de nacht, de dag is op de vlucht, |
| 8 | Refrein | en de nacht zingt met ons mee. |
| 9 | Couplet 2 | De thee is heet, de koek is op, |
| 10 | Couplet 2 | de rook trekt langzaam in je jas. |
| 11 | Couplet 2 | We zingen door, we houden niet op, |
| 12 | Couplet 2 | alsof het nog maar zeven uur was. |
| 13 | Brug | En als het vuur straks langzaam dooft, |
| 14 | Brug | dan blijft het lied nog in ons hoofd. |
| 15 | Refrein (repeat of 5–8) | Vuur in de nacht, vonken in de lucht, |
| 16 | Refrein | zing maar mee, zing maar mee. |
| 17 | Refrein | Vuur in de nacht, de dag is op de vlucht, |
| 18 | Refrein | en de nacht zingt met ons mee. |

The English example (section 12.2) has the same 18 lines once chords are removed, which is why its timing block numbers 1 to 18.

---

## 10. Extensions and versioning

### 10.1 Ignore rules (what keeps P1 apps working)

A P1 app MUST implement these rules; they are what FR-LIB-06 depends on:

1. An unknown directive is ignored.
2. An unknown `{start_of_<name>}` … `{end_of_<name>}` block is skipped entirely, including its content lines.
3. `[…]` tokens in content lines are removed from the display text.
4. Unknown `{meta}` names are kept as opaque text and MAY be shown on the info screen, but have no other effect.
5. Unknown fields in `zangbundel.json` are ignored.

With these rules, phase 2 and phase 3 content can be added to any song file at any time. The reverse also holds: a P2 or P3 app shows a plain P1 file without timing or chords exactly as a P1 app does.

### 10.2 Phase 2: timing data [FR-KAR-01]

Timing is added with reserved custom directives (the `x_` prefix is what ChordPro reserves for extensions; `zb` scopes them to Zangbundel). They are written **after** the lyrics body, as a block, so the lyrics stay readable.

**Per line (P2 MUST):**

```
{x_zb_time: <n> <start>}
{x_zb_time: <n> <start> <end>}
```

- `n` is the 1-based canonical line number (section 9). `start` and `end` are seconds from the start of the song, decimal with up to three places (`14.5`, `14.500`). `end` is optional; without it a line ends when the next timed line starts, or at `{duration}` for the last line.
- A timed song SHOULD time every canonical line. Lines without an entry are highlighted together with the previous timed line.
- Entries MUST be in increasing order of `n` and of `start`. An `n` beyond the end of the list, a duplicate `n`, or a non-increasing `start` is an error for a P2 app and a warning for a P1 app (which does not use the data but can check the shape).
- Chorus repeats have their own line numbers, so a chorus sung at 0:14 and again at 0:46 is timed twice. Write repeats with explicit `{chorus}` directives (section 7.2).

**Per syllable (P2 MAY, reserved):**

```
{x_zb_syllables: <n> <t1>@<c1> <t2>@<c2> …}
```

`tN` is the start time in seconds of the syllable that begins at Unicode code point offset `cN` (0-based) of the display text of line `n`. This anchors syllables without markers inside the lyrics. The P2 design decides whether this shape is final (open question Q6).

**Tempo changes, count-in, offsets** are not in the file format: they are gathering-time controls in the BLE protocol (FR-KAR-05, FR-KAR-06).

The `KARAOKE` flag in the beacon, the `Time` characteristic and the broadcast tick document reserved in the BLE protocol (section 14 there) synchronise the clock; the file only carries the times.

### 10.3 Phase 3: chords and notation [FR-INST-01]

- **Chords** are the ChordPro inline form, already allowed in P1 files and ignored by P1 apps (section 5.4). P3 defines the chord grammar (root, quality, bass note), transposition [FR-INST-03], `{key}`, `{capo}`, `{transpose}` and `{define}` for chord diagrams [FR-INST-04].
- **Tabs and grids** use the standard `{start_of_tab}` and `{start_of_grid}` environments.
- **Melody notation** is embedded as ABC inside `{start_of_abc}` … `{end_of_abc}` (ChordPro 6 delegated environment). P1 apps skip the block.
- **MusicXML** or other large notation is not embedded. A song references a file in the repository with `{meta: notation <path relative to repository root>}`, e.g. `{meta: notation notation/vuur-in-de-nacht.musicxml}`. The file is not part of the song's canonical form or hash and does not travel over BLE; it is available only to devices that have the library (as FR-LIB-22 songs are).
- Chord-only lines (no lyric text after chord removal) are shown by the musician view and are not canonical lines (section 9.1).

### 10.4 Format versioning [NFR-MAINT-01]

- `format_version` in `zangbundel.json` is `major.minor`. **Major** changes break the rules above (a new line type, a new canonicalisation, a changed line-list algorithm). **Minor** changes are additive: new directives, new meta names, new environments, new `zangbundel.json` fields. An app that implements major 1 reads any `1.x` library and ignores what it does not know.
- This document defines **1.0**. The P2 timing directives are planned as **1.1** and the P3 chord grammar and notation as **1.2**; both are described here so maintainers can prepare, but a 1.0 app treats them under the ignore rules.
- The app states the format versions it supports on the About screen, next to the app version [FR-GEN-07, NFR-MAINT-01].
- Song files carry no version of their own. A song is parsed under the rules of its library's `format_version`; a song received over BLE without its library is parsed under the highest major the app supports, which is safe because of the ignore rules.

---

## 11. Validation and import reporting [FR-LIB-07]

### 11.1 Levels

| Level | Effect |
|---|---|
| **Library error** | The library is not imported (or the previous revision stays installed, FR-LIB-15). |
| **Song error** | The song is skipped; the rest of the library is imported. |
| **Warning** | The song is imported; the maintainer should fix the file. |
| **Info** | Nothing to fix; shown only in the detailed report. |

### 11.2 Report

The import produces a report the user can open after import or update, and that the app keeps per library until the next import:

| Field | Content |
|---|---|
| `library` | Library id and name |
| `revision` | The git revision imported [FR-LIB-17] |
| `path` | Path of the file relative to the repository root (`songs/nl/vuur-in-de-nacht.cho`, `zangbundel.json`) |
| `line` | Line number in the file, 1-based, when applicable |
| `level` | error / warning / info |
| `code` | Code from section 11.3, stable across app versions |
| `message` | Localised explanation in the UI language, including the offending value where useful |

The summary line reads, for example, "42 liederen geïmporteerd, 2 overgeslagen, 5 waarschuwingen". Skipped songs are listed by file name with their first error, which is the FR-LIB-07 requirement. The report SHOULD be exportable as plain text so a user can send it to the maintainer.

The "what changed" view after an update [FR-LIB-11] is computed from song ids and content hashes of the previous and the new revision: added (new id), removed (id gone), changed (same id, different hash).

### 11.3 Codes

**Library level** (import refused):

| Code | Condition |
|---|---|
| L001 | `zangbundel.json` missing at the repository root |
| L002 | `zangbundel.json` is not valid JSON or a MUST field is missing or has the wrong type |
| L003 | `format` is not `zangbundel-library` |
| L004 | `format_version` major is not supported by this app ("update the app") |
| L005 | `min_app_version` is newer than this app ("update the app") |
| L006 | `id` is invalid, or clashes with an installed library from a different URL |
| L007 | Licence file named in `license.file` is missing |
| L008 | `songs/` directory missing or contains no `.cho` file (warning, not error, when the user confirms an empty library) |

**Song level** (song skipped):

| Code | Condition |
|---|---|
| S001 | File is not valid UTF-8, or contains control characters other than LF, CR, tab |
| S002 | File larger than 64 KiB |
| S003 | Required metadata missing or invalid: id, title or language |
| S004 | `id` does not equal the file name |
| S005 | `id` duplicates another song in this library (both files are reported; the first in path order is imported) |
| S006 | A single-valued directive occurs more than once |
| S007 | Environment not closed, closed without being open, or nested |
| S008 | `{chorus}` before any chorus definition |
| S009 | `{new_song}` present (one song per file) |
| S010 | No lyric lines (empty canonical line list) |
| S011 | Malformed directive line (`{` without closing `}` or the reverse) |
| S012 | Invalid value: `redistribute` not `yes`/`no`, `year` not four digits, `variant-group` or `tag` not in id syntax |

**Warnings** (song imported):

| Code | Condition |
|---|---|
| W001 | File not in canonical form; the deviation is named (BOM, CRLF, trailing whitespace, final newline, NFD). Hash computed over the canonical form. |
| W002 | Recommended metadata missing: lyricist/composer, copyright, license, source, tag |
| W003 | Title longer than 60 characters |
| W004 | Canonical line list longer than 254 lines |
| W005 | Lyric line longer than 120 characters |
| W006 | Directive name not lower case |
| W007 | Tab character in a content line |
| W008 | File larger than 8 KiB |
| W009 | Timing data (`x_zb_time`, `x_zb_syllables`) malformed, out of order, or referring to a line that does not exist |
| W010 | `license` not an SPDX identifier, `public-domain` or `proprietary` |
| W011 | `variant-group` with only one member in this library |
| W012 | `notation` path does not exist in the repository |

**Info:**

| Code | Condition |
|---|---|
| I001 | Unknown directive or meta name (listed once per name per file) |
| I002 | Unknown environment skipped |
| I003 | Non-`.cho` file under `songs/` ignored (counted, not listed) |

### 11.4 Where validation runs

- **In the app**, on every import and update, before the new revision replaces the old one [FR-LIB-15, NFR-SEC-01].
- **In the repository**, as a command-line validator that applies the same rules, intended for a pre-commit hook or CI in the library repository, so maintainers see problems before users do. The validator is a deliverable of the architecture document; its rules are this section.
- **Over BLE**, a received song file is parsed with the same parser; a song that fails with a song-level error is treated as a failed reception [FR-BLE-04] and the leader's copy is reported to the leader.

---

## 12. Examples

Both examples are original texts written for this specification and released under CC0, so they can be copied into a test library. They are translations of each other and share a variant group. The Dutch file is a plain phase 1 song. The English file additionally carries chords (phase 3) and line timing (phase 2); a phase 1 app shows it exactly like the Dutch one, with English words.

### 12.1 Dutch: `songs/nl/vuur-in-de-nacht.cho`

```
# Vuur in de nacht - voorbeeldlied bij design/song-format.md
{title: Vuur in de nacht}
{meta: id vuur-in-de-nacht}
{meta: language nl}
{lyricist: Zangbundel-project}
{composer: Percy Montrose}
{meta: melody Oh My Darling, Clementine}
{copyright: 2026 Zangbundel-project}
{meta: license CC0-1.0}
{meta: source Geschreven als voorbeeld bij de formaatspecificatie}
{meta: tag kampvuur}
{meta: tag avond}
{meta: variant-group vuur-in-de-nacht}

{start_of_verse}
De zon is weg, de lucht wordt grijs,
de eerste ster staat al te stralen.
We schuiven dichter om het vuur,
hier kan vanavond niemand verdwalen.
{end_of_verse}

{start_of_chorus}
Vuur in de nacht, vonken in de lucht,
zing maar mee, zing maar mee.
Vuur in de nacht, de dag is op de vlucht,
en de nacht zingt met ons mee.
{end_of_chorus}

{start_of_verse}
De thee is heet, de koek is op,
de rook trekt langzaam in je jas.
We zingen door, we houden niet op,
alsof het nog maar zeven uur was.
{end_of_verse}

{start_of_bridge}
En als het vuur straks langzaam dooft,
dan blijft het lied nog in ons hoofd.
{end_of_bridge}

{chorus}
```

What the app shows (Dutch UI): the title, then "Couplet 1" with four lines, "Refrein" with four lines set off, "Couplet 2", "Brug", and "Refrein" with the four chorus lines again. An English UI shows the same with "Verse 1", "Chorus", "Verse 2", "Bridge", "Chorus". The info screen shows lyricist, composer, melody, © 2026 Zangbundel-project, CC0-1.0, source and the tags "Kampvuur" and "Avond" (labels from `zangbundel.json`).

### 12.2 English: `songs/en/fire-in-the-night.cho`

```
# Fire in the Night - example song for design/song-format.md
# English variant of "Vuur in de nacht". Carries chords (P3) and line timing (P2);
# a P1 app ignores both and shows plain lyrics.
{title: Fire in the Night}
{meta: id fire-in-the-night}
{meta: language en}
{lyricist: Zangbundel project}
{composer: Percy Montrose}
{meta: melody Oh My Darling, Clementine}
{copyright: 2026 Zangbundel project}
{meta: license CC0-1.0}
{meta: source Written as an example for the song format specification}
{meta: tag kampvuur}
{meta: tag avond}
{meta: variant-group vuur-in-de-nacht}
{key: G}
{tempo: 96}
{duration: 1:00}

{start_of_verse: Verse 1}
The [G]sun is gone, the sky turns grey,
the [D]first star rises, clear and [G]bright.
We shuffle closer to the fire,
no[D]body's getting lost to[G]night.
{end_of_verse}

{start_of_chorus: Chorus}
[C]Fire in the night, [G]sparks up in the air,
[D]sing along, sing a[G]long.
[C]Fire in the night, the [G]day has gone somewhere,
[D]and the night joins in our [G]song.
{end_of_chorus}

{start_of_verse: Verse 2}
The [G]tea is hot, the biscuits gone,
the [D]smoke has settled in your [G]coat.
We carry on from song to song,
un[D]til the last one clears their [G]throat.
{end_of_verse}

{start_of_bridge: Bridge}
And [Em]when the fire burns [C]down to red,
the [D]song is still inside our [G]head.
{end_of_bridge}

{chorus: Chorus (2x)}

# Line timing (P2): canonical line number, start time in seconds.
{x_zb_time: 1 2.0}
{x_zb_time: 2 5.0}
{x_zb_time: 3 8.0}
{x_zb_time: 4 11.0}
{x_zb_time: 5 14.5}
{x_zb_time: 6 17.5}
{x_zb_time: 7 20.5}
{x_zb_time: 8 23.5}
{x_zb_time: 9 27.0}
{x_zb_time: 10 30.0}
{x_zb_time: 11 33.0}
{x_zb_time: 12 36.0}
{x_zb_time: 13 39.5}
{x_zb_time: 14 42.5}
{x_zb_time: 15 46.0}
{x_zb_time: 16 49.0}
{x_zb_time: 17 52.0}
{x_zb_time: 18 55.0}
```

Notes on this file:

- The explicit labels "Verse 1", "Chorus", "Bridge" are shown as written in every UI language. The Dutch file leaves them out and gets localised labels; that is the recommended style unless a specific label is needed.
- `{chorus: Chorus (2x)}` has no chorus definition with that exact label, so it repeats the most recent chorus and shows it labelled "Chorus (2x)". It expands to one repetition (lines 15–18); the "(2x)" is only a hint to singers. A karaoke-timed song that really sings the chorus twice writes `{chorus}` twice and times 22 lines.
- A P1 app removes the chord tokens: line 1 is shown as "The sun is gone, the sky turns grey," and line 4 as "nobody's getting lost tonight." The `x_zb_time` directives are unknown to it and ignored (reported once as I001).
- `{key}`, `{tempo}` and `{duration}` are shown on the info screen in P1 and used by the musician view and karaoke mode later.
- The variant group `vuur-in-de-nacht` links both files, so the leader can push the English words to an English-speaking group [FR-LEAD-07] and a singer can switch between the two [FR-LANG-06].

---

## 13. Requirements traceability

| Requirement | Where satisfied |
|---|---|
| FR-LANG-05 | `{meta: language}` MUST, BCP 47 (6.1, 6.2) |
| FR-LANG-06 | `{meta: variant-group}` (6.1), example 12 |
| FR-LANG-07 | `{sorttitle}` and article rule (6.1) |
| FR-LIB-01 | Library is a git repository (3) |
| FR-LIB-03 | Plain-text ChordPro profile (2, 5) |
| FR-LIB-04 | Song metadata table (6.1): id, title, language, lyrics MUST; the rest SHOULD/MAY |
| FR-LIB-05 | `zangbundel.json` fields (4.2) |
| FR-LIB-06 | Ignore rules (10.1), P2 and P3 extensions (10.2, 10.3) |
| FR-LIB-07 | Validation levels, report and codes (11) |
| FR-LIB-11 | "What changed" from ids and hashes (11.2) |
| FR-LIB-17 | Revision in the import report (11.2) |
| FR-LIB-20, FR-SING-15 | Attribution fields and licence summary (6.2) |
| FR-LIB-21 | `license.file` and `license.summary` in `zangbundel.json` (4.2) |
| FR-LIB-22 | `{meta: redistribute no}` (6.1) |
| FR-LIB-23 | App never alters metadata or text; canonicalisation changes only encoding and whitespace (6.2, 8) |
| FR-LIB-30 | Tags and localised tag labels (4.2, 6.1) |
| FR-LIB-31 | Display text is the search text (9.2) |
| FR-SING-10 | Environments, labels, chorus expansion (7.1, 7.2) |
| FR-SING-13 | Section boundaries from environments (7.1) |
| FR-LEAD-05, FR-SING-12 | Canonical line list for `pos` (9) |
| FR-LEAD-07 | Variant group (6.1) |
| FR-BLE-04, FR-BLE-12 | Canonical form and content hash (8) |
| FR-BLE-05 | Size limits (5.5) |
| FR-DATA-03 | Stable song id (3.2) |
| FR-KAR-01 | `x_zb_time`, `x_zb_syllables` (10.2) |
| FR-INST-01, 03, 04, 06 | Chords, tab, grid, ABC, notation file (10.3); chords ignored in P1 (5.4) |
| NFR-SEC-01 | Library-level errors refuse a corrupt library (4.3, 11.3) |
| NFR-PORT-01 | Format defined on bytes and lines, no platform dependency (5, 8, 9) |
| NFR-MAINT-01 | Format version and compatibility rules (10.4) |
| FRD constraint 7 | (library id, song id) unique (3.2, 4.2) |
| FRD Q4 | Decided: ChordPro profile (2) |

---

## 14. Open questions

| # | Question | Impact |
|---|---|---|
| Q1 | Is JSON acceptable for `zangbundel.json`, or do maintainers want YAML for its comments and multi-line strings despite the coercion risk (section 4.1)? | Library file, parser dependency |
| Q2 | Timing is keyed by canonical line number (10.2). Inserting a lyric line shifts every later number, so timed songs need a renumbering tool or an inline alternative such as a timing directive immediately before each line. Which do maintainers prefer? | P2 format, maintainer tooling |
| Q3 | Variant groups link songs within one library only. Should a group id be allowed to span libraries (`<library id>/<group>`), given that translations often come from different maintainers? | FR-LANG-06, library governance (FRD Q3) |
| Q4 | Should the project define a fixed tag vocabulary (with the NL/EN labels in a shared file) so filters work across libraries, or is a per-library `tags` map enough? | FR-LIB-30 across libraries |
| Q5 | Generated section labels follow the UI language (7.1). Should they follow the song language instead, so a Dutch song in an English UI still says "Refrein"? | Lyric view |
| Q6 | Per-syllable timing anchors syllables by code point offset (10.2). Is that robust enough for maintainers, or are explicit syllable markers in the lyrics (which P1 would have to strip) preferable? | P2 format |
| Q7 | Should the hash cover only the parsed content (metadata values plus canonical lines) instead of the whole file, so comment-only edits do not invalidate singers' cached copies? Whole-file hashing is simpler and matches the BLE document; the cost is one extra transfer after such an edit. | BLE section 5; section 8.2 here |
| Q8 | Should `name` and `description` in `zangbundel.json` be localisable (`{ "nl": …, "en": … }`) like tag labels? | Library screen |
| Q9 | Chord-only lines are not canonical lines (9.1). Should a P3 musician view be able to follow the leader to an instrumental line, which would require numbering them? | Line list stability between P1 and P3 |
| Q10 | The default library for FR-LIB-02: should this repository host it (under `library/` or as a separate repository), and who maintains licence checks on contributions? | FRD Q3, release planning |
| Q11 | The command-line validator (11.4): part of this repository, or a separate tool shared with library maintainers? | Architecture document |
