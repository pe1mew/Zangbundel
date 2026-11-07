# Decision: tag labels in library packages

| Field | Value |
|---|---|
| Proposal | Library packages carry tag labels in a new optional manifest field `tags`, so tags and their labels work the same in every library format. The 1.1 view does not change. |
| Date | 2026-11-07 |
| Status | **Decided by the owner, 2026-11-07.** Written into song format v0.26 (12.11). The library manager's FDS, the app and the test libraries follow. |
| Written by | The library session, which the owner made responsible for the format on 2026-11-07. |
| Affects | Song format 4.2 and 12.11; the library manager's FDS 4.1 (manifest), MAN-02, EDT-26, the Editor, and its test libraries; the app's package import and conformance vectors; the library repository's `scripts/test_package.py` |

## 1. The gap

Songs carry tags with `{meta: tag <tag>}` (song format 6.1). The app filters the combined songbook by tag [FR-LIB-30] (`design/guiDesign.md` D-52). The filter chips show a tag's localised label, such as *Kampvuur* in Dutch or *Campfire* in English. The labels come from the optional `tags` map in `zangbundel.json` (song format 4.2). A tag without a label is shown as its id, `kampvuur`.

A library package has nowhere to put labels:
- The manifest (FDS 4.1) has no field for them, and the app ignores unknown manifest fields.
- The 1.1 view may not carry them. PF-41 fixes the fields of its `zangbundel.json`, and the app refuses any other field with VIEW-02.
- The library manager's converter (EDT-26) and the library repository's `scripts/test_package.py` both drop the labels without saying so.

So the same library shows *Kampvuur* / *Campfire* as a release archive, and `kampvuur` as a package. The library repository has 9 tags, all labelled in Dutch and English, used 416 times in 247 songs.

## 2. The owner's decision

On 2026-11-07 the owner first sunset tags, then retracted that: *"the only issue is that manager does not support Labels. So, I decide manager shall support labels to stay consistent over all."* Asked where a package carries the labels, the owner chose **the manifest only**.

## 3. The format

A new optional manifest field, FDS 4.1:

| Field | Type | Required | Rules |
|---|---|---|---|
| `tags` | object | no | Localised labels for tags used in the songs: `{ "<tag>": { "<lang>": "<label>", … } }`, the shape of `tags` in `zangbundel.json` (song format 4.2). At most 200 tags. |
| `tags.<tag>` | object | — | The key is a tag in tag syntax, the syntax of a song id (song format 3.2). The value has 1 to 20 entries. |
| `tags.<tag>.<lang>` | string | — | The key is a language code as in `{meta: language}` (song format 6.1). The label is 1 to 60 characters, one line, NFC, with no control characters. |

- A violation is MAN-02, like any other manifest field. A manifest is written by the Editor, so it is held to its rules; a `zangbundel.json` is written by hand and only warned about (L002, S012).
- A label for a tag that no song uses is allowed. The Editor may report it as Info.
- A tag without a label is shown as its id, as in `zangbundel.json`.
- Key order carries no meaning.
- The 1.1 view does not change. PF-41 still lists no `tags`, and VIEW-02 still refuses any other field. An app that reads only format 1.1 sees the package's tags as bare ids, as it does today.
- `formatVersion` stays 1. The field is optional, and the app ignores unknown manifest fields, so an app from before this change reads a package with labels and shows bare tags.

Not chosen: labels in the view as well. A pre-package app would show them, but every app that already reads packages would refuse each package with labels (VIEW-02) until it is updated, and the library manager's engine would too.

## 4. What follows

| Where | Change | Owner |
|---|---|---|
| Song format | v0.26: 12.11 row *Tag labels*; 4.2 points to it. | Library session (done with this record) |
| FDS | 4.1 row `tags` as in section 3; MAN-02 covers it. The Editor lets the author edit labels. EDT-26 carries `tags` over from `zangbundel.json`, leaving out entries that break section 3, and names them. Test libraries: a valid case with labels and a MAN-02 case. | Library manager session |
| App | 12.11: the manifest's `tags` takes the place of `tags` in `zangbundel.json`. Conformance vectors for both cases, from the manager's test libraries. | App session |
| Library repository | `scripts/test_package.py` writes the release's labels into the manifest once the manager's tools do. `conformance.py` covers the new package cases through the corpus pin. | Library session |
