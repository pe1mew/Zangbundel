# Session record, 2026-10-19

A dated record of what the sessions of 2026-10-19 were asked and what they left. Not a status board: `memory/MEMORY.md` Current State holds the state.

## The asks, in order

1. In the library repository (`Zangbundel-bibliotheek`): implement a song library to `design/song-format.md`, convert the lyrics under `raw/`, verify their licences, add a validator, make a release. (Started 2026-10-18, continued 2026-10-19.)
2. Start the GUI design: welcome screen, background image and its size, home screen and modes, singer mode, the objective that the phone is not the attraction, leader mode, statistics, setlists; document every decision in `design/guiDesign.md`.
3. Draw the singer-mode flowchart and the BLE sequence diagrams as PlantUML with SVG and PNG renders.
4. Resolve the collisions with the FRD the GUI decisions produced, and specify the setlist file format.

## Threads

| Thread | State | Where |
|---|---|---|
| Library repository: eight songs, validator, release tool, release 2026.10.0 published, `releases.json` live | closed | `Zangbundel-bibliotheek`, committed and tagged by the owner |
| Four songs with untraceable authors kept at the owner's decision, marked `onbekend` / `proprietary`, public credits-and-corrections notice | closed | `Zangbundel-bibliotheek` README, license.md, contributing.md |
| Setlist support in the library repository (validator SL codes, release tool `setlists` manifest, example `setlists/voorbeeld-avond.zbs`, `format_version` 1.1) | partial: staged, not committed | `Zangbundel-bibliotheek`, owner commits |
| GUI design v0.1 to v0.7, D-01 to D-74, singer and leader mode complete for phase 1 | closed | `design/guiDesign.md` |
| Backdrop photograph: portrait and landscape WebP, measurements, preview | closed; consent and photographer-permission checklist unticked | `design/gui/` |
| Singer-mode flowchart, seven BLE sequence diagrams | closed | `design/gui/`, `design/ble/` |
| FRD v0.9: FR-LEAD-30/31 withdrawn, FR-LEAD-32/40 amended, Q5 closed; BLE v0.10 and v0.11; song format v0.3 with section 13 setlists; assessment v0.3 SP-16 | closed | committed by the owner |
| Statistics: planned, then decided share-only without a server; local statistics screen; field tests to the test plan | closed | `design/guiDesign.md` D-60 to D-62, assessment SP-16 |
| FRD section 19 still names `design/ui-flows.md`; the GUI design document stands in for it | open, needs the owner (FRD edit with a version row) | `design/functional-requirements.md`, guiDesign Q5 |
| Magnifier reading mode to prototype at a fire | open | guiDesign Q13 |
| Follow-the-leader at song level only; line position deferred to karaoke | open, accepted for phase 1 | guiDesign Q20 |
| A follow-up session was started for the statistics assessment; it finished and its files were committed | closed | assessment v0.3, RUNBOOK Step 4 note |

## Decisions of the day, one line each

- The phone is not the attraction (D-48); singers see only what the leader pushed (D-47); pushed songs vanish at the end, the setlist stays as a memory (D-49).
- Leader mode: libraries from repository or file, colour-coded songbook with filters and reset, setlists made and filled separately and importable as `.zbs`, the leader's page with *Nu* separators, the slide-open leader bar as the control view, closing bar at the end of the setlist.
- No download instruction, no singer list, no hand-over, no join-code change at the fire; delegates by helper code (BLE op 9); explicit leave (BLE op 10).
- Statistics share-only through the share sheet, no server.
- Setlists are format 1.1; unresolved entries are shown with their reason, never skipped silently (D-74).

## Lessons

See `memory/gotcha-log.md` entries dated 2026-10-19: GUI wishes against FRD MUSTs (promoted), PlantUML on Java 8 needs `-charset UTF-8`, switch/elseif chains render sideways, PNG clipping at 4096 pixels, parallel sessions in one checkout.
