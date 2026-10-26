# Proposal: a setlist document that does not change during the campfire

| Field | Value |
|---|---|
| Proposal | The setlist document carries only the order of the songs (their `dh` per position) and changes only when the setlist itself changes; what is sung and what is current comes from the signed state and from the songs pushed |
| Date | 2026-10-26 |
| Status | **Approved by the owner 2026-10-26, late joiners included; built as BLE protocol v0.35 and guiDesign v0.29.** Results on the phones in `design/test-results/2026-10-26-state-after-push.md`. |
| Asked by | The owner, 2026-10-26, after asking why the *Lijst* row of the reception grid turns white on every push and green again |
| Affects | `design/ble-protocol.md` 9.4 (two state keys), 9.5 (setlist document), 7.4 (bursts), 10.5; `design/guiDesign.md` D-31, D-47 (a note); `core/protocol` `SetlistDocument`, `StateBody`, `LeaderSession`, `SingerEngine`; `app/gathering/Gathering` |
| Evidence | `design/test-results/2026-10-26-state-after-push.md` (BLE protocol v0.34 run) |

## 1. What happens now

The setlist document (9.5, document 4) lists the songs sung so far and the current one, each with its reference, title and status (upcoming songs are left out, guiDesign D-47), plus since v0.32 the `dh` of every song by position. **Every push changes it**: a song moves from current to sung, and the new current one is added. So every push gives it a new `dh` and `dv`, and:

- the burst carries it three times (in this setlist about 60–100 legacy frames per pass);
- since v0.34 its frames come after the state's 4.5-s lead-in, so singers fetch it over GATT: on the phones the S23 made a separate connection for it after every push (+4.3 s), and the Huawei took it on its state connection;
- the reception grid's *Lijst* row starts again from white on every push, which is what the owner saw.

**Nothing on a singer's phone reads the items.** The singer's page (D-29), its order and its titles are built from the pushes themselves; the engine reads only the `dh` list. The items are sent with every push and used by nobody.

## 2. Proposed change

**The setlist document becomes the order alone.** Key 2, `ahead`: one `dh` per setlist position, empty for a song not broadcast [FR-LIB-22]; key 1 (`items`) is no longer sent. It is published at the start of the gathering and changes only when the setlist changes (a song added or removed, FR-LEAD-03), not when a song is pushed.

**The signed state carries where the campfire is**, in two new keys of the state body (9.4):

- `cpos` (uint, optional): the setlist position of the current song, 1-based; absent when none, or when the current song is not on the setlist (pushed from the songbook).
- `sung` (bstr, optional): a bitmap of the setlist positions pushed so far in this gathering, bit *i* for position *i* + 1; 1 byte per 8 positions (2 bytes for this 9-song setlist, 8 for 64 songs).

**The burst after a push** then carries the state lead-in and, when it changed, the song; no setlist.

## 3. What it saves

- **One GATT connection per singer per push** that only fetched the setlist (the S23's at +4.3 s), and on the Huawei's state connection the setlist's transfer.
- **Air time:** 3 passes of the setlist (180–300 legacy frames in this setlist) out of every burst, about 2–3 s on three sets, which brings the song's burst forward by that much for singers that do not hold it ahead.
- **The *Lijst* row stays green** after the first time; a push changes only the state.
- **Size:** the state grows by `cpos` and `sung`, 3 bytes for this setlist and at most about 10, within the 440-byte limit (9.4) and usually within the same number of frames.

## 4. What the app must derive itself

| Needed | Today from | Then from |
|---|---|---|
| The current song | the state's `cur` (unchanged) | the same; `cpos` adds its setlist position, also for a song that may not be broadcast |
| Songs already sung, for a singer present from the start | its own pushes | the same |
| Songs already sung, for a late joiner [FR-SING-03] | nothing (the items are not read) | `sung` gives the positions; the songs held ahead for those positions (verified by the static setlist's `dh` list) give title and text, so a late joiner can show what was sung before it arrived. Positions not held ahead show as numbers, as in the grid |
| The order of the page (D-29) | push order | push order for what the singer saw pushed; earlier sung songs of a late joiner in setlist order before them |
| The reception grid (D-81) | `dh` list, plus the pushed references for titles | the same |
| The leader's setlist block | the leader's own state | unchanged |

**One point on D-47:** a late joiner would show earlier sung songs it received ahead. They *were* pushed in this gathering, before it arrived, so the rule "only what the leader has sent" still holds; but D-47 says "in the order the leader pushed them", and the earlier ones would come in setlist order. A note on D-47 records that.

## 5. Costs and risks

- A protocol change in 9.4 and 9.5; all phones must run the new version (no release has gone out).
- The state record grows by a few bytes per 8 setlist positions.
- A late joiner that has not yet received an earlier sung song ahead shows its position as a number until it does.
- A song pushed twice in a row is not visible in `sung` (a bitmap); the page of a singer present at both pushes is unaffected.

## 6. Open points for the owner

1. Approve the static setlist with `cpos` and `sung` in the state?
2. Late joiners: show earlier sung songs from songs held ahead (proposed), or only from the moment of joining, as now?
