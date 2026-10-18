# Zangbundel – Secure-by-Design and Privacy-by-Design Assessment

| Field | Value |
|---|---|
| Document | Assessment of the design against the project's security and privacy principles |
| Project | Zangbundel – campfire songbook app |
| Document version | 0.1 |
| Date | 2026-10-18 |
| Author | Remko Welling |
| Status | Assessment of the design as of FRD v0.5, BLE protocol v0.5, song format v0.2, docs/RUNBOOK.md; the changes it led to are in FRD v0.6 and BLE v0.6 |

| Version | Date | Change |
|---|---|---|
| 0.1 | 2026-10-18 | First assessment, before any code exists. |
| 0.2 | 2026-10-18 | SP-04 resolution extended: joining by QR code that carries the leader's public key (FR-GATH-19 to 23, BLE 6.5); QR-only mode recommended for young groups. |

---

## 1. Why these principles

Zangbundel is used at scouting campfires. Many of the people holding a phone around the fire are **under 18**, some well under. That sets the bar: the app must be safe to hand to a child without a parent reading a policy first, and nothing about the child may leak to anyone, the developer included. The project therefore adopts **secure by design** and **privacy by design** as architecture principles (FRD section 2.6). This document records what they mean here and how the design, as it stands before any code, measures up.

The principles are assessed again at every architecture change and before each release (`docs/RUNBOOK.md` release flow); the version table above records each pass.

## 2. The principles, made concrete

### 2.1 Secure by design

| # | Principle | What it means for Zangbundel |
|---|---|---|
| S1 | **Least privilege** | Only the permissions the function needs, no more: Bluetooth, network for library downloads, a foreground service. No camera, no contacts, no install-packages permission, no location use. |
| S2 | **Minimal attack surface** | No server, no accounts, no third-party SDKs that talk to the internet, no code or configuration executed from received data. Every input from the air or from a library is data, bounded and validated. |
| S3 | **Secure defaults** | A fresh install is in its safest state: generic display name, no sharing, nothing persisted beyond what the next gathering needs. |
| S4 | **Verify everything received** | State is signed, documents are hash-bound to the signed state, songs are content-hashed, archives are size-bounded and hash-verified. Nothing is shown or stored until it verifies. |
| S5 | **Fail safe** | When verification fails the app discards and retries; when capacity is exceeded it says so; a lost leader never leaves a singer with a bogus state. |
| S6 | **Defence in depth** | Signed state *and* hash binding *and* sealed personal data *and* input limits, so one broken layer does not expose the next. |
| S7 | **Trustworthy build and supply chain** | Pinned dependencies with verified checksums, Play App Signing, upload key kept out of the repository, reproducible release archives for libraries. |

### 2.2 Privacy by design

Following the seven foundational principles of privacy by design (Cavoukian) and the GDPR notions of data minimisation, purpose limitation and storage limitation:

| # | Principle | What it means for Zangbundel |
|---|---|---|
| P1 | **Proactive, not reactive** | Privacy questions are answered in the design documents before code exists; this assessment is the first pass. |
| P2 | **Privacy as the default** | No data leaves the phone unless the user does something that obviously sends it (joining, requesting a song), and then only what that action needs. Defaults never identify a person. |
| P3 | **Embedded in the design** | End-to-end encryption of personal data is part of the protocol (FR-BLE-17), not a setting. |
| P4 | **Full functionality** | Privacy costs the user nothing: all features work without a name, an account or internet. |
| P5 | **End-to-end lifecycle** | Personal data lives only for a gathering and is deleted when it ends; what survives (titles sung) is not personal. |
| P6 | **Visibility and transparency** | The app says in plain language what goes where; the protocol and this assessment are public. |
| P7 | **Respect for the user** | No dark patterns, no nudging towards sharing more, nothing a child could be tricked into. |
| P8 | **Minors first** | Treat every singer as a possible minor: no public profiles, no singer-to-singer messaging, free text only to an adult leader, no data collection to need consent for, nothing in the store listing that identifies a child. |

## 3. Scope of this assessment

