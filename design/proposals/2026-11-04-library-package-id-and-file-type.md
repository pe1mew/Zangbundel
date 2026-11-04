# Proposal: song references to library packages, and opening `.zbl` from other apps (FDS O-09, O-10)

| Field | Value |
|---|---|
| Proposal | Use the library package's `libraryId` (a lower-case UUID) unchanged as the library id everywhere; reserve the UUID form for library packages; identify packages by `libraryId` instead of by URL; turn manifest sets into shipped setlists. Register Zangbundel for library files handed over by Share, and for opening a `.zbl` where the sending app makes that possible, with a new assessment finding SP-37 |
| Date | 2026-11-04 |
| Status | **Approved by the owner 2026-11-04, in full, with the answers to section 5 recorded in section 7.** Specified in FRD v0.24 (FR-LIB-05, FR-LIB-07, FR-LIB-17 amended; FR-LIB-08, FR-LIB-09, FR-LIB-18 added), song format v0.18, BLE protocol v0.50, GUI design v0.66, architecture v0.27 (A-24), assessment v0.28 (SP-37) and test plan v0.18; adopted in the library manager's FDS draft 0.4 (D-29 to D-33). Code not yet changed. |
| Asked by | The owner, 2026-11-04: a written proposal answering the two open items the library-manager FDS (draft 0.3) leaves to this repository, O-09 (how the app refers to songs of a library in the new format) and O-10 (whether the app registers `.zbl` as a file type). |
| Affects | `design/functional-requirements.md`; `design/song-format.md` 4.2, 4.3, 11.3, 12.2, 13.3, 13.5 and a new 12.11; `design/ble-protocol.md` section 5; `design/guiDesign.md` D-50; `design/architecture.md` (new A-24); `design/security-privacy-assessment.md` (SP-09, new SP-37); `design/test-plan.md` D-02, new D-09; `docs/RUNBOOK.md` Step 5; `android/app/src/main/AndroidManifest.xml`, `MainActivity`, `library/PickedFile.kt`; the FDS in `Zangbundel-bibliotheek-manager/design/FDS.md`; the validator in `Zangbundel-bibliotheek` |
| Requirements | FR-LIB-05, FR-LIB-07, FR-LIB-16, FR-LIB-17, FR-LIB-21, FR-LEAD-02, FR-BLE-12, FR-DATA-03, FRD constraint 7. New: FR-LIB-08, FR-LIB-09, FR-LIB-18 (FRD v0.24) |

## 1. Recommendations at a glance

| Question | Recommendation |
|---|---|
| O-09.1 UUID or short form | **The UUID as is**, as text, everywhere a library id goes. It already fits the id syntax and length of song-format 3.2, so no type, table, parser or wire format changes. A derived short form is rejected: it can collide, and it would make a second name for the same library. |
| O-09.2 Cost on the air | **It fits every stated limit, with room to spare.** Against the 64-character id the protocol is already sized for, a UUID is 28 bytes smaller. Against today's default library id it costs 15 bytes per reference. With the package's version as `rev` instead of a 40-character git revision, a typical state record is 25 bytes **smaller** than today's (section 2.3). |
| O-09.3 Clash rule | **A package is identified by its `libraryId`, never refused for its id.** The same id means the same library, handled by the FDS decision table (8.3). The UUID form is reserved for packages: a `zangbundel.json` id in that form is refused (L006), so an id can never belong to both formats. The URL rule of 4.2 stays for release archives and snapshots. M-73's title check compares with every installed library, both formats. |
| O-09.4 Setlists, memories, sets | **No new syntax.** `.zbs` entries and memories use `<libraryId>/<song id>` as now. Manifest sets become shipped setlists (song-format 13.5) of their library, in manifest order, with ids `set-1`, `set-2`, … |
| O-10.1 Intent filters | **Register SEND** for the vendor type, ZIP and `application/octet-stream`. This is the route that can work from every channel: *Share → Zangbundel*, with the format recognised by content (FDS APP-02). **Also register VIEW**, for the vendor type and for `content:` URIs whose path ends in `.zbl`. Whether tapping a file opens Zangbundel depends on what the sending app hands over; that is not documented, so it is not promised, and a new device test D-09 measures it per channel. |
| O-10.2 Attack surface | **No new permission.** Any app can now hand Zangbundel an untrusted URI. New finding **SP-37**, Low once its seven conditions are built: content URIs only, not the app's own provider, the same bounded read and parser as the file picker, never install without the user's tap, the display name sanitised, other extras ignored, join links dispatched strictly. |
| O-10.3 Documents | FRD v0.24 (FR-LIB-08, -09, -18), song format v0.18, BLE protocol v0.50 (text only, no wire change), GUI design v0.66 (D-50), architecture v0.27 (A-24), assessment v0.28, test plan v0.18, RUNBOOK Step 5, changelog. |

