# Proposal: knowing which singers are still there

| Field | Value |
|---|---|
| Proposal | How the leader's singer count drops for singers that leave without a leave notice |
| Date | 2026-10-26 |
| Status | **Option B chosen by the owner 2026-10-26 for phase 1; built as BLE protocol v0.38 and guiDesign v0.37.** Option A stays on record for a later phase. |
| Asked by | The owner, 2026-10-26: the radio and protocol work comes first, starting with stale singers |
| Affects | `design/functional-requirements.md` FR-BLE-15 (option A); `design/ble-protocol.md` 9.8 (a Control op), 10.9; `core/protocol` `LeaderGattService`, `SingerEngine`; the leader's drawer (D-61) |

## 1. What happens now

A singer that leaves on purpose sends Control op 10, and the count drops at once (10.9, v0.29). For everything else the protocol says the leader drops a singer after 10 minutes without GATT activity. In the code that expiry is **never run** (`LeaderGattService.tick` has no caller), so a singer that disappears without a notice stays counted until the end of the campfire.

Switching the expiry on as written would be wrong the other way: "seen" means a Join or a Control command carrying the singer's token. Repairs carry no token (Transfer is not sealed), and a singer with good reception and the songs ahead (FR-BLE-18) may never connect at all, so it would be dropped after 10 minutes while still singing.

The protocol has no presence signal from singers, and FR-BLE-15 allows a singer to connect only to register, request, act as delegate or recover what it missed.

**Built today, no protocol change:** a singer who swipes the app away sends the leave notice (the service waits until it is out). Left uncovered: a crash, a force stop, a flat battery, and walking out of range.

## 2. Options

### A. A presence notice every few minutes (protocol change; FR-BLE-15 amended)

A registered singer sends a sealed Control command *present* (new op 11, token inside, as every Control) every 4 minutes with ±30 s of jitter, skipped when it made any token-carrying write in that time. The leader runs the expiry of 10.9 with 10 minutes, so a singer that misses two notices in a row drops out.

- **Buys:** the count is right within about 10 minutes for every way of leaving.
- **Costs:** one short connection per singer every 4 minutes: about 6 a minute for 25 singers, 25 a minute for 100, each about a second of one of the leader's 6 slots; some battery on singers. FR-BLE-15 gets a fifth reason to connect.

### B. No expiry; say what the number is (recommended for phase 1)

Keep the count as registered singers minus those who left, with the swipe-away notice now covering the most common way of closing the app. The drawer labels the figure *aangemeld* (signed in) so the leader reads it as an upper bound, as 10.9 described before v0.11.

- **Buys:** no new connections, no protocol change.
- **Costs:** a crashed or out-of-range singer stays counted until the end.

### C. Presence by repairs (not possible as is)

Repairs would mark a singer present if they carried its token, but Transfer is not sealed and the token must only travel sealed (13). Not proposed.

## 3. Also looked at: songs not held ahead

Since v0.34 a singer that does not hold the pushed song ahead fetches it over GATT, because its frames follow the state's 4.5-s lead-in and the rule of 7.6 aims at NFR-PERF-01's 5 seconds. By air the song would come after 8.7 s at the earliest on three sets, so the repair is the faster path, and with songs ahead it is the exception (a singer that just joined, a song added late). **No change proposed.**

## 4. Open point for the owner

Option B for phase 1 (recommended), or option A now?