Assessed: FRD v0.5, BLE protocol v0.5, song format v0.2, `docs/RUNBOOK.md` (Google Play preparation), `docs/privacy-policy.md`. Not assessed: code (none exists), the architecture document (not yet written), field behaviour (no measurements yet).

## 4. What the design already does well

| Area | Evidence |
|---|---|
| No developer-side data at all | FR-NET-03, NFR-PRIV-01: no analytics, crash reporting, accounts or servers. The only network peers are libraries the user chose. |
| Personal data on the air is end-to-end encrypted | FR-BLE-17, BLE section 8.4: display name, request text and tokens sealed to the leader; delegate queue sealed to the delegate; broadcast carries nothing personal. |
| Pseudonymous, per-gathering identifiers | BLE section 5: `sid`, `tok`, `xk`, `rid` are new every gathering; the OS randomises Bluetooth addresses. |
| Data deleted when the gathering ends | FR-DATA-01, 06; BLE 10.10, 10.11. |
| Authenticity without pairing or accounts | Ed25519 key per gathering, gathering ID as key fingerprint, signed state, hash-bound documents (BLE 5, 9.4, 13). |
| Untrusted input is bounded | Song files ≤ 64 KiB with line and field limits (song format 5.5, 11); archives with size, ratio, entry and path rules (song format 12.2, 12.8, 12.9); CBOR with required-key checks. |
| Least privilege on Android | RUNBOOK Step 5: no camera, no install-packages, no location use; `neverForLocation` on scanning. |
| Transparent | Privacy policy in both languages; design documents public; licence and attribution shown per song. |
| Minors not messaging each other | Requests go only to the leader and delegates; the leader's message is one-to-many; `reqstat` broadcast carries only random IDs (FR-REQ, BLE 9.5). |
| Build hygiene prepared | RUNBOOK Step 6, `.gitignore` for keystores, reproducible `.zbl` builds. |

## 5. Findings

Severity: **High** = violates a principle in a way a minor could be harmed by or that would falsify the privacy policy; **Medium** = a gap that a reviewer or an incident would expose; **Low** = hygiene or wording.

