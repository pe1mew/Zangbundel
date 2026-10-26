# Proposal: broadcast the setlist's songs ahead, *Nu* only switches

| Field | Value |
|---|---|
| Proposal | The leader broadcasts every song of the setlist continuously from the start of the gathering; singers keep what they receive, hidden, and show a song the moment the leader pushes it |
| Date | 2026-10-26 |
| Status | **Approved by the owner 2026-10-26 with the proposed answers to all six open points, and moved into FRD v0.16 (FR-BLE-18, FR-SING-05), BLE protocol v0.32 (7.3–7.7, 9.5, 13, 16), guiDesign v0.28 (D-47, D-81) and assessment v0.10 (SP-26).** One simplification while specifying: the setlist lists the songs' `dh` by position only, not their references, because the manifest at the push already verifies the song (protocol v0.32 history row). This file stays as the record. |
| Asked by | The owner, 2026-10-26: the leader walks around before and during the campfire, so continuous broadcasting gives better coverage and distant singers a better chance; at the start the leader only cues the song centrally so everybody has the same song |
| Affects | `design/functional-requirements.md` (new FR-BLE-18, FRD v0.16); `design/ble-protocol.md` 7.3, 7.4, 7.5, 7.7, 9.5, 13, 16; `design/guiDesign.md` D-47 (a note), D-81 (the island becomes the completeness grid and shows itself when needed); `design/security-privacy-assessment.md` (new SP-26); `design/test-plan.md` S-nn and F-02; `core/protocol` carousel and `SingerEngine`, `app/gathering/Gathering` |

## 1. What happens now

The carousel carries the **current song only** (BLE protocol 7.3, document 3). A song goes on the air for the first time when the leader taps *Nu*: three burst passes, then the normal loop, and GATT repair for whoever missed frames (7.4, 7.6). Before the first *Nu* the air carries the state, info and the setlist document, but no lyrics. So the time the leader spends walking around before and between songs is not used for lyrics, and a singer at the edge of range only gets a song while it is being sung, by many passes or by a repair connection. That is the protocol as specified, not a bug: D-47 and guiDesign Q17 were written about what singers *see*, and the carousel was built to match "send only what is pushed".

## 2. Proposed behaviour

