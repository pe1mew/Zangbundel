# Zangbundel – Song Format and Library Layout

| Field | Value |
|---|---|
| Document | Song file format and library repository layout |
| Project | Zangbundel – campfire songbook app |
| Format version | 1.1 (draft; 1.0 plus setlists, section 13) |
| Document version | 0.15 (draft) |
| Date | 2026-10-28 |
| Author | Remko Welling |
| Status | Draft for review |
| Depends on | `design/functional-requirements.md` v0.21, `design/ble-protocol.md` v0.13 |

| Version | Date | Change |
|---|---|---|
| 0.1 | 2026-10-18 | First draft. Answers FRD open question Q4 (ChordPro). |
| 0.2 | 2026-10-18 | Added section 12: the release archive (`.zbl`), its manifest, repository snapshots, release index, build and load procedures, validation codes A001–A103. Later sections renumbered (examples 13, traceability 14, open questions 15). `release_index` in `zangbundel.json`; `releases.json` in the layout. Q12–Q14. |
| 0.3 | 2026-10-19 | Depends on FRD v0.9 and BLE v0.11 (v0.10 withdrew the download instruction, v0.11 added the helper code and the explicit leave; neither touches this format): FR-LEAD-30 and 31 withdrawn, so the leader's download instruction in 12.1 and 12.7 is marked not used in phase 1. Section 13, setlists (`.zbs`), added as format 1.1 and archive 1.1: the setlist file, the `setlists/` directory in repositories and archives, the `setlists` manifest field, validation codes SL001–SL104 (`design/guiDesign.md` D-59, Q18). Later sections renumbered (examples 14, traceability 15, open questions 16). |
| 0.4 | 2026-10-20 | Q5 resolved: generated section labels follow the app language, as 7.1 already says. Q8 resolved: a library's `name` and `description` are not localisable; tag labels stay localisable. Depends on FRD v0.10 and BLE v0.12. No change to the format (`design/guiDesign.md` D-77). |
| 0.5 | 2026-10-22 | Depends on FRD v0.11 and BLE protocol v0.13. Q11 resolved: the command-line validator and the release tool live in the library repository; the app has its own implementation, kept in step by a shared conformance corpus (`design/architecture.md` A-21, A-22). Q13 resolved: the app imports release archives and snapshots only and has no git client (A-11). No format change. |
| 0.6 | 2026-10-25 | Depends on FRD v0.12 (open question Q14 on NFR-PERF-01); nothing in this document changes. |
| 0.7 | 2026-10-25 | Depends on FRD v0.13 (FR-SING-05, the singer's reception bar); no other change. |
| 0.8 | 2026-10-26 | Depends on FRD v0.14 (Q3 resolved: the default library is `github.com/pe1mew/Zangbundel-bibliotheek`). Q10 resolved with it. No format change. |
| 0.9 | 2026-10-26 | Depends on FRD v0.15 (FR-NET-01 and FR-NET-03 widened to setlist addresses); no other change. |
| 0.10 | 2026-10-26 | Depends on FRD v0.16 (FR-BLE-18: the setlist's songs are broadcast ahead; FR-SING-05 amended); no other change. |
| 0.11 | 2026-10-26 | Depends on FRD v0.17 (FR-SING-05: the reception view is a hidden feature, opened by a long press only); no other change. |
| 0.12 | 2026-10-26 | Depends on FRD v0.18 (requests, likes and the delegate role moved to phase 2); no other change. |
| 0.13 | 2026-10-27 | Depends on FRD v0.19 (FR-DATA-09: the opt-in campfire statistics on the leader's device, export by hand only); no other change. |
| 0.14 | 2026-10-28 | Depends on FRD v0.20 (Q2 and Q4 closed, Q11 further resolved: open questions the design had already answered); no other change. |
| 0.15 | 2026-10-28 | Depends on FRD v0.21; no format change. Q12 (signed archives) deferred to after 1.0 by the owner; Q2 and Q6 deferred to phase 2, Q9 to phase 3. |

---

## 1. Introduction

### 1.1 Purpose

This document specifies the files a Zangbundel **library** consists of: the layout of the git repository, the library-level metadata file, and the format of a **song file**. It defines how a song file is turned into the lines the app shows, how a song file is canonicalised and hashed so that leader and singers can recognise the same song over BLE, and how the format grows in phase 2 (karaoke timing) and phase 3 (chords and notation) without breaking phase 1 apps.

It is written for two audiences: **library maintainers**, who edit song files with a text editor and git, and **app developers**, who implement the parser, the validator and the import.

### 1.2 Scope

In scope: the repository layout, `zangbundel.json`, the `.cho` song file, canonical form and content hash, the canonical line list, validation and import reporting, the extension rules for later phases, and the release archive (`.zbl`) with its manifest and release index, which is the unit the app downloads or opens from a file.

Out of scope: the network and storage code that fetches a repository or an archive (architecture document), the on-device storage model, the BLE transport of a song (`design/ble-protocol.md`), and the UI that renders a song.

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
| **Release archive** | A `.zbl` ZIP file built from a tagged revision of a library: canonical song files, metadata, licence and a manifest (section 12). What the app downloads, opens from a file, or passes to another phone. |
| **Manifest** | `zangbundel-archive.json` inside a release archive: revision, version, build time, and the content hash of every song (12.4). |
| **Snapshot** | A plain ZIP of the repository tree without a manifest; accepted with reduced guarantees (12.5). |
| **Release index** | `releases.json`: the list of a library's releases with archive URLs, sizes and digests, used for update checks (12.6). |

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
├── releases.json           release index, written by the release tooling (12.6)  SHOULD
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
| `release_index` | string | MAY | HTTPS URL of the library's release index (`releases.json`, section 12.6). With it the app can check for and download releases; without it the app can only update through git or a URL the user supplies. |
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

For the Dutch example in section 14.1 the list has 18 entries:

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

The English example (section 14.2) has the same 18 lines once chords are removed, which is why its timing block numbers 1 to 18.

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
- This document defines **1.0** and, with section 13 (setlists), **1.1**. The P2 timing directives are planned as **1.2** and the P3 chord grammar and notation as **1.3**; both are described here so maintainers can prepare, but a 1.0 app treats them under the ignore rules.
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

## 12. Release archive (`.zbl`)

A library is maintained in git, but the app does not need git to use it. A **release archive** is a ZIP file that the library's release tooling builds from a tagged revision of the repository: the canonical song files, the library metadata, the licence, and a manifest that records where the content came from and the content hash of every song. It is the unit the app downloads, opens from a file, is pointed at by the leader, and can pass on to another phone.

### 12.1 What the archive is for

| Use | How | FRD |
|---|---|---|
| **Download a library over HTTPS** without a git client in the app: the app fetches the archive of the latest release, or of a chosen release from the release index (12.6). | URL of an archive or of a release index | FR-LIB-10, FR-LIB-11, FR-LIB-13, FR-LIB-14 |
| **Import from a local file** on a phone that never has internet: the archive arrives by USB, SD card, e-mail attachment, download at home. | Open a `.zbl` file from the file manager or share sheet | FR-LIB-16 |
| **Leader's download instruction** names an archive URL, so singers with internet fetch exactly the revision the leader has. *Not used in phase 1: FR-LEAD-30 and FR-LEAD-31 were withdrawn in FRD v0.9; the BLE `download` document is kept reserved.* | BLE `download` document, `url` and `rev` | FR-LEAD-30, FR-LEAD-31 (withdrawn) |
| **Phone-to-phone without internet**: a device exports an installed library as an archive and hands it over with Quick Share or Bluetooth file transfer, the same route as the app itself (FRD section 8.5). | Export + system share sheet; the receiver opens the file | FR-LIB-16, FR-DIST-05 pattern |
| **Reproducible record** of what a release contained, independent of the hosting platform. | Manifest with revision and per-song hashes | FR-LIB-17, FR-LIB-11 |

Importing from the git repository directly (clone or pull) remains allowed; whether the app implements it at all is an architecture decision (open question Q13). An app that supports only archives satisfies FR-LIB-10 and FR-LIB-11, since the archive is the "equivalent download of a chosen branch or tag" those requirements allow.

### 12.2 Container

| Property | Rule |
|---|---|
| Format | ZIP (PKWARE APPNOTE), as produced by `zip`, Python `zipfile`, `java.util.zip` and the like. ZIP64 MAY be used; archives stay far below its need. |
| File name | `<library id>-<version>.zbl`, e.g. `zangbundel-voorbeeld-2026.10.0.zbl`. The extension `.zbl` ("Zangbundel library") lets the app register as the handler for the file type; a plain `.zip` opened through the file picker is accepted too. |
| MIME type | `application/vnd.zangbundel.library+zip`. Hosting that serves `application/zip` or `application/octet-stream` is accepted. |
| Entry names | UTF-8 (general-purpose flag bit 11 set), forward slashes, relative to the archive root, no leading `/`, no `.` or `..` components, no drive letters, no backslashes. ≤ 255 bytes per name. Names compared case-sensitively; two entries that differ only in case are an error (A006). |
| Entry types | Regular files only. Directory entries are ignored; symbolic links and other special entries are an error (A005). |
| Compression | *stored* (0) or *deflate* (8) only. Encryption of any kind is an error (A004). |
| Top-level layout | All content at the archive root: `zangbundel-archive.json`, `zangbundel.json`, `songs/…`. Exactly one top-level directory wrapping everything (as GitHub source downloads produce) is accepted only for repository snapshots (12.5). |
| Size limits | Archive ≤ 64 MiB (A002); warning above 16 MiB (A101). Uncompressed total ≤ 256 MiB and ≤ 20 × the compressed size (A003, zip-bomb guard). ≤ 10 000 entries (A002). Individual `.cho` entries follow section 5.5. |
| Duplicate names | An error (A006). |
| Comments and extra fields | Ignored. |

### 12.3 Contents

| Entry | Req. | Content |
|---|---|---|
| `zangbundel-archive.json` | MUST | The manifest (12.4). Distinguishes a release archive from a repository snapshot. |
| `zangbundel.json` | MUST | The library metadata, byte-identical to the repository file at the recorded revision (section 4). |
| licence file | MUST | The file named by `license.file` in `zangbundel.json`, at that path. |
| `songs/**/*.cho` | MUST (≥ 1) | Every song file of the revision that passes validation without a song-level error, **in canonical form** (section 8). The builder canonicalises; the app verifies each file's content hash against the manifest. |
| `notation/**` | MAY | Notation attachments referenced by `{meta: notation …}` (section 10.3). |
| `setlists/**/*.zbs` | MAY (archive 1.1) | Setlist files of the revision that pass validation (section 13), in canonical form, listed under `setlists` in the manifest. |
| `README.md` | MAY | Shown nowhere; included so a human who unzips the file sees what it is. |
| anything else | ignored | The app ignores other entries without a message, except that entries outside `songs/`, `notation/` and the root files above count towards the size limits only. |

Songs that failed validation at build time are **not** in the archive; the manifest lists them under `skipped` so the maintainer sees them in the release and the app can report them as part of the import report (section 11.2) without parsing them.

### 12.4 Manifest: `zangbundel-archive.json`

| Field | Type | Req. | Meaning |
|---|---|---|---|
| `format` | string | MUST | Fixed value `"zangbundel-archive"`. |
| `archive_version` | string | MUST | `"<major>.<minor>"` of this archive specification; `"1.0"` for this document (12.10). |
| `library` | string | MUST | Library id; MUST equal `id` in the archive's `zangbundel.json`. |
| `format_version` | string | MUST | Copy of `format_version` from `zangbundel.json`, so the app can refuse an unsupported major before unpacking songs. |
| `version` | string | MUST | The release version as the maintainer names it, normally the git tag without a leading `v`: `2026.10.0`, `1.4.2`. Free text, ≤ 32 characters; the app shows it and never parses it for ordering. |
| `revision` | string | MUST | The full git commit hash the archive was built from, 40 lower-case hex characters (64 for SHA-256 repositories). Becomes the installed revision [FR-LIB-17]. |
| `created` | string | MUST | Build time, RFC 3339 in UTC (`2026-10-18T14:05:00Z`). Used for ordering releases and shown as the release date. |
| `source` | object | MUST | `{ "repository": "<HTTPS URL of the git repository>", "ref": "<tag or branch name>" }`. Informational; the app does not fetch it. |
| `songs` | array | MUST | One entry per included song: `{ "path": "songs/nl/vuur-in-de-nacht.cho", "id": "vuur-in-de-nacht", "hash": "<32 lower-case hex characters>", "size": <bytes> }`. `hash` is the content hash (section 8.2) in hex. Sorted by `path`. |
| `skipped` | array | MAY | Songs excluded at build time: `{ "path": …, "code": "S003", "message": "…" }`. |
| `notation` | array | MAY | Included notation files: `{ "path": …, "sha256": "<64 hex>", "size": <bytes> }`. |
| `setlists` | array | MAY (archive 1.1) | Included setlist files (section 13): `{ "path": "setlists/zomerkamp.zbs", "id": "zomerkamp", "title": "Zomerkamp", "songs": <count>, "sha256": "<64 hex>", "size": <bytes> }`, sorted by `path`. Absent when the library ships no setlists. |
| `builder` | string | MAY | Name and version of the tool that built the archive, e.g. `zangbundel-release 1.0.0`. |
| `previous` | object | MAY | `{ "version": …, "revision": … }` of the release before this one, so a "what changed" report can be made without the previous archive. |

Unknown fields MUST be ignored. The manifest is UTF-8 JSON, LF line endings, keys in the order above, two-space indentation, so that two builders produce the same bytes (12.6).

Example:

```json
{
  "format": "zangbundel-archive",
  "archive_version": "1.0",
  "library": "zangbundel-voorbeeld",
  "format_version": "1.0",
  "version": "2026.10.0",
  "revision": "9f2c1e7d4b0a6c3e8f5d2a1b7c4e9f0d3a6b8c1e2f4d7a9b0c3e6f1d5a8b2c4e",
  "created": "2026-10-18T14:05:00Z",
  "source": {
    "repository": "https://github.com/example/zangbundel-voorbeeld",
    "ref": "v2026.10.0"
  },
  "songs": [
    { "path": "songs/en/fire-in-the-night.cho", "id": "fire-in-the-night", "hash": "3b7d2f9a1c4e6b8d0f2a4c6e8b1d3f5a", "size": 1912 },
    { "path": "songs/nl/vuur-in-de-nacht.cho",  "id": "vuur-in-de-nacht",  "hash": "a1c3e5f7b9d1f3a5c7e9b1d3f5a7c9e1", "size": 1180 }
  ],
  "skipped": [],
  "builder": "zangbundel-release 1.0.0",
  "previous": { "version": "2026.09.0", "revision": "4d1a8e2c7f3b9d5e1a6c8f2b4d7e9a1c3f5b8d2e6a9c1f4b7d0e3a6c9f2b5d8e" }
}
```

### 12.5 Repository snapshots (plain ZIP without manifest)

A ZIP of the repository tree without a manifest, such as the "Download ZIP" a hosting site offers or a maintainer's own `zip -r`, is accepted as a **snapshot** with reduced guarantees:

- The app looks for `zangbundel.json` at the root, or inside exactly one top-level directory; anything else is A001.
- Song files are canonicalised on import (8.3, warning W001 per file) and validated as on a git import.
- The installed revision is recorded as `snapshot-<sha256 of the ZIP, first 16 hex>` and shown as "snapshot, revision unknown". The leader's `libs` list and FR-LEAD-31 therefore never show such an install as "same version as leader"; songs still match by content hash over BLE [FR-BLE-12], which is what matters at the fire.
- Update checks (12.7) are not possible for a snapshot; the library screen says so and offers to replace it by a release archive or repository URL.

### 12.6 Building a release

The release tooling is a deliverable of the architecture document (section 11.4 names the validator it builds on). Its behaviour is specified here so that any implementation, including a maintainer's own script, produces an archive the app accepts.

1. Check out the tagged revision. Refuse to build from a dirty working tree.
2. Run the validator (section 11). Refuse on any library-level error. Exclude songs with a song-level error and record them under `skipped`.
3. Canonicalise every included `.cho` file (8.1) and compute its content hash.
4. Write the manifest (12.4) with `revision` from git, `created` from the commit time of the tag (not the wall clock) and `version` from the tag.
5. Add entries in **sorted path order**, with the manifest first, each entry's modification time set to the commit time, no extra fields, deflate at a fixed level. Two builds of the same revision then produce byte-identical archives, so the archive's SHA-256 can be published and compared.
6. Name the file `<library id>-<version>.zbl`, attach it to the release, and update the release index (below).

**Release index.** A library SHOULD publish a `releases.json` next to its releases so the app can check for updates without downloading an archive [FR-LIB-13]:

| Field | Type | Meaning |
|---|---|---|
| `format` | string | `"zangbundel-releases"` |
| `library` | string | Library id |
| `releases` | array | Newest first: `{ "version", "revision", "created", "url", "size", "sha256" }` with the same meanings as the manifest; `url` is the HTTPS URL of the archive, `sha256` the hex digest of the archive bytes. |

The index is reachable from the library: `zangbundel.json` MAY carry `"release_index": "<HTTPS URL>"` (added to section 4.2), and a repository SHOULD keep `releases.json` at its root, maintained by the release tooling, so the raw-file URL of the default branch works as the index URL. On GitHub, `https://github.com/<owner>/<repo>/releases/latest/download/<library id>-latest.zbl` is a stable URL for the newest archive if the tooling also uploads the archive under that fixed name.

**Versioning of releases** is the maintainer's choice (calendar `2026.10.0` or semantic `1.4.2`). The app orders releases by `created` and identifies them by `revision`; it never compares version strings.

### 12.7 Loading an archive in the app

Whether the archive came from a URL, a file or the share sheet, the app proceeds the same way:

1. **Bound the input.** Refuse a file above 64 MiB before reading entries (A002). Read the central directory; refuse on encryption, bad entry names, special entries, duplicates, entry count, or an uncompressed total that breaks the limits (A002–A006). Never extract to the file system with the entry's own path; read entries into memory or into app-private files the app names.
2. **Read the manifest** (A007–A010) or fall back to snapshot handling (12.5).
3. **Refuse what the app cannot use** before unpacking songs: `format_version` major (L004), `min_app_version` (L005) from `zangbundel.json`, `archive_version` major (A008), library id clash with another configured library (L006).
4. **Verify every song**: the entry exists, its bytes are canonical (else A011: a release archive is built canonical, so a deviation means tampering or a broken builder, not a maintainer slip) and its content hash equals the manifest entry (A012). A song listed in the manifest but missing, or present but unlisted, is A012.
5. **Validate** with section 11 exactly as for a git import. Songs the builder skipped are reported from `skipped` with their recorded codes.
6. **Install atomically** [FR-LIB-15]: the previous revision stays in use until the new one is complete; then switch and delete the old one.
7. **Record** the revision, version, `created`, the source URL or file name, and the archive's SHA-256; show them on the library screen and in the import report (11.2). Compute the "what changed" view [FR-LIB-11] from the previous installed manifest, or from `previous` when the installed one is older than that.

**Update check** [FR-LIB-13]: fetch the release index (HTTP conditional request with `ETag`/`If-None-Match` so an unchanged index costs nothing), compare the newest `revision` with the installed one, and offer the download with its `size` [FR-NET-04]. Verify the downloaded bytes against `sha256` from the index before step 1.

**Leader's download instruction** [FR-LEAD-30, withdrawn in FRD v0.9; reserved for a later phase]: should the BLE `download` document be used, its `url` MAY be an archive URL or a release index URL; `rev` is the manifest `revision`. A singer that already has that revision installed reports "up to date" and downloads nothing. Phase 1 apps do not send or act on it.

**Export** (SHOULD): the app can re-create a release archive of an installed library from its stored canonical files and manifest, for sharing with a nearby phone through the system share sheet. An exported archive is byte-identical to the original when the original was a release archive; a snapshot exports with a manifest whose `revision` is the snapshot id and `source.repository` is the recorded URL or empty.

### 12.8 Archive validation codes

Archive-level codes are **library errors** (section 11.1): the archive is refused and the previous revision stays installed.

| Code | Condition |
|---|---|
| A001 | Not a ZIP file, or no `zangbundel.json` at the root (or, for a snapshot, inside the single top-level directory) |
| A002 | Archive larger than 64 MiB, or more than 10 000 entries |
| A003 | Uncompressed total larger than 256 MiB or more than 20 × the compressed size |
| A004 | An entry is encrypted or uses a compression method other than stored or deflate |
| A005 | An entry is a symbolic link or other special entry |
| A006 | An entry name is not valid (absolute, `..`, backslash, not UTF-8, too long) or duplicates another, including by case |
| A007 | `zangbundel-archive.json` is not valid JSON or a MUST field is missing or has the wrong type |
| A008 | `archive_version` major is not supported by this app ("update the app") |
| A009 | Manifest `library` does not equal `id` in `zangbundel.json`, or `format_version` differs between the two files |
| A010 | Manifest `revision` or `created` malformed |
| A011 | A song entry is not in canonical form |
| A012 | A song's content hash differs from the manifest, a listed song is missing, or a `.cho` entry under `songs/` is not listed |
| A013 | Downloaded archive bytes do not match the `sha256` in the release index |

Warnings (archive imported):

| Code | Condition |
|---|---|
| A101 | Archive larger than 16 MiB |
| A102 | Repository snapshot without manifest: revision unknown, no update checks |
| A103 | `notation` entry listed with a `sha256` that does not match (the file is dropped, the song stays) |
| A104 | `setlists` entry listed with a `sha256` that does not match, or listed but missing (the setlist is dropped, the library stays; section 13.7) |

### 12.9 Security notes

- **Path traversal** ("zip slip") is prevented by never using entry names as file-system paths (12.7 step 1) and by rejecting `..`, absolute names and backslashes (A006).
- **Decompression bombs** are bounded by the size and ratio limits (A002, A003), checked from the central directory before any entry is inflated, and enforced again while inflating.
- **Integrity**: every song is hash-verified against the manifest, and the archive against the index when it came from one. A manifest can still be replaced together with the songs by whoever controls the hosting; authenticity rests on HTTPS to the maintainer's host [NFR-SEC-01]. A detached signature over the manifest, with the public key published in the repository, would close that gap and is open question Q12.
- **Content**: an archive carries text and notation only. Nothing in it is executed, and the app never follows URLs found inside it except the release index named in `zangbundel.json`, and only when the user asks for an update check.

### 12.10 Archive format versioning

`archive_version` follows the same rule as `format_version` (10.4): a **minor** bump adds fields or optional entries; a **major** bump changes the container rules, the manifest's MUST fields or the verification procedure. An app that implements major 1 reads any `1.x` archive and ignores what it does not know. This document defines **1.0** and **1.1**, which adds the optional `setlists` manifest field and the `setlists/` entries (section 13.7).

---

## 13. Setlists (`.zbs`)

A **setlist** (*setlist*) is the ordered list of songs a leader prepares for an evening. The app keeps setlists on the leader's device, but a leader also writes them on a computer, a group shares them as files, and a library may ship them (`design/guiDesign.md` D-54, D-59). This section defines the file. It is **format 1.1**: a 1.0 app never looks under `setlists/` and is unaffected; a 1.1 app reads setlists from libraries that carry them and imports or exports the same file on its own.

### 13.1 File

- File name: `<setlist id>.zbs`; the id has song-id syntax (section 3.2) and MUST be unique among the setlists of a library. In a repository and an archive the files live under `setlists/`, any depth; the path carries no meaning.
- Encoding and canonical form exactly as a song file (sections 5.1 and 8.1): UTF-8 without BOM, NFC, LF, no trailing whitespace, one final LF. The content hash of a setlist is the same construction as for a song (section 8.2) and is used only for the manifest.
- Line types, in the manner of section 5.2: **blank** (ignored), **file comment** (first character `#`, ignored), **directive** (`{name}` or `{name: value}`, section 5.3), and **entry** (anything else).
- Size: at most 64 KiB and at most 200 entries (SL103 above 200; the BLE setlist document and the lyric page are sized for an evening, not a songbook).

### 13.2 Directives

| Directive | Req. | Meaning |
|---|---|---|
| `{title: …}` | MUST | The setlist's name as shown; at most 60 characters. Exactly one, and it MUST come before the first entry. |
| `{meta: id <id>}` | MUST | Equals the file name without `.zbs`. Exactly one. |
| `{meta: description …}` | MAY | One line shown under the name. |
| `{meta: language <tag>}` | MAY | BCP 47 tag of the evening's main language, for filtering; informational. |
| any other directive | ignored | Section 10.1 applies: unknown directives are ignored and listed once as I001. |

### 13.3 Entries

```
<library id>/<song id>
<library id>/<song id>   # <title>
? <title>
```

- A **song entry** names a song by its library id and song id, both in id syntax, separated by one `/`. Everything after the first run of whitespace followed by `#` is a comment, conventionally the title, and is ignored by the app: the title shown is always the one from the song file.
- A **title-only entry** starts with `?` and a space and gives a title in free text, for a song the author knows but whose library they do not have, or that is in no library. The app keeps it as an unresolved entry and tries to match it by title against the installed libraries on import and whenever a library is added (section 13.5).
- Order is sung order. The same song MAY appear more than once.
- Anything else is a malformed entry (SL004).

### 13.4 Example: `setlists/zomerkamp.zbs`

```
# Zomerkamp 2026 - avondprogramma, bijgehouden door de leiding
{title: Zomerkamp}
{meta: id zomerkamp}
{meta: description Eerste avond, rond het grote vuur}
{meta: language nl}

zangbundel-bibliotheek/advocaatje                 # Advocaatje
zangbundel-bibliotheek/hoog-laait-het-vuur        # Hoog laait het vuur
zangbundel-bibliotheek/daar-was-laatst-een-meisje-loos
zangbundel-voorbeeld/vuur-in-de-nacht             # Vuur in de nacht
? Vader Jacob
```

### 13.5 Behaviour in the app

- **Shipped with a library** [FR-LEAD-02]: every valid `setlists/*.zbs` of an installed library appears on the leader's *Setlists* screen under the library's name and colour, read-only; *Dupliceren* makes an editable copy owned by the device. When the library is updated, the shipped setlists follow the library; copies are untouched.
- **Import from a file or URL**: the same parser; the setlist becomes a device-owned setlist. The report lists malformed lines and unresolved entries by line number.
- **Resolution**: a song entry whose library is installed and holds the song is resolved; one whose library is not installed, or whose song id is unknown in that library, is kept as *niet gevonden* with its identifiers, and resolves itself when the library arrives. A title-only entry is matched case- and accent-insensitively against titles and sort titles of installed songs; a single match resolves it, several matches leave it unresolved with the candidates offered to the leader. Unresolved entries are shown in the setlist with the reason (library not installed, song id unknown in that library, no single title match) and are skipped at the fire; the skip is never silent: the leader's page shows the entry's bar with the reason under it, and the leader is told the count before starting (`design/guiDesign.md` D-74).
- **Export**: the app writes the setlist in this format, canonical, with the title as comment on each song entry and `? <title>` for entries that were never resolved, so a file survives a round trip unchanged in meaning.
- **At the start of a gathering** the setlist is copied into the gathering (`design/guiDesign.md` D-55); the file is not touched.
- Setlists are never sent over BLE as files; the gathering's setlist goes out as the protocol's setlist document.

### 13.6 Validation codes

Setlist errors skip the setlist; the rest of the library imports. Warnings import it. The report fields are those of section 11.2.

| Code | Level | Condition |
|---|---|---|
| SL001 | error | File is not valid UTF-8, or contains control characters other than LF, CR, tab |
| SL002 | error | `{title}` or `{meta: id}` missing, empty, duplicated, or `{title}` after the first entry |
| SL003 | error | `id` does not equal the file name, or duplicates another setlist in this library |
| SL004 | error | Malformed entry line (not `<id>/<id>`, not `? <title>`) or malformed directive |
| SL005 | error | No entries |
| SL101 | warning | Song entry names this library but the song id does not exist in it |
| SL102 | warning | File not in canonical form (as W001) |
| SL103 | warning | More than 200 entries |
| SL104 | warning | Title longer than 60 characters |
| I004 | info | Song entry names another library (resolved only when that library is installed) |
| I005 | info | Title-only entry |

### 13.7 Archive

A release archive (section 12) of a library at format 1.1 MAY include its valid setlists under `setlists/`, in canonical form, and then lists them in the manifest's `setlists` array (12.4) with path, id, title, song count, SHA-256 and size. Setlists that fail validation are left out and reported in the build like skipped songs. On loading, a listed setlist whose SHA-256 does not match is dropped with warning A104, the library is still imported. `archive_version` becomes `1.1` when the `setlists` field is present.

---

## 14. Examples

Both examples are original texts written for this specification and released under CC0, so they can be copied into a test library. They are translations of each other and share a variant group. The Dutch file is a plain phase 1 song. The English file additionally carries chords (phase 3) and line timing (phase 2); a phase 1 app shows it exactly like the Dutch one, with English words.

### 14.1 Dutch: `songs/nl/vuur-in-de-nacht.cho`

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

### 14.2 English: `songs/en/fire-in-the-night.cho`

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

## 15. Requirements traceability

| Requirement | Where satisfied |
|---|---|
| FR-LANG-05 | `{meta: language}` MUST, BCP 47 (6.1, 6.2) |
| FR-LANG-06 | `{meta: variant-group}` (6.1), example 13 |
| FR-LIB-10, FR-LIB-11 | Release archive as the download unit (12.1, 12.7); "what changed" from manifests (12.7) |
| FR-LIB-13 | Release index and conditional update check (12.6, 12.7) |
| FR-LIB-14 | Archive and index over plain HTTPS (12.6) |
| FR-LIB-15 | Atomic install of an archive (12.7 step 6) |
| FR-LIB-16 | Archive from a local file; repository snapshot (12.1, 12.5) |
| FR-LEAD-30, FR-LEAD-31 (withdrawn, FRD v0.9) | Download instruction with archive URL and revision (12.7), reserved, not used in phase 1 |
| FR-NET-04 | Archive size from the index before download (12.7) |
| NFR-SEC-01 (archives) | Container limits, hash verification, zip-slip and bomb guards (12.2, 12.8, 12.9) |
| FR-LANG-07 | `{sorttitle}` and article rule (6.1) |
| FR-LIB-01 | Library is a git repository (3) |
| FR-LIB-03 | Plain-text ChordPro profile (2, 5) |
| FR-LIB-04 | Song metadata table (6.1): id, title, language, lyrics MUST; the rest SHOULD/MAY |
| FR-LIB-05 | `zangbundel.json` fields (4.2) |
| FR-LIB-06 | Ignore rules (10.1), P2 and P3 extensions (10.2, 10.3) |
| FR-LEAD-02 | Setlist file, `setlists/` in repositories and archives, import, export and resolution (13) |
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

## 16. Open questions

| # | Question | Impact |
|---|---|---|
| Q12 | *Deferred 2026-10-28 to after 1.0, by the owner; HTTPS only until then, and the app treats library content as untrusted and bounded:* Should release archives be signed (a detached signature over the manifest, public key in the repository), so that a compromised host cannot serve altered songs? HTTPS alone protects the transport, not the host. | 12.9, NFR-SEC-01, release tooling |
| Q13 | *Resolved 2026-10-22:* release archives and snapshots only; the app has no git client. FR-LIB-17 is met by the manifest's `revision` and `source` (`design/architecture.md` A-11). | Resolved |
| Q14 | The release index convention (12.6) is written with GitHub Releases in mind. Which other hosts must the tooling support out of the box (GitLab, Codeberg, a plain web server)? | Release tooling |
| Q1 | Is JSON acceptable for `zangbundel.json`, or do maintainers want YAML for its comments and multi-line strings despite the coercion risk (section 4.1)? | Library file, parser dependency |
| Q2 | *Deferred 2026-10-28 to phase 2:* Timing is keyed by canonical line number (10.2). Inserting a lyric line shifts every later number, so timed songs need a renumbering tool or an inline alternative such as a timing directive immediately before each line. Which do maintainers prefer? | P2 format, maintainer tooling |
| Q3 | Variant groups link songs within one library only. Should a group id be allowed to span libraries (`<library id>/<group>`), given that translations often come from different maintainers? | FR-LANG-06, library governance (FRD Q3) |
| Q4 | Should the project define a fixed tag vocabulary (with the NL/EN labels in a shared file) so filters work across libraries, or is a per-library `tags` map enough? | FR-LIB-30 across libraries |
| Q5 | *Resolved 2026-10-20:* generated section labels follow the app (UI) language, as 7.1 says; a Dutch song in an English app shows "Chorus". A label written in the song file is shown as written (`design/guiDesign.md` D-77). | Resolved |
| Q6 | *Deferred 2026-10-28 to phase 2:* Per-syllable timing anchors syllables by code point offset (10.2). Is that robust enough for maintainers, or are explicit syllable markers in the lyrics (which P1 would have to strip) preferable? | P2 format |
| Q7 | Should the hash cover only the parsed content (metadata values plus canonical lines) instead of the whole file, so comment-only edits do not invalidate singers' cached copies? Whole-file hashing is simpler and matches the BLE document; the cost is one extra transfer after such an edit. | BLE section 5; section 8.2 here |
| Q8 | *Resolved 2026-10-20:* no. `name` and `description` stay plain strings in the maintainer's language and are shown as written; only tag labels are localisable (`design/guiDesign.md` D-77). | Resolved |
| Q9 | *Deferred 2026-10-28 to phase 3:* Chord-only lines are not canonical lines (9.1). Should a P3 musician view be able to follow the leader to an instrumental line, which would require numbering them? | Line list stability between P1 and P3 |
| Q10 | *Resolved 2026-10-26 (FRD Q3):* a separate repository, `github.com/pe1mew/Zangbundel-bibliotheek`; its maintainer checks licences on contributions with its validator and contribution rules. | FRD Q3, release planning |
| Q11 | *Resolved 2026-10-22:* a separate tool. `validate.py` and `release.py` live in the library repository `github.com/pe1mew/Zangbundel-bibliotheek`; the app has its own implementation of the same rules, and both run a shared conformance corpus (`design/architecture.md` A-21, A-22). | Resolved |