Section 5 lists five points where the FDS and the FRD disagree, or may need to, outside O-09 and O-10: licence, maintainer and minimum app version, strictness, revision, and converted libraries. The owner should decide those before FR-LIB-08 can be met.

---

## 2. O-09: referring to songs of a library package

### 2.1 Where a library id goes today

| Where | Form | Bound | Source |
|---|---|---|---|
| Setlist file entries | `<library id>/<song id>` | id syntax, ≤ 64 each | song-format 13.3 |
| BLE song reference `ref` | `{1: lib (tstr), 2: song (tstr), 3: hash (bstr 16), 4: rev (tstr, optional)}` | state body ≤ 440 bytes; sealed writes ≤ 512 | ble-protocol 5, 9.4, 9.5, 9.6, 9.8 |
| Resume record | setlist positions as `library-id/song-id` text | ≤ 200 characters per text (`ResumeRecord.TEXT_MAX_CHARS`) | ble-protocol 10.1 step 2, 10.9 |
| Memories, prepared setlists, songbook | `libraryId`, `songId` columns; key `libraryId/songId` | `LibraryId`: `isValidId`, 1–64 characters | `core/model/Ids.kt`, `platform/storage` |

A lower-case UUID such as `3f6c2a1e-8b4d-4c1a-9e2f-7d5b0a6c9e11` matches `^[a-z0-9]+(-[a-z0-9]+)*$`: five groups of hex digits, separated by single hyphens. It is 36 characters, under 64. `LibraryId("3f6c2a1e-…")` is valid as the code stands.

### 2.2 Question 1: the UUID as is

**Recommendation: use `libraryId` unchanged as the library id**, in every place in the table above.

