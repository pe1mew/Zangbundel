# BLE sequence diagrams

Sequence diagrams of what happens on the air for the main scenarios of `design/ble-protocol.md` (document v0.11, protocol 1.0), drawn in preparation of the technical design. Each diagram cites the protocol sections and FRD requirements it depicts, and the GUI decisions (`design/guiDesign.md` D-nn) at the points where a person acts or sees something. The protocol document is normative; a diagram that disagrees with it is wrong.

| File | Scenario | Protocol sections |
|---|---|---|
| `seq-01-start-gathering` | The leader starts a gathering: pre-start scan, keys, documents, beacon, data sets, carousel, background scan for other gatherings | 6, 7.1, 7.4, 10.1 |
| `seq-02-join-gathering` | A singer joins from the list or by QR code: verify the key, collect state and song, register with a sealed Join, repair what is still missing | 6.5, 7.5, 8.4, 9.2–9.4, 9.7, 10.2 |
| `seq-03-push-song` | The leader pushes a song with *Nu*: new state, beacon, burst; a singer with the library uses its own file, one without collects the frames | 7.4, 9.4, 9.5, 10.3, 10.5 |
| `seq-04-missing-song-repair` | A singer misses frames: repair timer, random delay, Transfer of the missing byte ranges over GATT, backoff when the leader is busy | 7.6, 9.7, 10.4, 10.8 |
| `seq-05-singer-leaves` | A singer leaves on purpose or walks out of range: local clean-up and memory, reconnecting, the 5-minute end; what the leader sees | 3.3, 10.9, 10.11 |
| `seq-06-end-gathering` | The leader ends the gathering: final state and beacon, singers' clean-up, the 60-second window, key deletion | 9.4, 10.10, 10.11 |
| `seq-07-helper-code-delegate` | The leader shows a helper code, the helper's app sends it, the delegate acts through the leader, the leader revokes | 8.4, 9.4, 9.8, 10.12 |

Each scenario exists as `.puml` (source), `.svg` and `.png`. Render with PlantUML (the jar from any project; sequence diagrams need no Graphviz):

```bash
java -jar plantuml.jar -charset UTF-8 -tsvg -tpng design/ble/seq-*.puml
```

The `-charset UTF-8` flag matters on Java 8, which otherwise reads the files as cp1252 and garbles the dashes and arrows.

## Findings while drawing

- **Explicit leave, since v0.11.** Drawing scenario 5 against v0.10 showed that the protocol had no message for a singer that leaves, so the leader's count only dropped after ten minutes without GATT activity (10.9). Protocol v0.11 added Control op 10: a leaving singer tells the leader, best effort within two seconds, and the count drops at once; silent departures are still caught by the ten-minute rule. Scenario 5 shows both.
- **Join by QR skips Info.** Scenario 2 shows the two ways in side by side: from the list the key comes from the Info document and is checked against `gid`; from the QR code the key is pinned from the code and Info is never consulted (6.5).
- **Helper code, since v0.11.** The delegate appointment by a four-digit code shown on the leader's screen (guiDesign D-73) is Control op 9 in protocol v0.11 (9.8, 10.12); scenario 7 draws it together with a delegate action and the revocation.
