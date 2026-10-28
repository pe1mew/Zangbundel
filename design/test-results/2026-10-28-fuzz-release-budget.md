# Release check G-09 at the release budget – 2026-10-27/28

Test plan section 3.4 at its release budget: `bash scripts/fuzz.sh 3600`, an hour per Jazzer target, on the owner's PC (8 cores, Temurin 17.0.20). **Result: pass. All 14 targets fuzzed for an hour each, no finding.** This completes G-09 for the gate (test plan 9) on the core code of 0.1.1; a change to a parser or decoder before the release commit calls for a new run of its target.

| Target | Module | Fuzzed | Result |
|---|---|---|---|
| songParser | core:songformat | 3 607 s | pass |
| setlistParser | core:songformat | 3 608 s | pass |
| libraryFile | core:songformat | 3 607 s | pass |
| releaseIndex | core:songformat | 3 607 s | pass |
| archiveManifest | core:songformat | 3 607 s | pass |
| archive | core:songformat | 3 607 s | pass |
| cbor | core:protocol | 3 607 s | pass |
| payloads | core:protocol | 3 607 s | pass |
| deflate | core:protocol | 3 607 s | pass |
| beacon | core:protocol | 3 607 s | pass |
| joinLink | core:protocol | 3 606 s | pass |
| reassembly | core:protocol | 3 607 s | pass |
| erasure | core:protocol | 3 607 s | pass |
| gattWrites | core:protocol | 3 619 s | pass |

**How it ran.** The first thirteen targets ran one after another in the working tree, from 19:46 on 27 October (commit `9fb1fa1`) to 08:47 on 28 October. The PC was needed elsewhere by 09:00, so `gattWrites`, last in the order, ran in parallel in a fresh clone of `fb6fea5` from 23:08 to 00:09; the main run's own `gattWrites`, which had begun at 08:47, was stopped at once and its Gradle daemon with it. The commits during the run (`7543365`, `fb6fea5`, `d63af14`) changed nothing under `android/core/` (only `android/app/build.gradle.kts`), so every target ran on the same core code, which is that of the 0.1.1 release commit `6faaf6f`.

**What a pass means here.** `scripts/fuzz.sh` counts a target as passed when its Gradle run exits cleanly: Jazzer reported no finding. No new input appeared under `src/fuzz/resources/` (`git status` clean afterwards). The script discards Jazzer's output, so the number of inputs per target and the coverage reached are not recorded.

**Not run:** the run on the release commit itself (the core code is unchanged, as above); counting inputs or coverage.
