# Session record 2026-10-28 to 2026-11-03 (German and 0.3.0)

Dated record, not a status board. Current state lives in `memory/MEMORY.md`. The session was compacted several times; the threads come from the commits and the summaries.

## The ask

A run of the owner's requests: the failed F-Droid MR pipeline, the README for 0.2.0 and the closed test, a promotional text for a radio-scout group, the wiki's Testing page, then German: "can we push the app with the german language to internal testing?" (the reviewer lives in Switzerland), issues from the reviewer's texts, "promote the german language to production version", "create next release with German language added. close the issues with added solution and refer to this release", the wiki for 0.3.0, and a feature request for an update check.

## Threads

| Thread | Outcome | Commit |
|---|---|---|
| F-Droid MR !54679 pipeline | Closed: CRLF from pasting was the failure; the maintainer closed the MR on Quality point 6; resubmit from 2027-01-18 | `2472411` |
| README for 0.2.0 and the closed test | Closed | `3fa63a8` |
| Promotional text, 500 characters | Closed, in chat only | — |
| Wiki Testing/Testen: group link, default and own library | Closed, pushed by the owner | `c7dd9ce` |
| Review release on the internal track (A-23, SP-36, FR-LANG-08) | Closed: `src/review/res`, `-PreviewVersionCode`; 2026102890 uploaded by the owner | `163cbc4` |
| Issues #1 to #5 from the reviewer | #1 to #4 fixed (*Horche…*, *Lead a campfire*, *Your name*, gender star) and closed with a comment naming 0.3.0; #5 open for French | `163cbc4` |
| German approved, into `src/main` | Closed (the owner: "Yes, fully approved") | `163cbc4` |
| Release 0.3.0 | Partial: bundle on the closed test, tag, GitHub release, G-07 and G-08 (GitHub) pass; open: G-08 on a Play install, D-01 to D-08 with *Deutsch* | `acce35a`, `38ec401` |
| Wiki manual for 0.3.0 | Closed, live | `9d73bdf`, wiki `4ca3f4d` |
| Feature request: update check on About | Closed as issue #6 with a checklist of points to settle (FR-NET-01/03 stand in the way of an in-app check) | — |

## The owner's decisions

- German is a released language from 0.3.0, on the Swiss reviewer's full review.
- German uses the gender star (Leiter\*in, Sänger\*innen); *Leitung* in compounds. French will use the middle dot (#5).
- English *Lead a campfire* and *Your name*; Dutch *Kampvuur leiden*, *Je naam*.
- Not yours: the German privacy policy is my draft and needs the reviewer's check.
