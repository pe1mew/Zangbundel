# Proposal: erasure coding across broadcast frames

| Field | Value |
|---|---|
| Proposal | Erasure coding on both carriers, GATT repair as fallback, the library as the fast path |
| Date | 2026-10-25 |
| Status | **Approved as written by the owner 2026-10-25 and moved into `design/ble-protocol.md` v0.21** (7.2–7.7, 9.7, 12.1); implemented in `core/protocol`. This file stays as the record of the comparison. |
| Affects | `design/ble-protocol.md` 7.1–7.7, 9.4 (`len`), 9.7, 12.1; `design/functional-requirements.md` NFR-PERF-01, FR-BLE-03 (FRD Q14); `design/test-plan.md` S-02, F-02 |
| Recorded as | ble-protocol Q21, FRD Q14 |
| Evidence | `design/test-results/2026-10-25-D-05.md` |

## 1. The problem

On the first two-phone runs a singer heard only part of the advertising events sent to it: per advertiser at 100 ms the Huawei Mate 20 Lite heard 17 % (40 % with Wi-Fi off), the PC 24 %, the Samsung A52 38 %. A third receiver showed that the senders are not the bottleneck. The carousel of protocol v0.18 assumes nearly every frame arrives: a 2 kB document is 125 legacy frames, each of which must be heard at least once. At 40 % reception the Huawei needed 44 and 49 s to complete one; at 17 % none completed.

## 2. What the decision buys

Simulated delivery of a 2 kB document (≈ 4 kB of song text after DEFLATE), with every transmission received independently at the stated rate; median, and in brackets the 90th percentile. The simulation predicts 61 s for the current rules at 40 %, against the 44 and 49 s measured, so it is in the right range and slightly pessimistic.

| Option | 17 % | 40 % | 95 % |
|---|---|---|---|
| Current rules (v0.18), 3 legacy sets | 170 s (240) | 61 s (82) | 9.1 s (12.6) |
| More passes: all sets loop the song | 117 s (159) | 43 s (57) | 8.6 s (11.9) |
| **Erasure code, 3 legacy sets** | 31 s (34) | 12 s (13) | 4.4 s (4.5) |
| Extended carrier, current rules | 18 s (31) | 7 s (11) | 1.3 s (2.3) |
| **Extended carrier with erasure code** | 6 s (8) | 2.2 s (3.1) | 0.9 s (1.0) |

Losses on real phones may come in bursts, which these figures do not model. The extended rows assume the singer receives extended advertising at the same rate; no phone here could test that (the Huawei cannot receive it, and only the A52 can lead it).

## 3. Proposed protocol changes

### 3.1 The code

A systematic Reed–Solomon erasure code over GF(2⁸) across the frames of one document version. The `k` **source frames** are the document cut into frames of `fs` bytes (16 on the legacy carrier, `fs` on the extended carrier), the last one padded with zero bytes; they are exactly today's frames. The leader adds `r` **repair frames**. A singer that has any `k` distinct frames of the `n = k + r` reconstructs the document; one that has all source frames needs no decoding.

- Field: GF(2⁸) with the polynomial x⁸ + x⁴ + x³ + x² + 1 (`0x11D`).
- Repair frame `j` (0 ≤ j < r), byte position `b`: `R_j[b] = Σᵢ c(j, i) · S_i[b]` over the source frames `i` (0 ≤ i < k), with the Cauchy coefficients `c(j, i) = 1 / (x_j ⊕ y_i)`, `y_i = i`, `x_j = k + j`. Every square submatrix of a Cauchy matrix is invertible, so any `k` rows of the stacked identity and Cauchy matrix decode.
- Limits: `n ≤ 256`. Redundancy `r = min(2k, 256 − k)`: a 2 kB document (`k` = 125) gets 131 repair frames, a state record (`k` = 15) 30, a song on the extended carrier (`k` = 9) 18. A document with `k > 255` (only on the extended carrier, above about 59 kB) is sent uncoded and relies on repair.
- All broadcast documents 1–7 are coded on both carriers, the state record included, because the state is what a singer needs first.

### 3.2 Frame formats (replacing 7.2)

**Legacy frame (26 bytes, within the 27 a legacy manufacturer-data structure allows):**

