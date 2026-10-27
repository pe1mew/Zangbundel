# Campfire statistics – 2026-10-27

guiDesign D-60 to D-62 as built in v0.49; assessment SP-16 (v0.15). On the A52 as leader, debug build, with the Huawei as singer in the second campfire. **Result: the opt-in screen, the record kept at the end of a campfire, the season view, the preview and the share sheet work; the database migrated from version 4 to 5 without loss. Two faults found and fixed on the way, and one condition of SP-16 that was not met.**

| Step | Result |
|---|---|
| Update from database version 4 to 5 | The app opened with the library and setlists intact; the new table is empty |
| *Statistieken van kampvuren* in settings | The explanation, *Wat nooit bewaard wordt*, the line on sharing and the switch at the bottom, off |
| Switch on; a campfire with two pushes, about 2 minutes, no singer | The drawer showed *Statistieken delen*; after *Afsluiten* the season showed *1 kampvuur, 2 liederen, gemiddeld 0 zangers* and the two songs under *Het meest gezongen* |
| The exported record | Date, two pushes with library and song identifiers, push times from the start and durations, the six setlist songs not sung, radio figures, versions; no name, gathering name, fingerprint or device address. **Fault:** `singers` was empty: the sample timer started at `Long.MIN_VALUE`, so `now - sampled` overflowed and no sample was ever due. Fixed: the first sample is taken at the start |
| The season row | **Fault:** date, songs and singers side by side wrapped *2 lieder/en* on the A52's width. The date now has its own line |
| After the fixes: a campfire with the Huawei joined, one push, about 2 minutes | Record `"singers":[[1222,0],[61595,1]],"maxSingers":1`, `repairsServed 3`, `peakConnections 1`; the season shows *2 kampvuren, 3 liederen, gemiddeld 1 zanger*, each campfire as date and *liederen · zangers · duur*, *Hoog laait het vuur · 2×* first |
| *Delen*, first build | The share sheet opened at once with `zangbundel-statistieken.json`. **Not as SP-16 requires:** the leader must see the content before the share sheet opens (P6) |
| *Delen*, after the change | A dialog *Dit deel je* shows the JSON array in full, scrollable; its *Delen* opens the share sheet with the file; no app was chosen |

**Covered by unit tests, not on the phone:** the exact JSON of a record, escaping of quotes and backslashes in identifiers, the season count of pushed songs (not skipped ones) and the export as an array (`CampfireRecordTest`). **Not tested:** *Wissen* and *Alle gegevens verwijderen* deleting the records; the drawer's *Statistieken delen* after the preview was added (it opened the share sheet directly before, and now calls the same dialog as the screen); a record after a resume.
