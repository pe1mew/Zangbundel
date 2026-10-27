# Another campfire nearby and the battery – 2026-10-27

FR-GATH-15, FR-GATH-17; guiDesign D-68 and D-61 as built in v0.51; BLE 10.1 step 6 (v0.42); assessment SP-30. Debug build on the A52 and the S23, side by side on a desk; the S23 had battery saver on and was on USB power at 100 %, the A52 had it off. **Result: each leader hears the other within one or two listens, the warning sign and the drawer rows appear and go, the battery line shows before *Starten* and its button opens the system setting. One naming problem found by the owner and settled.**

| Step | Result |
|---|---|
| *Kampvuur starten* on both phones at once | Both reached the start screen; the S23 showed *Zet de batterijbespaarstand uit en gebruik een powerbank…* with *Batterij-instellingen* (its `low_power` setting was 1); the A52 showed no line |
| The battery line's layout | **Fault:** the button beside the line squeezed it into a narrow column. The button now sits under the line. **Not seen on the phone after the change**: the S23 had locked itself |
| *Starten* on both within a second | Neither pre-start scan saw the other (neither advertised yet); both started. First listen, 2 s later: each logged `leader other gathering nearby: Kampvuur 27 okt` |
| The leader bars | An orange ⚠ at the right of both bars |
| The drawers | A52: *Ander kampvuur in de buurt: "Kampvuur 27 okt"*. S23: *Batterijbespaarstand staat aan ›* and the same row. **The owner:** two campfires with the same name in that row is nonsense. Decided: name the other gathering, or its two words when the name is the leader's own |
| After the change: both apps reinstalled (the gatherings died), both resumed at once | S23 after its first listen: *Ander kampvuur in de buurt: "koek-toon"* (the A52's words); A52 at its second listen, 30 s later: `braam-klaver`. A resumed gathering did not report itself |
| *Batterijbespaarstand staat aan ›* on the S23 | Opened Samsung's `BatterySaverSettingsActivity`; back returned to the leader's page |
| The S23 ended its campfire | On the A52 the sign and the row went 90 s after the S23 was last heard (`other gathering nearby: none`), within the 65 s timeout plus one listen interval |
| The S23 back at its home screen | It listed the A52's campfire and offered to join rather than start (FR-GATH-16) |

**Covered by unit tests:** own beacons, ended gatherings and relays are never another gathering; the one heard last is named; one not heard for 65 s is gone; a name equal to the leader's own, ignoring case and spaces at the ends, gives the two words (`OtherCampfiresTest`). **Not tested:** battery saver switched on during a gathering (the S23 had it on from the start), a battery below 30 % without a charger, the screen off while leading, and range in the field (F-09).