| Offset | Field | Type | Meaning |
|---|---|---|---|
| 0 | `magic` | u8 | `0xD1` |
| 1–2 | `gid16` | 2 bytes | |
| 3 | `doc` | u8 | |
| 4–5 | `dv` | u16 | |
| 6 | `k` | u8 | Number of source frames (1–255). Replaces `total`. |
| 7 | `idx` | u8 | 0 … k − 1 source, k … n − 1 repair. |
| 8–9 | `len` | u16 | Document length in bytes, to strip the padding. |
| 10–25 | `data` | 16 bytes | Source bytes, or a repair frame. Always 16 bytes. |

**Extended frame (≤ 247 bytes):** `magic` `0xD2`, `gid16`, `doc`, `dv`, `k` (u16), `idx` (u16), `fs` (u16), `len` (u16), then exactly `fs` ≤ 233 data bytes; the header grows from 12 to 14 bytes, so `fs` falls from 235 to 233.

Because every frame carries `len`, the state record and Info, which have no manifest entry, are coded like the rest.

### 3.3 Carousel (7.4)

One **pass** of a document is all `n` frames, source frames first. The burst sends three passes, shared over the legacy sets as v0.18 decided; the normal loop cycles the `n` frames. The pass time `pt` becomes the time for `n` frames on the legacy carrier.

### 3.4 Reassembly (7.5)

A group keeps a bitmap of `n` positions and the frames received. When `k` distinct frames are present it decodes (directly when they are the source frames), strips the padding to `len`, and verifies as today: signature for the state, `SHA-256(pk)` for Info, `dh` for the rest. Repaired bytes from GATT count as the source frames they cover.

### 3.5 Repair over GATT as the fallback (7.6, 9.7)

Transfer stays as it is, in the document's byte space; the leader still clamps ranges. What changes is when a singer gives up on the broadcast. The fixed timers of 7.6 (3 s for a song on legacy) would send nearly every singer to GATT at these reception rates. Proposed: a singer repairs a coded document when, from its own rate of new distinct frames over the last 2 s, it does not expect to reach `k` within **10 s** on the legacy carrier or **4 s** on the extended carrier, or when nothing new has arrived for 3 s. Those two numbers are open (section 6).

Cost, estimated and untested (GATT is not built yet): one connection per singer that gives up, about 1–2 s each with an MTU of 247, six slots at a time (10.8). If 25 singers all gave up, the last would finish 5–10 s after the timer.

### 3.6 The library as the fast path (FR-BLE-12, unchanged)

A singer whose library holds the pushed song needs only the beacon and the state record. The beacon carried every push to the singer within about a second in the measurements, and the state record is 15 legacy frames, which coding makes robust. This is the only path that meets the 2-second target for local songs at the measured rates. It needs no protocol change, only that scouts install the library before the camp; the GUI's onboarding is where to ask for that (a follow-up for `design/guiDesign.md`).

## 4. Costs

- **Wire format.** Both frame layouts change, inside the draft protocol 1.0 that has no released implementation; no major version bump.
- **Code.** A GF(2⁸) Reed–Solomon module in `core/protocol` (about 200 lines, encoder and decoder by Gaussian elimination), changes to the framer, reassembler and carousel, a new fuzz target for the decoder. An iOS app must implement the same code byte for byte; the conformance corpus gets coded frames.
- **Air time.** A pass is about twice as long (`n` ≈ 2k), so the burst of three passes takes about 26 s for a 2 kB song on three legacy sets. Singers finish long before; the leader is simply busier for longer.
- **CPU and memory.** Negligible: decoding 125 frames of 16 bytes is a 125 × 125 elimination over GF(2⁸); the leader keeps `n` frames per document.
- **Limits.** Legacy documents stay at 4 080 bytes (255 source frames); extended documents above about 59 kB go uncoded.

## 5. Expected result against the requirements

With the code, a 2 kB song not held locally reaches a legacy-only singer in about 12 s at 40 % reception and 31 s at 17 %; on the extended carrier in about 2 s and 6 s. NFR-PERF-01 (4 kB in 5 s for 25 singers) therefore looks attainable only on the extended carrier and for library holders. That is FRD Q14.

## 6. Open points for the owner

1. **Redundancy.** `r = min(2k, 256 − k)` as proposed, or less to shorten the passes.
2. **Repair thresholds.** 10 s on legacy and 4 s on extended, or other values; they set the balance between waiting for the broadcast and loading the leader with connections.
3. **The NFR-PERF-01 target** (FRD Q14).
4. **Measure before implementing?** The extended rows and the independence assumption need a second extended-capable phone and the field test F-02.
