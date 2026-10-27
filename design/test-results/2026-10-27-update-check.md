# The automatic library update check on Wi-Fi – 2026-10-27

FR-LIB-13; guiDesign D-50 as built in v0.47; architecture A-14 (v0.15); assessment SP-29. On the A52, debug build, on Wi-Fi, with *Zangbundel bibliotheek* 2026.10.2 installed (the newest release). **Result: the switch schedules one daily job on an unmetered network; its first run follows at once, reads only the release index, and answers *Current*, then *Unchanged* by a 304. Two faults found and fixed on the way.**

| Step | Result |
|---|---|
| *Automatisch controleren op wifi* switched on (first build) | WorkManager scheduled the job and a one-time job; both ran within a second; log `library update check zangbundel-bibliotheek: Current` |
| One-time job removed: a new periodic job runs its first check at once anyway | — |
| The switch shown **off** after a reinstall while `auto_update_check` was stored **true** | **Fault:** the library view model wrote `_state.value.copy(libraries = index.all())`, a copy read before the call suspended, over the switch's state; taps then wrote `true` again and nothing changed on screen. Fixed with `update { }` on values computed first |
| After the fix: the switch shown on; off, then on | One run: `library update check zangbundel-bibliotheek: Failed` |
| The failure | **Fault:** `HttpsFetcher` took GitHub's *304 Not Modified* to the stored `ETag` for a redirect without `Location`. Fixed |
| After the fix: off, then on | One run: `library update check zangbundel-bibliotheek: Unchanged` (GitHub answered 304) |
| Merged manifest | New permissions `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, as SP-29 and RUNBOOK Step 5 say; WorkManager's boot receiver ships disabled and was enabled when the job was scheduled (`WM-PackageManagerHelper: RescheduleReceiver enabled`) |

**Covered by unit tests, not on the phone:** *Nieuwe versie beschikbaar* (a release in the index with another SHA-256 than the installed archive), and libraries without a release index (an archive address, a file), which are never fetched (`LibraryRepositoryTest`). **Not tested:** the daily run itself, and mobile data (the job must wait).
