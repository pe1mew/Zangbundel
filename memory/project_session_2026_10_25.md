# Session record 2026-10-22 to 2026-10-25

Dated record, not a status board. Current state lives in `memory/MEMORY.md`.

## The ask

"Scaffold the Android project in android/ according to the architecture's section 4 module layout", followed in the same session by: getting CI green, testing on the owner's phones, a radio check, and the protocol core in `core/protocol`.

## Threads

| Thread | Outcome | Commit |
|---|---|---|
| Android scaffold: nine modules, pinned toolchain, manifest, resources, first tests | Closed | `d782dcd` |
| CI green: verification metadata missed cached poms, then the Linux aapt2 jar | Closed | `0e149d5`, `0e102eb` |
| HTC One M7 as a test phone | Closed: Android 5, too old; usable only as a BLE scanner | none |
| Huawei Mate 20 Lite and Samsung A52 as test phones | Closed: debug build runs on both | none |
| Dark system bar icons on night blue (A52) | Closed | `fea185b` |
| Radio check (test-plan F-01) and first results | Closed: `design/test-results/2026-10-25-F-01.md` | `8c8d7c6` |
| Extended frame cannot be sent as specified | Closed: owner chose one 247-byte structure, protocol v0.14 | `8c8d7c6` |
| CBOR codec, frames, reassembly, section 9 payloads, signed state, DEFLATE | Closed | `55efdcc` |
| Sealed payloads (8.4); op 8 chunk limit 432 corrected to 417 | Closed: protocol v0.16 | `c0d5134` |
| Protocol gaps Q17 to Q20 | Closed: decided by the owner, protocol v0.17, implemented | `ee11742` |
| Jazzer fuzz targets (test plan 3.4) | Open: Jazzer's JUnit integration needs JUnit 5; the tests are JUnit 4 | none |
| Carousel scheduler, state machines, `AndroidTransport`, screens | Open: next protocol work | none |
| compileSdk 37 (newer AndroidX) | Not ours: owner's call | none |

## Decided by the owner

- 2026-10-25: extended frame in one manufacturer-data structure, at most 247 bytes (protocol v0.14), after the A52 refused any structure above 251 bytes.
- 2026-10-25: Q17 state body fitted by leaving out title, then rev, then pt; Q18 repair ranges ended by the manifest `len` and clamped by the leader; Q19 decoded documents at most 67 584 bytes; Q20 document 8's `dh` over compressed bytes (protocol v0.17). The Q19 option label said 66 560 bytes by mistake; 67 584 was implemented, as its description said, and the owner was told.
