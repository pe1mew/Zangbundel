# Review Profile

<!-- Read by the review-changes skill at Step 1. One per repo; never user-global
     (the skill is global, this file is the repo-specific half). -->

Read by `review-changes` Step 1. **This file is Zangbundel's half of that skill.** The skill
ships identically to every project; everything in this file is ours and never travels.

The repository is in its design phase: the three documents under `design/` are the product
until code exists, and other documents (and later the app) are built from them. A wrong
sentence in a spec reaches every reader who builds on it, which is why specs are HIGH here.

## Risk tiers

| Tier | File patterns | Depth |
|------|-------------|-------|
| **HIGH** | `design/functional-requirements.md`, `design/ble-protocol.md`, `design/song-format.md`, `CLAUDE.md`, `.claude/review-profile.md`, `scripts/**`, `.gitignore`, `LICENSE`, `license.md` | Full battery (3-4 lenses) |
| **MEDIUM** | `design/**` (anything else under design, e.g. a future architecture or UI-flow document), `android/**` (re-tier to HIGH for published interfaces once code exists), `README.md`, `contributing.md` | Two lenses (adversarial + doc-accuracy) |
| **LOW** | `memory/**`, `design/prompts.md`, `changelog.md`, `code_of_conduct.md` | One lens (adversarial) |

`**` crosses directory levels; a leading `/` anchors to the repo root. **The most specific
matching pattern wins.** Where no pattern is more specific than another, take the highest tier.

## Guarantee surfaces

Every path here sits in the HIGH row above.

- `design/functional-requirements.md`: a requirement ID, once published, keeps its meaning for ever; a withdrawn one is marked *Withdrawn* and kept, never deleted or renumbered. The version table and change history record every normative change.
- `design/ble-protocol.md`: the beacon layout (section 6.1), the frame formats (7.2), the GATT UUIDs and error codes (8), and every CBOR key number (9) are wire format. Within protocol major version 1 they may only be added to, never renumbered or re-meaned; a breaking change bumps the major version.
- `design/song-format.md`: the canonical form and the content-hash definition decide whether two devices consider a song identical. A change to either silently breaks matching between devices on different app versions, so it needs a format-version bump and a note in the protocol.
- `scripts/check-design.sh`: the gate named in `CLAUDE.md` Hard Constraints. It must exit non-zero on a seeded failure (a cited-but-undefined ID), or it is a silent hook.

## Test baseline

```bash
bash scripts/check-design.sh
```

That is the whole test surface today. When application code lands in `android/`, add its build and
unit-test command here and name the Gradle wrapper (`./gradlew`), not a system Gradle.

## Always-full-depth carve-outs (project additions)

- `design/*.md` header tables (the `| Version |` row and the change-history table): a one-line version bump claims a document is current with the FRD; check that the body actually changed to match.
- Any edit to a `Depends on` line: it declares cross-document consistency that `check-design.sh` only partly verifies (version only, not content).
- `design/ble-protocol.md` section 16 (requirements traceability) and `design/functional-requirements.md` section 20 (requirement summary by phase): small tables that readers trust instead of re-reading the body.

## Project lenses

- `spec-coherence` — fires on: `design/**`
  *Read the changed document against the other two specs, not just against itself. Refute the claim that the three documents still agree: find any requirement the protocol or song format cites whose wording or priority (MUST/SHOULD/MAY) changed in the FRD without the citing document changing; any number stated in two documents (frame sizes, timeouts, limits, hash lengths) that now differs; any FRD open question the changed document silently answers without the FRD recording it. A positive is a concrete pair of sentences, one from each document, that cannot both be true.*

## Project additions to the shipped lens prompts

- **adversarial**: *Attack the physical and platform claims. Anything stated about BLE capacity (bytes per advertisement, advertising intervals, connection limits, MTU), Android or iOS API behaviour, or timing budgets is an assertion from reading, not from a device. Flag a claim as a blocker when it is stated as fact but the document's own open questions or constraints say it has not been measured, and when a number was changed without the budget table that depends on it changing too.*
- **doc-accuracy**: *The FRD's section 20 summary table and the protocol's section 16 traceability table are derived from the bodies; a new or re-tiered requirement that does not appear there is a defect even though the body is right.*

## Project procedure kept with the profile

Run the test baseline before and after reading the diff. Then seed it once per review of
`scripts/check-design.sh` itself: temporarily cite a non-existent ID such as `FR-ZZZ-99` in a scratch
copy of a design document (not the tracked file), run the script against the copy, and confirm it
exits 1. A check that cannot be made to fail has not been shown to work.
