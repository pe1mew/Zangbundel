# The field-test log and its consent – 2026-10-27

Architecture A-20 as built in v0.18; test plan 5.1 and 5.5; assessment SP-22. The `fieldtest` build, signed with the debug key (no keystore on this machine), on the A52 and the S23. **Result: the consent screen comes first, and the log holds what A-20 lists and nothing else, for a singer and for a leader. The leader's half could be tested only after the library's release index was fixed (below).**

| Step | Result |
|---|---|
| First start of the `fieldtest` app | *Toestemming testversie Zangbundel* with the text of test plan 5.5 and *Ik doe mee* / *Niet meedoen*, before the welcome screen; the owner consented on both phones |
| The default library in the fieldtest app (S23) | **Refused:** *Deze bibliotheek vraagt een nieuwere versie van de app* (`zangbundel.json · L005 · 1.0.0, this app is 0.1.0`). Not an app fault: the library's `releases.json` lists 2026.10.1 (min app 1.0.0) and 2026.10.0, not 2026.10.2 (min app 0.1.0, published on GitHub on 26 October), so a fresh install takes 2026.10.1. The debug apps had 2026.10.2 from earlier. Needs the index updated in `Zangbundel-bibliotheek` |
| Singer session: the A52 (fieldtest) in a campfire led by the S23 (debug), about 3.5 minutes, one push | Six lines: `start` (role singer, date, Android 14, SDK 34, `caps` peripheral, multiple sets, extended, max data 1650, app 0.1.0, protocol 1.0), four `sample` lines at 0, 60, 120 and 180 s (battery 100, charging, repairs 0, failed 0, carrier, state), `end` at 216 s with state `ended` |
| What is not in it | No name, campfire name, fingerprint, song, key, token or address |
| *Log delen* | The file in full in *Dit deel je*; its *Log delen* opened the share sheet; nothing chosen |
| The index fixed | The owner pushed `releases.json` with 2026.10.2 (from the published archive, its SHA-256 matching GitHub's digest, `release.py verify` 0 warnings); the A52's fieldtest app then installed the library |
| Leader session: the A52 (fieldtest) leads without a setlist, the Huawei (debug) joins, *Advocaatje* pushed from the songbook; the S23 (debug) joined by hand after 3 minutes; ended after about 4 minutes | `start` (role leader, date, Android 14, SDK 34, the same `caps`, versions, `resumed` false, 3 legacy sets, extended set on, 6 connections at most); `sample` at 0, 60, 120, 180 and 240 s (battery 100, charging, singers 0 → 1 → 2, frames sent 4 → 8 695, repairs served 0 → 4, bytes repaired 0 → 376, peak connections 1, refused 0); `end` at 290 s, written when the session closed after the 60 s of 10.10 |
| Unit test (`FieldLogTest`, debug) | One JSON object per line, nested capability maps, strings escaped so they cannot leave their quotes or the line |

**Found on the way, built the same day:** the reception grid (D-81) seemed not to open on the singer: in a campfire with no setlist and no push yet it has no rows, so the long press showed nothing. At the owner's request it now opens with empty bars for *Lied* and *Lijst* (guiDesign v0.54), checked on the Huawei.

**Not tested:** a resumed leader; *Log wissen*; *Niet meedoen*; a log over hours (F-04, F-05). Note for F-04: the leader's `end` line comes 60 s after *Afsluiten*, when the ending broadcast stops.