- **Nothing has to change to carry it.** The id syntax, the 64-character bound, the Room schema, the `.zbs` parser, the CBOR `ref` and the resume record all accept it today.
- **A short form would be a second name for one library.** Every device would have to derive it the same way, and every place that meets both forms (a `.zbs` from a friend, a leader's `ref`, the FDS device memory keyed by `libraryId`) would have to map one onto the other.
- **A short form can collide, and the collision is silent.** Take the first 8 hex digits (32 bits). By the birthday bound, the chance that two libraries anywhere share a prefix reaches 1 % at about 9,300 libraries and 50 % at about 77,000. A collision would make the clash rule refuse an unrelated library, or resolve a setlist entry against the wrong one. A longer prefix makes this rarer but never impossible, and costs more bytes.
- **What a short form would save is small.** An 8-hex form saves 29 bytes per reference. The UUID as 16 raw bytes saves 21, but changes the type of `lib` on the wire and gives the app two kinds of library id (section 2.3, line 1).
- **"Means nothing to a person" matters less than it seems.** The app's screens show a library's title (guiDesign D-50), not its id. The id shows only in `.zbs` files. There the app writes the title as a comment on every entry (song-format 13.5, *Export*), and someone writing a setlist by hand can use a `? <title>` entry, which the app resolves by title.

**Validation.** For a package, the app checks `libraryId` against the FDS form exactly: `^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$`. Anything else (upper case, braces, another UUID version) refuses the import as MAN-02. Normalising instead of refusing would give two spellings of one library.

### 2.3 Question 2: what a UUID costs on the air

Computed by `2026-11-04-uuid-cost.py` beside this file, a CBOR encoder with preferred serialization (ble-protocol 9.1). It reproduces the 405-byte worst-case body that `StateTest` asserts and 9.4 quotes, which checks the encoder. The figures below are its output: `python -I design/proposals/2026-11-04-uuid-cost.py design/song-format.md`.

**1. One library id as CBOR, with its map key:**

| Form | Bytes |
|---|---|
| Today's default library, `zangbundel-bibliotheek` (22 characters) | 24 |
| UUID as text (36 characters) | 39 |
| The longest id allowed (64 characters), which the limits are sized for | 67 |
| 8-hex short form (rejected) | 10 |
| UUID as a 16-byte byte string (rejected) | 18 |

**2. Worst-case state body** (9.4; title, `rev` and `pt` left out, as the leader does when needed; 64-character song id; every other field at its maximum):

| Library id | Body (limit 440) | Signed record (limit 512) | Legacy frames (limit 30) |
|---|---|---|---|
| 64 characters (today's bound) | 405 | 475 | 30 |
| UUID | 377 | 447 | 28 |

The protocol's limits were set for a 64-character id. A UUID is 28 bytes shorter, so every worst case in 9.4, 8.4 and 9.8 still holds, with more room.

**3. A representative state record.** These are assumptions, not measurements:

- song id `vuur-in-de-nacht` with its title;
- `ts` at 90 minutes; 25 singers;
- a song and a setlist in the manifest;
- `cpos`, `sung` and `pt` present; no delegates (phase 2).

| `cur` carries | Body | Record | Legacy `k` | Extended `k` |
|---|---|---|---|---|
| Today: `zangbundel-bibliotheek` + 40-hex git revision | 228 | 297 | 19 | 2 |
| UUID + 40-hex revision (the id alone) | 243 | 312 | 20 | 2 |
| **UUID + the package's version, `"4"`** (proposed) | **203** | **272** | **17** | 2 |
| UUID, no `rev` | 200 | 269 | 17 | 2 |

- **The id alone costs 15 bytes.** In this example that is one more legacy source frame: with `r = 2k`, three more frames per pass. A burst's state lead-in grows by 9 frames, 3 advertising events on three sets, 0.3 s.
- **A package has no git revision.** Its `rev` is the decimal `version` (section 2.4), which is 40 bytes shorter than a 40-character revision. A package's state is therefore 2 legacy frames *smaller* than today's.
- **The extended carrier is unaffected:** 2 frames in every row.

**4. Sealed writes, worst case** (64-character song id, 64-character `rev`):

| Library id | Request op 1 (9.6) | Control op 1 and 4 (9.8) | Limit |
|---|---|---|---|
| 64 characters | 304 | 304 | 512 |
| UUID | 276 | 276 | 512 |

Control op 8 carries no `ref`, so its 417-byte `data` bound (8.4) is untouched.

**5. Song document 3 (and 9)**, measured with the example song of song-format 14.1 (1,082 bytes), its licence summary `CC0-1.0`, and raw DEFLATE at level 9, the level `Deflate.kt` uses:

| `ref` carries | Raw | Deflated | Legacy `k` | Extended `k` |
|---|---|---|---|---|
| Today: `zangbundel-bibliotheek` + 40-hex revision | 1,201 | 585 | 37 | 3 |
| UUID + version `"4"` | 1,176 | 601 | 38 | 3 |
| UUID + 40-hex revision | 1,216 | 607 | 38 | 3 |

- Hex digits compress worse than a word, so after DEFLATE a UUID costs 16 to 22 bytes. That is one legacy source frame for this song: 3 frames per pass, out of 111. The extended carrier is unchanged.
- The bounds are untouched: 64 kB framed, 67,584 bytes after inflating (7.3).

**6. Device storage.** The worst-case `library-id/song-id` text drops from 129 to 101 characters, under the resume record's 200. Memories and prepared setlists store the two ids in separate columns with no length bound beyond `isValidId`.

**Not affected:**

- The setlist document carries only `dh` per position since v0.35 (9.5), so no library id.
- Info `libs` and the `download` document are reserved and not sent in phase 1.
- Document 8 (requests, phase 2) carries one `ref` per request, GATT only, within its 64 kB.

### 2.4 Question 3: the clash rule without a URL

Song-format 4.2 refuses a library whose `id` equals that of an installed library from a different repository URL. A package has no URL; the FDS identifies it by `libraryId` (D-18, 8.3). Proposed rules:

1. **Package against package: never refused for its id.** An incoming package whose `libraryId` matches an installed package, or one in device memory (FDS APP-10 to APP-12), is the same library. The FDS decision table decides: key, then version, with M-70 to M-72 and M-74, at most one question (APP-05). This replaces the URL rule for packages.
2. **The UUID form is reserved for packages, so the formats cannot meet on an id.**
   - A `zangbundel.json` whose `id` matches `^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$` is refused with L006 ("invalid"), with a message naming the reason.
   - A package's id always has that form (2.2).
   - The library repository's validator gets the same rule.
   - Whether any existing library uses a UUID-shaped id cannot be checked from here. The default library's id is `zangbundel-bibliotheek`.
3. **Release archives and snapshots keep the URL rule** of 4.2 unchanged, among themselves.
4. **The title check (M-73) looks at every installed library.** That means packages by `title`, and release archives and snapshots by `zangbundel.json` `name`, compared case-insensitively.
   - *Add separately* installs next to it.
   - *Replace* removes the installed library first, whatever its format.
   - Device-owned setlist entries that named the removed library then show as *niet gevonden* (song-format 13.5) until a library with that id returns. The proposal does not re-point them automatically (section 5, point 5).
5. **What is recorded per package** (FR-LIB-17's role):
   - `libraryId`, `version` and `modified`;
   - the signing status and fingerprint (FDS APP-20, APP-21);
   - the source file name and the file's SHA-256, as for an archive (song-format 12.7 step 7).

   The installed "revision" shown and sent as `rev` is the decimal `version`. An archive's `rev` stays its git revision; `rev` is informational in both cases, since matching is by content hash (FR-BLE-12).
6. **Device memory** (key or "unsigned", and version, per `libraryId`) is library metadata, not personal data. It lives in the app database, which backup and device transfer already exclude (architecture A-13, assessment SP-05).

### 2.5 Question 4: setlists, memories and manifest sets

**`.zbs` and memories: unchanged syntax.**

```
{title: Kampvuur}
{meta: id kampvuur}

3f6c2a1e-8b4d-4c1a-9e2f-7d5b0a6c9e11/vader-jacob   # Vader Jacob
zangbundel-bibliotheek/advocaatje                   # Advocaatje
? Kumbaya
```

- The entry rule of 13.3 (`<id>/<id>`) already accepts the UUID, so `SetlistParser` and SL004 need no change. Song-format 13.3 gets this example and one sentence saying a package's library id is its `libraryId`.
- Memories store library id, song id and title as now (FR-DATA-03). The stored title keeps a memory readable after the library is removed.

**Manifest sets become shipped setlists** (FDS APP-23, song-format 13.5, FR-LEAD-02):

| Shipped setlist field | From the package |
|---|---|
| owning library | `libraryId` |
| setlist id | `set-<n>`, `n` its 1-based position in `sets` of the installed version. It is used only to address the setlist on the device, never on the air or in a file. A copy made with *Dupliceren* gets its own id and does not follow later versions (13.5). |
| title | `sets[].name`. The setlist screens truncate a name over 60 characters as they truncate a long `.zbs` title (SL104 is a warning). |
| entries | `<libraryId>/<song id>` for each of `sets[].songs`, in order. SET-01 guarantees every song exists, so a shipped set never has unresolved entries. |
| order on the *Setlists* screen | manifest order (APP-23), under the library's name and colour |
| file name | none; the shipped-setlist table records `manifest.json` |

- An update replaces the sets with the new version's, as for archive setlists.
- A set of more than 200 songs gets warning SL103 in the import report, as a `.zbs` would. The FDS has no such bound (section 4).
- Exporting a shipped set writes an ordinary `.zbs` with UUID entries and title comments.

**A library converted by the Editor is a different library.** EDT-26 gives a converted release archive a new `libraryId`, so `.zbs` entries naming `zangbundel-bibliotheek/…` do not resolve against the converted copy.

- At the fire this does not matter: leader and singer match by content hash (FR-BLE-12).
- Package files may be stored with a BOM or CRLF (FDS SNG-01), so the app must canonicalise them before computing the content hash, as for a snapshot (song-format 8.3). Then the converted song and the original have the same hash.
- What stays is the leader's setlists naming the old id. The recommendation is to accept this, since the stored setlist shows those entries as *niet gevonden* with the reason. Section 5, point 5, gives the FDS option that would remove it.

---

## 3. O-10: opening a `.zbl` from other apps

### 3.1 What Android matches, and why a tap is not guaranteed

From Android's `<data>` element reference (developer.android.com, read 2026-11-04):

- **No system-wide extension.** Android's MIME table does not know `.zbl`, and an app cannot add an extension to it. A sending app that derives the type from the file name therefore labels a `.zbl` `application/octet-stream`, or leaves the type out.
- **A typed intent needs a typed filter.** A filter that lists only a MIME type is assumed to accept `content:` and `file:` URIs. An intent that carries a type matches only a filter that lists that type.
- **Path attributes need a scheme and a host.**
  - `pathSuffix` exists only from API 31, and the app supports 26 to 30 too.
  - `pathPattern`'s `.*` is lazy and does not backtrack: `.*\\.zbl` stops at the first dot in the path. A path such as `…/troop.v2.zbl` needs extra patterns (`.*\\..*\\.zbl`, …).
- **The file name may not be in the URI.** Whether a `content:` URI's path holds the file name at all depends on the provider of the app that sends it. It is not documented, and it differs between apps.

So the only route that does not depend on the sending app's provider is **Share**: the user picks *Share → Zangbundel*, and the app recognises the format by content (FDS APP-02, song-format 12.2), whatever type the sender put on it.

### 3.2 Proposed filters

All three are added to the existing `MainActivity` (exported, `singleTask`):

```xml
<!-- A library handed over with Share, from any app (FR-LIB-18). The format is decided by content (APP-02). -->
<intent-filter>
    <action android:name="android.intent.action.SEND" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:mimeType="application/vnd.zangbundel.library+zip" />
    <data android:mimeType="application/zip" />
    <data android:mimeType="application/x-zip-compressed" />
    <data android:mimeType="application/octet-stream" />
</intent-filter>

<!-- Opened with the library type: Zangbundel's own export, or a host that serves the type. -->
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:scheme="content" />
    <data android:mimeType="application/vnd.zangbundel.library+zip" />
</intent-filter>

<!-- Opened by name: a content URI whose path ends in .zbl, whatever generic type it carries. -->
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:scheme="content" />
    <data android:host="*" />
    <data android:mimeType="application/octet-stream" />
    <data android:mimeType="application/zip" />
    <data android:mimeType="application/x-zip-compressed" />
    <data android:pathSuffix=".zbl" />             <!-- API 31 and later -->
    <data android:pathPattern=".*\\.zbl" />        <!-- API 26 to 30: no dot before the extension -->
    <data android:pathPattern=".*\\..*\\.zbl" />   <!-- one dot before it -->
    <data android:pathPattern=".*\\..*\\..*\\.zbl" /> <!-- two dots before it -->
</intent-filter>
```

- **Not registered:**
  - VIEW for every ZIP or every `octet-stream` file. Zangbundel would offer to open every unknown file on the phone.
  - The `file:` scheme (section 3.4).
  - `SEND_MULTIPLE`.
- **The cost of the SEND filter.** Zangbundel appears in the share sheet whenever the user shares a ZIP or a file of unknown type. Leaving out `application/octet-stream` would avoid that, but would also miss most `.zbl` files coming from a messaging app (3.1). **Decision for the owner: point 1 of section 6.**
- **When Zangbundel shares a library itself** (song-format 12.7 *Export*, a SHOULD not yet built):
  - it sets the vendor type;
  - it names the file `<title or id>-<version>.zbl` under `cacheDir/share/`, so its own FileProvider URI ends in `.zbl`.

  Whether the receiving messenger keeps either is not documented.

### 3.3 What works per channel

**Nothing in this table is measured.** "Expected" follows from 3.1; D-09 (section 3.5) measures every row on two phones before any document or listing claims that tapping opens Zangbundel.

| Channel | *Share → Zangbundel* | Tapping the file | Works today, without this proposal |
|---|---|---|---|
| WhatsApp, Signal, Telegram | Expected, where the app offers Share on a received document | Hoped: needs the vendor type or a URI path ending in `.zbl`; differs per app | Save the file, then *Bibliotheek toevoegen → uit een bestand* |
| Discord | Expected if the download can be shared from the app or the file manager; a link to the file only opens a browser | Hoped | The same, after downloading |
| E-mail (Gmail, Thunderbird for Android, others) | Expected where the attachment can be shared or saved, then shared | Hoped: the sender's mail client usually labels it `octet-stream` | The same, after saving |
| Website download (Chrome, Firefox) | Expected from the downloads list or the file manager | Hoped: GitHub serves a `.zbl` release asset as `application/octet-stream` (checked 2026-11-04 on the default library's 2026.11.0 asset), and the downloads provider's URI may not hold the name | The same |
| File manager (Files by Google, Samsung My Files) | Expected | Hoped: depends on whether the manager's URI holds the name | The same |
| Quick Share or Bluetooth from another phone | Arrives as a file in Downloads: as the file manager row | Hoped | The same |

The file picker route (FR-LIB-16, D-50) stays, and stays the documented route until D-09 shows otherwise.

### 3.4 Attack surface: new finding SP-37

**SP-37 — Library files arrive by intent from any app** (FR-LIB-18, architecture A-24).

- **The change.** Any app on the phone can start Zangbundel with VIEW or SEND and a URI of its choosing, without the user's involvement, as often as it likes.
- **What arrives is untrusted.** The bytes, the display name and the type are all untrusted, as with the file picker. The picker only delivers what the user chose.
- **Principles:** S1, S2, S4, S5, P7.
- **Severity: Low**, once the seven conditions below are built. Medium before.

**Conditions of the resolution:**

1. **Content URIs only.**
   - VIEW's data, or SEND's `EXTRA_STREAM` taken as exactly one `Uri`; `ClipData` beyond it is ignored.
   - Any other scheme (`file:`, `http:`, `android.resource:`) is refused.
   - A URI whose authority is the app's own (`${applicationId}.files`) is refused, so another app cannot make Zangbundel read its own share cache on its behalf.
2. **The temporary grant only.** Bytes are read through `ContentResolver.openInputStream` with the permission the intent carries. The app never calls `takePersistableUriPermission` and adds no storage permission. RUNBOOK Step 5 and check G-01 are unchanged.
3. **The same bounded read and parser as the file picker.**
   - `readPicked` at 64 MiB + 1 byte, then the archive or package checks before any entry is inflated (song-format 12.7 step 1, FDS 3.5).
   - The package's `manifest.json` and `manifest.sig` parsers are new code on hostile input. They get fuzz targets under SP-09 and NFR-SEC-04 before they ship.
4. **Never installed without the user.**
   - The intent opens the library screen's import flow at its first step: report, licence, and the FDS question if one is due.
   - Nothing is written to library storage until the user taps the final button, as for a picked file (D-50).
   - A new intent replaces a pending one rather than queueing, so a flood of intents shows at most one import.
   - A running gathering is not interrupted: its foreground service keeps running, and the import screen opens over the app.
5. **The display name is shown, not trusted.** `OpenableColumns.DISPLAY_NAME` is cut to 100 characters, with control characters and bidirectional overrides removed, before it is shown or recorded as the source file name (song-format 12.7 step 7).
6. **Other extras are ignored:** `EXTRA_TEXT`, `EXTRA_SUBJECT`, `EXTRA_HTML_TEXT` and any URL in them. A shared text never makes the app fetch anything (FR-NET-03).
7. **Join links are dispatched strictly.** Today `MainActivity.handle` passes the data of *any* VIEW intent to `JoinLink.parse`, so a `content:` URI would show "invalid join link". The handler must dispatch by action and scheme:
   - `https` on `pe1mew.github.io` under `/Zangbundel/j`, or `zangbundel://j/…`, goes to the join;
   - `content:` goes to the import;
   - anything else is ignored.

**Privacy.** The file name may hold a person's name ("Remko's library.zbl"). It stays on the device, as it does for a picked file today. Nothing reaches the network or the developer, so the privacy policy and the Data safety form are unchanged.

### 3.5 Tests

- **D-02** (amended): "…then from a `.zbl` file, **and the same file through *Share → Zangbundel* from the file manager**". Pass: identical import report.
- **D-09** (new), *Opening a library from other apps*.
  - **Setup:** a release archive and a package `.zbl` (the second with a dot in its name), each sent through WhatsApp, Signal, Telegram, Discord, Gmail, a GitHub release download and Quick Share, to two phones (API 30 or lower, and API 31 or higher).
  - **Record per channel:** whether *Share* offers Zangbundel; whether tapping opens it; the intent's type and URI authority (debug log, nothing else); the import result.
  - **Pass:** *Share* imports from every channel that offers Share, and every channel's result is recorded in `design/test-results/`.
- **Unit tests** for the dispatch and URI checks of SP-37 (conditions 1, 5, 6, 7), and fuzz targets for the manifest parsers (condition 3).

---

## 4. Documents and requirements that change

### 4.1 This repository

| Document | New version | Change |
|---|---|---|
| `design/functional-requirements.md` | 0.24 | **FR-LIB-08** (MUST, P1): the app imports a library in the library package format of the library manager's FDS, telling the formats apart by content, besides release archives and snapshots. **FR-LIB-09** (MUST, P1): a package's `libraryId` is its library id wherever a library id is used; the UUID form is reserved for packages. **FR-LIB-18** (SHOULD, P1): the app accepts a library file handed to it by another app (Share, and opening a file where the sending app allows), and installs nothing without the user's confirmation. Change-history row; Q-section unchanged. FR-LIB-16 and constraint 7 unchanged. |
| `design/song-format.md` | 0.18 | 4.2: the URL rule applies to `zangbundel.json` libraries; the UUID form is reserved. 4.3 and 11.3: L006 "invalid" includes the reserved form. 12.2: the extension and MIME type are shared with packages; the format is decided by content. **New 12.11, *Library packages***: detection order (FDS APP-02), library id, `rev` = decimal `version`, content hash over the canonical form, the clash rules of 2.4, what is recorded. 13.3: package example. 13.5: manifest sets as shipped setlists (2.5). *Depends on* FRD v0.24, protocol v0.50. |
| `design/ble-protocol.md` | 0.50 | Section 5, *Song reference*: `lib` may be a package's UUID (36 characters); for a package, `rev` is its decimal `version`. One sentence in 9.4 that the 405-byte figure assumes a 64-character id, which a UUID stays below. **No wire change**; protocol 1.0 unchanged. *Depends on* FRD v0.24. |
| `design/guiDesign.md` | 0.66 | D-50 amended: a library can also arrive from another app and opens the same import flow at its first step (SP-37, condition 4). The package's display and questions (FDS APP-20 to APP-24, M-70 to M-74) need their own D-nn decisions once FR-LIB-08 is approved. *Depends on* FRD v0.24. |
| `design/architecture.md` | 0.27 | **A-24** (supersedes nothing; extends A-11): libraries also arrive as library packages, and by intent through `MainActivity`; the package parser sits in `core/songformat` beside the archive reader. |
| `design/security-privacy-assessment.md` | 0.28 | **SP-37** as in 3.4. SP-09 extended with the manifest parsers' fuzz targets. Version row; section 6 outcome line. |
| `design/test-plan.md` | 0.18 | D-02 amended, D-09 added (3.5); traceability rows for FR-LIB-08, -09, -18. |
| `docs/RUNBOOK.md` | Step 5 | One row: intent filters for library files need no permission and no Play declaration. |
| `changelog.md` | — | A line under [Unreleased]. |
| Code, after approval | — | `AndroidManifest.xml` (3.2); `MainActivity` dispatch; `PickedFile.kt` name sanitising; the package reader. |

`scripts/check-design.sh` must pass with the three new FR-LIB IDs defined before any document cites them.

### 4.2 The library manager's FDS, and the library repository

| Where | Change |
|---|---|
| FDS 4.1, `libraryId` | Give the exact form the app checks, `^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$`, and say that the app refuses anything else (MAN-02). |
| FDS D-25, 3.1 | Name the MIME type `application/vnd.zangbundel.library+zip` for a saved library, so the Editor sends it when it hands over a download. |
| FDS 8.3, M-73 | "An installed library" includes release archives and snapshots, compared by their `zangbundel.json` `name`. |
| FDS 4.1 `sets[].name`, 6.7 | Optional, for a consistent display: a name of at most 60 characters, as a setlist title (song-format 13.2), and a warning above 200 songs in a set (song-format SL103). |
| FDS 11, 12 | O-09 and O-10 resolved with a reference to this proposal once approved. |
| `Zangbundel-bibliotheek` validator | L006 refuses a UUID-shaped `id` (2.4, rule 2). |

---

## 5. Found while reading: the FDS and the FRD disagree

These are outside O-09 and O-10 but block FR-LIB-08. Each needs the owner's decision, as either an FDS change or an FRD amendment.

1. **Licence (FR-LIB-05, FR-LIB-21, MUST).**
   - The FRD requires the collection's licence terms in library metadata, shown at first import and on request.
   - The manifest has no licence field. Songs may carry `{meta: license}`, but only as a warning (SNG-16).
   - *Suggested FDS change:* a required `license` object in the manifest, `{id, summary}`, and an optional licence file listed in `files`, as `zangbundel.json` has.
2. **Maintainer and minimum app version (FR-LIB-05, MUST).**
   - The manifest has no maintainer contact; `author` is present only when signed.
   - It also has no minimum app version; `formatVersion` covers part of that.
3. **Strictness (FR-LIB-07, MUST).**
   - FR-LIB-07 asks the app to report bad songs without refusing the rest of the library.
   - FDS APP-01 refuses a package on any Error.
   - There is a precedent: a release archive is refused as a whole on A011 and A012, because an error there means tampering or a broken builder.
   - *Suggested FRD amendment:* FR-LIB-07 applies to repositories and snapshots; a package or release archive whose integrity rules fail is refused as a whole.
4. **Revision (FR-LIB-17, MUST).**
   - FR-LIB-17 names repository URL, branch or tag, and commit hash.
   - A package has `libraryId` and `version`.
   - *Suggested FRD amendment:* "…or, for a library package, its library id and version" (2.4, rule 5).
5. **Converted libraries lose their setlist references (EDT-26).** *Optional FDS change:* an optional manifest field naming the library id it was converted from. The app would then resolve `.zbs` entries that name the old id against the converted package. Not recommended now (2.5), but the owner may prefer it before libraries are converted in numbers.

---

## 6. Points for the owner

1. **SEND with `application/octet-stream`** (3.2): recommended. Without it most library files from messaging apps cannot be shared to Zangbundel, at the cost of Zangbundel appearing in the share sheet for other unknown files.
2. **The UUID form reserved for packages** (2.4, rule 2): recommended. It changes L006 in this repository and the library repository's validator.
3. **Manifest sets as read-only shipped setlists with `set-<n>` ids** (2.5): recommended.
4. **Section 5:** decide each of the five points before FR-LIB-08 is written in.
5. **Order of work after approval:**
   - first the FRD;
   - then song format, protocol, GUI, architecture, assessment, test plan;
   - then the FDS change request in the manager repository;
   - code last, once D-09's channels have been tried with the filters built.

---

## 7. The owner's decision (2026-11-04)

The proposal is approved in full: points 1 to 3 of section 6 as recommended, and the FDS changes of section 4.2, which the library manager's FDS adopted in draft 0.4 (exact `libraryId` form, PF-04 and EDT-59 for the MIME type, the same-title check over every format, set names of at most 60 characters and SET-05). O-09 is resolved there as D-29, O-10 as D-30.

The answers to section 5:

| Point | Decision | Where |
|---|---|---|
| 1. Licence | The manifest has a required `license` object: `id` (an SPDX identifier, `proprietary` or `mixed`, as in `zangbundel.json`), `summary` (1–200 characters) and an optional `text` with the full licence (up to 32,000 characters). There is no licence file. The app shows it on first import and on request (APP-25); the Editor does not save without one (MAN-07). FR-LIB-21 is met unchanged. | FDS D-31 |
| 2. Maintainer and minimum app version | `maintainer` is optional, with `name` and at least one of `email` or `url`, shown by the app when present (APP-26). There is no minimum-app-version field: `formatVersion` covers it (MAN-03). FR-LIB-05 is amended for the package format. | FDS D-32; FRD v0.24 |
| 3. Strictness | A library package is accepted whole or not at all (APP-01). FR-LIB-07 is amended: reporting bad songs and importing the rest applies to release archives and snapshots. | FDS D-33; FRD v0.24 |
| 4. Revision | As suggested: FR-LIB-17 records a package's library id and version. Approved with the proposal. | FRD v0.24 |
| 5. Converted libraries | No field for the old library id. FDS limitation 8 says that setlists and memories naming the old library do not find its songs in the converted one. | FDS limitation 8 |