1. **From the start of the gathering, the leader broadcasts every song of the setlist**, in the time the state, info, setlist and the current song leave free. Order: the songs after the current one first (the next song soonest), then the rest, round after round, as long as the gathering runs. Before the first *Nu* all of the large-document time goes to these songs.
2. **The singer keeps what arrives, hidden.** A song received ahead is checked when it is complete (SHA-256 of the file against the `hash` in its reference) and kept in memory with the received lyrics. It is **not shown** until the leader pushes it: D-47 stays exactly as it is, the page holds only pushed songs, and no title or text of an upcoming song appears anywhere.
3. ***Nu* only switches.** When the new state names a song the singer already holds, the singer shows it as soon as the state arrives, exactly as it does today for a song in its own library (`hasSong`, NFR-PERF-01's 2-second path). The burst of the current song still runs as now, for singers who missed it ahead.
4. **The end deletes everything.** Songs received ahead and never pushed are deleted with the rest at the end, on leaving, or when the gathering is treated as ended [FR-DATA-01]; they never reach the gathering memory, which keeps the titles of pushed songs only [FR-DATA-03].
5. **No repair connections for songs ahead.** GATT repair (7.6) stays for the current song only, so the number of connections does not grow [FR-BLE-15].
6. **Exclusions as today.** A song marked "do not redistribute" [FR-LIB-22] is never sent ahead, and a singer that has the song in its library ignores its frames.

## 3. Protocol changes (BLE protocol v0.32)

- **7.3:** a new document type **9, *ahead*** (a setlist song sent before it is pushed), CBOR exactly as document 3 (`ref`, `file`, `lic`). Its `dv` is a 16-bit tag that identifies the song within the gathering (the first two bytes of the song's hash, with a counter on collision), so frames of several songs can be in the air at once and a singer reassembles each separately. Document 3 stays "the current song".
- **7.4:** a new rule. The large-document sets loop: *current song*, *setlist*, *ahead song*, *current song*, … one pass each, with the ahead song rotating through the setlist starting after the current song; before the first *Nu*, *setlist* and *ahead songs* only. The extended set adds the ahead songs to its loop of documents 1–7. The burst on change (rule 4) is unchanged and does not include ahead songs.
- **7.5:** the singer reassembles document 9 per `dv`, verifies the hash and keeps the song hidden; at most the setlist's number of songs, at most 64 KiB each and 1 MiB together, oldest dropped first. Incomplete reassemblies are dropped when the setlist no longer lists the song.
- **9.5:** document 9 described; the setlist document (4) MUST then list upcoming items with their `ref`, so a singer can tell which tags belong to the gathering. The singer's interface still shows no upcoming item (D-47).
- **13:** received-ahead data is untrusted until its hash is checked against a `ref` in the signed setlist; its memory is bounded as above.

## 4. What it costs and what it buys

Figures from 7.7 for a typical song of 2 kB after compression (125 source frames, 256 frames with the erasure code on the legacy carrier; 9 and 27 on the extended carrier), for a setlist of 9 such songs.

| Leader | One round of 9 songs, before the first *Nu* | One round while a song is current |
|---|---|---|
| 3 legacy sets (A52 as leader) | about 2 minutes | about 4 minutes |
| 2 legacy sets (one of them carries songs) | about 4 minutes | about 8 minutes |
| 1 legacy set (Huawei as leader) | about 5 minutes | about 10 minutes |
| Extended carrier | under 1 minute | about 1 minute |

At 40 % reception a singer needs about 1.3 rounds to hold every song, so on three legacy sets everything is in after about 3 minutes of walking around before the start; at the edge of range, at 17 %, after about 6 minutes. During the campfire the current song keeps half of the large-document time, so its delivery to singers who did not have it ahead slows from one pass per 6.3 s to one per about 13 s; that is the price, and it only matters for singers who missed the song in the minutes before. A singer that holds the song shows it in about 0.5–1.5 s after *Nu* (the time for the state record), against 5–6 s measured today on the A52 and Huawei.

Battery and memory: the leader advertises as it does now, only with different frames; a singer keeps at most 1 MiB of lyrics in memory.

## 5. Requirements

- **New FR-BLE-18** (MUST, P1): *From the start of a gathering the leader MUST broadcast the songs of the setlist continuously, in the time the current song and the gathering state leave free, so that singers can hold a song before it is pushed; a singer MUST NOT show a song before the leader pushes it.*
- FR-BLE-12, FR-BLE-15, FR-BLE-16, FR-DATA-01, FR-DATA-03 and NFR-PERF-01 stand as written; D-47 gets a sentence that songs received ahead stay invisible.
- Security assessment: SP-26 for the hidden, bounded store of received-ahead songs.

## 6. The singer's view: the completeness grid (owner, 2026-10-26)

The reception island of D-81 grows into a grid: the row *Lijst* for the setlist document, then **one row per setlist song** in setlist order, the current song marked with the orange bar of D-29. Each row is the song's source blocks (`k`, ble-protocol 7.3) shrunk to the full width, in the colours of D-81: **green** received, rebuilt or fetched; **white** still to come; **orange** missed. A song in the singer's own library is green at once. Rows of songs not yet pushed carry **only their setlist number**; a pushed song carries its title, which the singer already sees on the page (D-47 holds). Rows are thin (about 8 dp) so that a setlist of 20 songs stays under a third of the screen; past that the grid scrolls inside the island.

**When it shows.** The owner's requirement: when it is needed and when it is enabled.

- *Enabled* stays the switch *Ontvangst tonen* in *Instellingen*, *Zingen* (D-81). Proposed change: the switch **keeps its value across restarts**; D-81 set it off at every start because the island was always visible while on, which the next point removes.
- *Needed* means: the current song is not complete on this phone. While enabled, the grid **shows itself** when the state names a song the phone does not yet hold in full, and **hides itself** 3 s after that song is complete. A song already held (sent ahead, or in the library) never brings it up.
- **On request:** while enabled, a long press on the header strip (D-20) shows the grid for as long as the singer wants; a tap on the grid hides it until the next song that is needed. A tap no longer switches the setting off.
- Not enabled: nothing shows, ever, as now.

*D-48:* the grid is only on the screen while a song is missing or the singer asked for it, and never moves, blinks or counts; a complete campfire shows nothing.

## 7. Open points for the owner

1. **Order of the round:** next songs first, then the rest (proposed), or plain setlist order?
2. **Share while a song is current:** half the large-document time for songs ahead (proposed), or less, so the current song stays faster for latecomers?
3. **Songs added to the setlist during the gathering** go into the round as soon as they are added (proposed).
4. **Leader's view:** no new display in this change (proposed); a later one could show on each separator whether the song has gone round at least once.
5. **The switch across restarts:** keep its value (proposed, section 6), or stay off at every start as D-81 has it now?
6. **On request:** a long press on the header strip (proposed); the toolbar of D-17, where an *Ontvangst* button could live, is not built yet.