| ID | Finding | Principle | Severity | Resolution |
|---|---|---|---|---|
| SP-01 | **Default gathering name derived from the device name** (FR-GATH-01 v0.5: "device name plus date"). Device names are usually "Remko's Pixel": the owner's first name broadcast in clear to everyone in range, by default. | P2, S3 | High | FR-GATH-01 amended: default is a neutral name ("Kampvuur" plus date), never the device name; leader UI asks not to put a person's name in it. |
| SP-02 | **Gathering memory meant to store the leader's display name** on the singer side (FR-DATA-03 v0.5), while the protocol never transmits it (BLE 10.11, FRD Q6). A name kept indefinitely on a child's phone for no function. | P2, P5 | Medium | FR-DATA-03 amended: no names in the memory, titles and date only. FRD Q6 closed. |
| SP-03 | **Display name is free text with no guidance.** A minor may type a full name; the field could be pre-filled from the device or account name. | P2, P8 | Medium | FR-SING-01 amended: default generic, never pre-filled from the device or an account, UI asks for a first name or nickname only, 24 characters. |
| SP-04 | **Joining the wrong or a fake gathering.** Anyone in range can advertise a gathering with a familiar name and push any text up to 64 KiB, which singers' phones display. Children would join it in good faith. The join code (FR-GATH-05) helps only when the leader sets one. | S4, P8 | High | **Join QR code** (FR-GATH-19 to 23, BLE 6.5): the code on the leader's screen carries the leader's public key, so a phone that scans it pins the key first and can only join the gathering signed by it; nothing on the air can forge that. In **QR-only mode** (FR-GATH-21) a random join code exists only inside the QR code, so the list cannot be used to join at all; recommended for young groups. For singers who join from the list: FR-GATH-18's two-word gathering fingerprint, a duplicate-name warning, and one-tap leaving (FR-GATH-07). Residual: a leader-pushed text cannot be filtered; the leader is an adult by role. Someone showing their own QR code is a social attack the leader's screen as trust anchor addresses. |
| SP-05 | **Android auto-backup** would copy app data (gathering memories, settings, and the leader's persisted singer table with names and keys) to the user's Google account, which is a transmission off the device the privacy policy denies. | P2, P5, S3 | High | NFR-PRIV-02 added: backup disabled or gathering data excluded from it. |
| SP-06 | **Persisted resume data can outlive a gathering.** FR-GATH-13 keeps key, names and tokens in app-private storage for resume; after a crash without a restart nothing deletes them. | P5 | Medium | FR-DATA-08 added: resume data is deleted when the resume window expires, at the latest at next app start. |
| SP-07 | **No in-app privacy explanation.** The policy lives on a web page; a 12-year-old opening the app sees permission dialogs without context. | P6, P8 | Medium | FR-GEN-08 added: a plain-language privacy screen in the app, in the UI language, reachable before granting any permission. |
| SP-08 | **No rule against components that phone home or logs that leak.** A crash-reporting SDK or a verbose log line could send names off the device and silently falsify the Data safety declaration. | S2, P2 | Medium | NFR-SEC-03 added: no third-party component may transmit data; release builds log no personal data; every dependency's network behaviour is reviewed. |
| SP-09 | **Parsers run on hostile input.** The song parser, CBOR decoder, DEFLATE inflater and ZIP reader all process bytes from the air or from a library the user did not write. Limits exist; robustness is asserted, not tested. | S2, S5 | Medium | NFR-SEC-04 added: parsers are fuzz-tested and run with explicit size and time bounds; a parse failure of a received song is a failed reception, never a crash. |
| SP-10 | **5-byte gathering ID fingerprint** can be forged with about 2^40 work (BLE Q4). | S4 | Low | Accepted for 1.0: singers pin the key at their first verified state, so forgery must precede the join, and the fingerprint of SP-04 exposes a swap to the leader and singers. Q4 stays open. |
| SP-11 | **Join code not covered in the protocol's security table.** Since v0.5 it travels inside the sealed Join, which the table did not say; nor did the table address the fake-gathering case of SP-04. Documentation only. | — | Low | BLE 13 gained a row in v0.6 covering both. |
| SP-12 | **Library download instruction from a leader** names a URL; singers confirm, but the host then sees each singer's IP address. | P6 | Low | Accepted: FR-LEAD-30 already requires confirmation; the prompt shows the host name. Privacy policy already says libraries are the only network peers. |
| SP-13 | **Delegates see requesters' display names** (document 8); a delegate may be a minor. | P8 | Low | Accepted: the leader appoints delegates; names are nicknames by SP-03; the queue is sealed and deleted at the end. Noted in the leader UI. |
| SP-14 | **Store listing photos** of recognisable minors need consent. | P8 | Low | Already in RUNBOOK Step 8. |
| SP-15 | **Supply chain** rules are implied (RUNBOOK Step 6) but not required. | S7 | Low | NFR-SEC-05 added: pinned dependency versions with verified checksums, dependency review per release. |

## 6. Outcome

- **Principles adopted** in FRD section 2.6 (v0.6) and in `CLAUDE.md` as a hard constraint.
- **Four requirements amended** (FR-GATH-01, FR-SING-01, FR-DATA-03, FR-GATH-13 clarified by FR-DATA-08) and **eight added** (FR-GEN-08, FR-GATH-18, FR-DATA-08, NFR-PRIV-02, NFR-SEC-03, 04, 05) in FRD v0.6.
- **Two risks accepted** with reasoning (SP-10, SP-12, SP-13 noted).
- **The privacy policy and the Play Data safety declaration remain true** under the amended design; SP-05 and SP-08 were the two findings that could have falsified them after the fact.

## 7. Next assessment

Run this assessment again, and record it in the version table, when:

1. the architecture document is written (check SP-05, SP-08, SP-09, SP-15 against concrete choices),
2. the first App Bundle is built (check permissions in the manifest against S1, and the backup configuration against NFR-PRIV-02),
3. karaoke (P2) or instrument (P3) features are designed, since each adds data and surface,
4. anything is added that touches personal data, a permission, the network, or bytes received over the air.

Keep the finding IDs; a finding that reopens keeps its number with a new date.
