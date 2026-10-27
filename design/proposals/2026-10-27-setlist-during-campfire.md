# Proposal: changing the setlist during the campfire (guiDesign D-58)

| Field | Value |
|---|---|
| Proposal | The leader can add, remove and reorder upcoming songs during the campfire and push any song from the songbook; the setlist document and the state's `cpos` and `sung` follow, and singers stop reading positions across two setlists |
| Date | 2026-10-27 |
| Status | **Approved by the owner 2026-10-27 with the recommended answers to all four open points; built as BLE protocol v0.40, guiDesign v0.40 and architecture v0.14.** |
| Asked by | The owner, 2026-10-27: "Write a proposal for D-58 (adding songs and pushing songbook songs during a campfire, BLE 9.4 and 9.5), then build it once I approve." |
| Affects | `design/ble-protocol.md` 7.5, 9.4 (`cpos`, `sung`), 9.5 (setlist), 10.5; `design/guiDesign.md` D-55, D-58, D-65 (*Wis*); `design/architecture.md` 5.4 (the resume record); `core/protocol` `SingerEngine`, `LeaderSession`; `app/gathering/Gathering`, `ui/gathering/LeaderScreen` |
| Requirements | FR-LEAD-01 (MUST: push any song from the local library with one action), FR-LEAD-03 (MUST: during a gathering see the setlist, push the next song, skip a song, add songs), FR-LEAD-06 (MUST: clear the current song). No FRD change. |

## 1. What happens now

- **The leader can push only what the setlist held at the start.** The leader's page (D-55) is the setlist copied at *Starten*; *Nu* pushes one of its songs. A campfire started without a setlist has nothing to push. FR-LEAD-01 and FR-LEAD-03 are not met.
- **The protocol already allows a changed setlist, but nothing uses it.** Since v0.35 the setlist document (9.5, document 4) is the `dh` of each song ahead by position, and "changes only when the setlist itself changes". The state's `cpos` and `sung` (9.4 keys 15, 16) are setlist **positions**.
- **Positions are what breaks when the setlist changes.** On the leader, the songs pushed are kept as positions (`Gathering.pushedOrder`). On the singer, `SingerEngine` reads `sung` against the newest setlist it verified, and remembers the earlier songs it showed by position (`earlierDone`). Insert, remove or move a song, and every later position names a different song. A singer that has the new state but still the old setlist (the setlist arrives after the state's lead-in, 7.4) would read the new `sung` against the old order. It could then show a song as "sung earlier" (7.5, v0.35) that was not sung.
- ***Wis*** (D-65, FR-LEAD-06) is designed but not built.

## 2. Proposed change

### 2.1 Protocol (BLE v0.40)

1. **9.5, setlist:** "changes only when the setlist itself changes" stays. Changing it means a song added, removed or moved during the campfire (FR-LEAD-03). The document is unchanged in form: one `dh` per position.
2. **9.4, `cpos` and `sung`:** these name positions **in the setlist the same state's manifest names** (key 10, document 4). A state that changes the setlist renumbers both in the same signed body. The leader keeps the songs pushed by identity, not by position, and recomputes the bitmap for each state.
3. **7.5, singer (the safety rule):** a singer reads `cpos` and `sung` only against the setlist whose `dh` the applied state's manifest names. Until that setlist has arrived it shows no earlier songs and draws the setlist rows of the reception grid as unknown. It remembers the earlier songs it showed by `dh`, not by position, so a renumbering never repeats or skips one.
4. **10.5:** a setlist change without a push is a new state (`seq` + 1, the same `cur`). Its burst carries the state's lead-in and the setlist, not the song, which did not change (7.4 rule 4). The songs ahead (7.4 rule 7) follow the new setlist: added songs go out ahead from then on, removed ones stop.
5. **Clear (*Wis*, FR-LEAD-06):** a state without `cur` and `cpos`, with `sung` unchanged. The current song is no longer in the manifest. This is what 9.4 already says ("Absent = no current song"); it only gets built.

### 2.2 The leader (guiDesign D-58, D-65)

The ≡ button opens the leader's sheet with two tabs, as D-58 describes:

- **Setlist:** the current song and the sung songs are fixed, greyed, with no handle. Upcoming songs can be dragged to a new place, and swiped away with *Ongedaan maken* for a few seconds. At the bottom: *Toevoegen uit zangbundel* opens the second tab.
- **Zangbundel:** the songbook of D-52, with search, and per song **Toevoegen** and **Nu zingen**.
  - **Toevoegen** puts the song at the end of the setlist (open point 2).
  - **Nu zingen** puts the song in the setlist right after the current song (at the end when nothing is current) and pushes it in the same state (open point 1). The leader's page gets its separator there, as for every setlist song.
- ***Wis*** on the current song's separator bar (D-65): nothing is being sung; singers keep the text with *gewist* in their strip.

A campfire started without a setlist begins with an empty page and *Kies een lied uit de zangbundel* in place of the setlist block. Its setlist grows with every *Nu zingen*. The resume record (architecture 5.4) follows the setlist as it is now, so a resume brings back the changed one.

### 2.3 What it costs

- **A setlist change on the air:** about 6 source frames per pass for a 10-song setlist (93 bytes at 16 bytes a frame), three passes, after the state's lead-in (4.5 s on three sets, 7.4 rule 4). That takes about 5 to 6 s of burst on the legacy carrier, in which the current song is not repeated. Singers that hold the song lose nothing, and new singers get it a few seconds later.
- **A song pushed by *Nu zingen*:** no singer holds it ahead, so most fetch it over GATT, as for a song not held ahead today (7.4 rule 4; about 3 s later than a song held ahead on the phones). Every song added before it is sung goes out ahead, so this cost applies only to songs pushed straight from the songbook.
- **A singer between a new state and its setlist:** for a second or two it shows no earlier songs and blank setlist rows in the hidden reception grid. The current song is not affected.
- **Assessment:** no new finding. Nothing new is received or sent: the setlist document and the state are the signed documents they are today, and the rule in 2.1.3 makes the singer stricter about received bytes, not looser. Upcoming songs are still sent only as `dh` values and song documents ahead, never shown (D-47).

## 3. Not proposed

- **Pushing a songbook song without adding it to the setlist** (`cpos` absent, as the v0.35 text allows): the song would have no position, so no separator on the leader's page and no place in `sung`. A late joiner would never see it among the earlier songs. Rejected in favour of 2.2, unless the owner prefers it (open point 1).
- **Moving or removing sung songs:** the evening's history would change under the singers' feet, and the memory (FR-DATA-03) would no longer match what they saw.

## 4. Open points for the owner

1. ***Nu zingen*** from the songbook adds the song after the current one and pushes it (recommended), or pushes it without adding it to the setlist (section 3)?
2. **Toevoegen** puts a song at the end of the setlist (recommended; drag it to move it), or right after the current song?
3. **Sung songs and the current song** stay fixed in the setlist tab (recommended), or can they be moved and removed too?
4. ***Wis*** (FR-LEAD-06, D-65) built in this change (recommended, small), or later?
