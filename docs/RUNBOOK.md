# Runbook

<!-- Operational principles + how-to. Loaded via the project file's "Before You Start" table.
     Today this file is about one thing: getting Zangbundel onto Google Play. Local
     development and build sections grow when code lands in android/. -->

Status 2026-10-18: the Google Play developer account exists (personal, created after 13 November 2023). No app code exists yet. Everything below that needs an App Bundle waits for the first build; everything else can be done now.

## Principles

- **Irreversible Play settings are set once; decide them in this file first.** Application ID, free-or-paid, and the developer name shown on the listing cannot be changed after the first upload. They are recorded under Decisions below; do not type them into Play Console from memory.
- **Keys never enter the repository.** The upload keystore, its passwords and `keystore.properties` live outside the tree and in a password manager. `.gitignore` enforces it; a review of any `.gitignore` change is full depth for this reason.
- **Target the required API level before the deadline, not after.** Google raises the bar every August; a new app that targets less is refused at upload, not warned.
- **The 14-day closed test is a calendar constraint, not a formality.** Plan the first upload at least a month before the day you want the app in production.
- **Secure by design, privacy by design, because users are under 18.** Before each release, re-run `design/security-privacy-assessment.md` and record the pass in its version table; a release that adds a permission, a network peer or a third-party component without an assessment entry is not ready.
- **The store listing describes the app that exists.** Screenshots must be screenshots of the app; a feature graphic may be a campfire photo. Nothing in the listing promises phase 2 or 3.

## Decisions (fixed)

| Decision | Value | Why |
|---|---|---|
| Application ID | `nl.pe1mew.zangbundel.app` | Chosen 2026-10-18 by the owner. Permanent after first upload. |
| App name on Play | Zangbundel | Check availability when creating the app; a taken name needs a suffix such as "Zangbundel – kampvuur". |
| Default store language | Dutch (nl-NL); English (en-US) as second listing | FR-LANG-01 |
| Category | Music & Audio | |
| Pricing | Free, no ads, no in-app purchases | Free is irreversible on Play. |
| Distribution | All countries, Android phones and tablets | Nothing in the app is region-bound. |
| Minimum SDK | 26 (Android 8.0) | `startAdvertisingSet` (multiple advertising sets) is API 26; the BLE protocol's broadcast carousel needs it on leaders. Singers on older devices are not supported. |
| Target SDK | 36 (Android 16) | Required for new apps from 31 August 2026. |
| Packaging | Android App Bundle (`.aab`), Play App Signing | Mandatory on Play for new apps. The Play-signed universal APK from the App Bundle Explorer is what goes on GitHub Releases for offline sharing (FRD FR-DIST-05, Q11). |
| Target audience | 13 and over; not designed for children | Selecting any under-13 age group puts the app under the Families policy with its own review. Scouts younger than 13 sing from a leader's or parent's phone. Revisit if the owner wants a Families listing. |
| Version scheme | `versionName` semantic (`1.0.0`); `versionCode` strictly increasing integer, `YYYYMMDDnn` (e.g. `2026101801`) | Play refuses a bundle whose `versionCode` is not higher than every previous upload on any track. |

## Google Play setup

### Step 1 — Developer account (done; verify)

In Play Console → Settings → Developer account:

- [ ] Identity verification completed (no "verify" banner on the dashboard).
- [ ] Developer name as it should appear on the listing (e.g. "Remko Welling (PE1MEW)").
- [ ] Contact e-mail address that may be shown publicly.
- [ ] No payments profile needed for a free app; do not create one unless paid apps follow.

### Step 2 — Create the app (can be done now)

Play Console → All apps → Create app:

| Field | Value |
|---|---|
| App name | Zangbundel |
| Default language | Dutch (Netherlands) – nl-NL |
| App or game | App |
| Free or paid | Free |
| Declarations | Developer Program Policies: agree. US export laws: agree. |

The application ID is **not** entered here; Play takes it from the first uploaded bundle. That bundle fixes it forever, so the Gradle file must say `applicationId = "nl.pe1mew.zangbundel.app"` before anything is uploaded.

### Step 3 — Dashboard tasks ("Set up your app")

Do these in Play Console before the first upload; they block release otherwise. Answers that follow from the design documents:

| Task | Answer for Zangbundel | Source |
|---|---|---|
| Privacy policy | URL of `docs/privacy-policy.md` rendered on GitHub, or a GitHub Pages page. Must be public and not a PDF. | NFR-PRIV-01, FR-NET-03 |
| App access | All functionality is available without special access (no login, no join code needed to review). Mention that BLE features need two phones. | FR-BLE-02 |
| Ads | No ads | |
| Content rating (IARC questionnaire) | Category: Utility, Productivity, Communication or Other. Answer no to violence, sexual content, language, controlled substances, gambling, user interaction (see note), location sharing, purchases. Expected rating: Everyone / PEGI 3. | |
| Target audience and content | Age groups 13–15, 16–17, 18+. "Could the app unintentionally appeal to children?" answer honestly; avoid child-styled graphics in the listing. | Decision above |
| News app | No | |
| COVID-19 contact tracing / status | No | |
| Data safety | See the Data safety section below. | NFR-PRIV-01 |
| Government app | No | |
| Financial features | None | |
| Health | No health features | |
| App category and contact details | Music & Audio; e-mail; website = repository URL | |
| Store listing | See Store listing below | |

**User interaction note.** The content rating questionnaire asks whether users can interact or share content. Singers send song requests (free text up to 100 characters) and a display name to the leader over Bluetooth within a few metres. Answer "yes, users can interact" and "no, content is not shared with the developer or beyond nearby devices". This may raise the rating slightly; it is still accurate.

### Step 4 — Data safety declaration

Nothing ever reaches the developer (FR-NET-03, NFR-PRIV-01), but that is not the test the form applies. Google's definitions (Play Console help, "Provide information for Google Play's Data safety section", read 2026-10-18):

- **Collect** "means transmitting data from your app off a user's device." Data "only processed locally on the user's device and not sent off device" need not be disclosed. Data is also exempt when end-to-end encrypted so that it is "unreadable by you or anyone other than the sender and recipient". Pseudonymous data must be disclosed. Data "only stored in memory" counts as ephemeral: it must be entered in the form but is not shown on the listing.
- **Share** "refers to transferring user data collected from your app to a third party", and a third party is "any organization other than the first party or its service providers". Transfers "based on a specific user-initiated action, where the user reasonably expects the data to be shared" are exempt.
- **Personal info → Name** is "how a user refers to themselves, such as their first or last name, or nickname".
- A privacy policy link is required even when nothing is collected.

Applied to the BLE protocol as designed (v0.5, with sealed payloads):

| Data | Leaves the device? | Exempt? | Literal reading |
|---|---|---|---|
| Display name (Join request; document 8 to delegates) | Yes, over GATT, **sealed end-to-end to the leader's key**; document 8 sealed to the delegate's key (protocol section 8.4, FR-BLE-17) | Yes: end-to-end encrypted, readable only by sender and recipient | Not collected |
| Song requests (free text up to 100 characters) | Yes, sealed to the leader | Yes, as above | Not collected |
| Gathering name chosen by the leader | Yes, broadcast in the beacon | No | Arguably App activity → Other user-generated content; a title such as "Zomerkamp vrijdag" is not about a person. Not declared; the UI hint asks leaders not to put people's names in it. |
| Per-gathering random IDs and tokens (`sid`, `tok`, `rid`) | Yes | New for every gathering, linked to nothing | Not an identifier "that relates to an individual device, browser or app"; not declared. |
| Lyrics, setlist, state, hashes | Yes, broadcast | Not user data | Not declared. |
| Anything to the developer or a third party | **No** | | **Shared: none.** Other singers are users, not an "organization", and every transfer is a user-initiated action the user expects. |

**Declaration: no data collected, no data shared.** Every item in the table is on-device only, end-to-end encrypted, or not user data. This became true by design on 2026-10-18, when the protocol adopted sealed payloads (BLE protocol section 8.4, FRD FR-BLE-17): the display name, request text and session tokens are encrypted to the leader's per-gathering key, the delegate's request queue to the delegate's key, and the broadcast carousel carries nothing personal. Before that, the honest declaration would have been "Name and In-app messages collected", which the listing would have shown to every parent even though nothing leaves the fire.

Two conditions keep the declaration true, and both are review items for the first bundle:

- The implementation must seal exactly what section 8.4 says. A debug build that writes Join in plaintext, or a log line that prints display names to a server-bound crash reporter, breaks the declaration.
- `docs/privacy-policy.md` says the same thing in user language (name and requests encrypted so only the leader's phone can read them; lyrics and state broadcast unencrypted because they are not personal). Change both together, never one.

If an opt-in crash reporter is ever added, re-declare "Crash logs, optional, not shared" and add the service to the privacy policy.

### Step 5 — Permissions and policy declarations the first bundle will trigger

| Permission / feature | Needed because | Play consequence |
|---|---|---|
| `BLUETOOTH_SCAN` with `neverForLocation`, `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT` (API 31+); `BLUETOOTH`, `BLUETOOTH_ADMIN`, `ACCESS_FINE_LOCATION` with `maxSdkVersion="30"` for API 26–30 | Discovery, broadcast carousel, GATT (BLE protocol sections 6–8) | No declaration form, but the in-app rationale is required (FR-GEN-05). The location permission on old devices needs an explanation in the listing's full description ("used only for Bluetooth scanning on Android 11 and lower; no location is read"). |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_CONNECTED_DEVICE` | Leader keeps advertising with the screen off (FR-GATH-15) | Play Console asks for a **foreground service type declaration**: a description and a short screen-recorded video of the leader flow. Prepare the video with the first upload. |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Library import (FR-LIB-10) | None. |
| Camera | **Not** requested in the baseline: the app shows QR codes (FR-DIST-01, FR-GATH-19); scanning uses the phone's camera app, which opens the join link (FR-GATH-23). If an in-app scanner is added later, `CAMERA` is requested only when the user taps "scan" and declared on Play with that rationale. | None in the baseline. |
| App Links for the join QR code | The join link `https://<project domain>/j#…` must open the app directly (FR-GATH-20, 22). | Host `/.well-known/assetlinks.json` on the project domain listing the app's package name and the **app signing key** SHA-256 (Step 6), plus the static install page. Domain is FRD Q13; until it exists, joining by QR works only with the `zangbundel:` scheme fallback. |
| `REQUEST_INSTALL_PACKAGES` | **Not** requested: sharing the app's own package (FR-DIST-05) goes through the system share sheet; the receiver's file manager performs the install. | Avoids a restricted-permission review. |
| `QUERY_ALL_PACKAGES`, accessibility services, SMS, contacts | Not requested. | |

### Step 5b — GitHub Pages for the join link (option 1, do now)

The join QR code (FRD FR-GATH-19 to 23, BLE section 6.5) encodes `https://pe1mew.github.io/Zangbundel/j#<payload>`. The page lives in this repository at `docs/j/index.html`; `docs/.nojekyll` makes GitHub serve the folder as is.

- [ ] Repository → Settings → Pages → Source "Deploy from a branch", branch `main`, folder `/docs`. Save; wait for the first deploy (a minute).
- [ ] Open `https://pe1mew.github.io/Zangbundel/j` on a phone: the install page appears. Open it with a test fragment (section 6.5 of the protocol gives the payload; `#` followed by base64url): the join page appears with the gathering name and an "Open in Zangbundel" button.
- [ ] The page loads no external resource and sends nothing: check the browser's network panel shows only the page itself.
- [ ] Enforce HTTPS is on in the Pages settings (default).

With only this step, Android 12 and later open the link in the browser first; the page's button launches the app through an `intent://` link, or Play when the app is missing. One extra tap, no verification needed.

### Step 5c — Prepare App Links verification (option 2, publish after the first upload)

Verified App Links make the camera app open Zangbundel directly, skipping the browser. Android verifies by fetching `https://pe1mew.github.io/.well-known/assetlinks.json`, the **host root**, which GitHub Pages serves only from a repository named `pe1mew.github.io`. Preparation that needs no code is done:

- [ ] `docs/app-links/assetlinks.template.json` holds the file to publish, with the package name filled in and the fingerprints as placeholders.
- [ ] `docs/app-links/intent-filters.xml` holds the two intent filters (verified `https` form, `zangbundel:` scheme form) for the Android manifest.

When the app has been uploaded once (Step 6 records the app signing key SHA-256):

1. Create the repository `pe1mew/pe1mew.github.io` with Pages enabled on `main`, containing `.nojekyll` and `.well-known/assetlinks.json` copied from the template with the real fingerprint(s). An `index.html` that redirects to `https://pe1mew.github.io/Zangbundel/` is optional.
2. Confirm `curl -s https://pe1mew.github.io/.well-known/assetlinks.json` returns the JSON with `Content-Type: application/json`.
3. Install a build that carries the `autoVerify` filter and check on the phone: `adb shell pm get-app-links nl.pe1mew.zangbundel.app` must show `pe1mew.github.io: verified`. Verification runs at install time; reinstall after publishing the file.
4. Scan a join code with the camera app: Zangbundel opens without a browser step.

Nothing in the QR code or in the page changes between option 1 and option 2.

### Step 6 — Signing

Play App Signing is mandatory: Google holds the app signing key; you hold an **upload key**. Generate it once, outside the repository:

```bash
keytool -genkeypair -v -keystore zangbundel-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10950 -storetype PKCS12 -dname "CN=Remko Welling, O=PE1MEW, C=NL"
```

- [ ] Store `zangbundel-upload.jks` and both passwords in the password manager and on one offline backup. Losing the upload key means a key-reset request through Play support with a new key; losing the Play-held signing key is not possible.
- [ ] Create `keystore.properties` next to the Android project (ignored by git) with `storeFile`, `storePassword`, `keyAlias`, `keyPassword`; the Gradle signing config reads it and fails loudly when absent.
- [ ] On the first upload, Play Console → Setup → App signing: choose "Google-generated app signing key" and let the console register the upload certificate from the bundle. Record the **app signing key SHA-256** from that page in this file afterwards: it is the signature every installed copy carries, including the Play-signed universal APK used for offline sharing (FRD FR-DIST-05).

### Step 7 — Tracks and the closed-testing rule

The account is personal and was created after 13 November 2023, so Play requires a closed test with **at least 12 opted-in testers for 14 consecutive days** before production access can be requested. The count must never dip below 12 or the 14 days start again.

| Track | Use | Who |
|---|---|---|
| Internal testing | Every build first; up to 100 testers by e-mail list; available minutes after upload | Owner and one or two leaders |
| Closed testing, track "Scouting testers" | The 14-day run; also the real field test for BLE protocol questions Q2 and Q3 | Recruit 15–20 people (leaders, parents, a second group) so dropouts do not reset the clock. They must opt in through the test link and keep the app installed. |
| Open testing | Not needed | |
| Production | After "Apply for production access" is approved (questionnaire about recruitment and feedback) | Staged rollout 20 % → 50 % → 100 % |

Both test tracks still require the dashboard tasks of Step 3 to be complete.

### Step 8 — Store listing

Prepare in `docs/store-listing/` (text) and a non-committed folder for graphics, or commit graphics with Git LFS. Required assets:

| Asset | Spec |
|---|---|
| App icon | 512 × 512 px, 32-bit PNG, no alpha; same artwork as the adaptive launcher icon |
| Feature graphic | 1024 × 500 px, JPEG or 24-bit PNG; a campfire photo works. Get consent from recognisable people, especially minors, before using a group photo. |
| Phone screenshots | 2 to 8; 16:9 or 9:16; each side 320–3840 px; actual screenshots of the app, night theme included |
| 7-inch and 10-inch tablet screenshots | Recommended since tablets are supported (FR 2.4) |
| Short description | ≤ 80 characters, nl-NL and en-US |
| Full description | ≤ 4000 characters, nl-NL and en-US; include the Bluetooth and old-Android location sentence from Step 5 |
| Release notes | ≤ 500 characters per language per release |

The photos in `raw/` (untracked, 2–5 MB each) can serve the feature graphic after cropping; they are not screenshots and cannot go in the screenshot slots.

### Step 9 — Release flow (once code exists)

1. Bump `versionName` and `versionCode`; add the release to `changelog.md` under a dated heading.
2. Run the checks (`bash scripts/check-design.sh` for the design; the Android unit tests once they exist).
3. Build the release bundle: `./gradlew bundleRelease` with `keystore.properties` present. Never upload a debuggable build.
4. Upload to **Internal testing**; wait for the pre-launch report; install from the Play link on two phones and run a gathering between them.
5. Promote the same bundle to **Closed testing**; write release notes in nl and en.
6. After the 14 days and production access: promote to **Production** with a staged rollout; watch Android vitals (crashes, ANRs) for a week before 100 %.
7. Download the signed universal APK from App Bundle Explorer and attach it to the GitHub release with the same version, for offline sharing.

### Post-deploy verification

- [ ] Install from the Play Store link on a phone that was not used for development; open the app; start a gathering; join it from a second phone.
- [ ] Check the pre-launch report for crashes on the device matrix and for the permission rationale screens.
- [ ] Confirm the signing certificate of the installed app equals the recorded app signing key SHA-256.

## Common problems

| Symptom | Likely cause | Fix |
|---|---|---|
| Upload refused: "version code already used" | `versionCode` not higher than a previous upload on any track, including deleted releases | Bump `versionCode`; never reuse |
| Upload refused: target API too low | `targetSdk` below the current requirement (36 from 31 August 2026) | Raise `targetSdk`, rebuild |
| Upload refused: wrong signing key | Bundle signed with a key other than the registered upload key | Use the registered keystore; if lost, request an upload key reset |
| Release blocked by a red task on the dashboard | Data safety, content rating, foreground service declaration or privacy policy incomplete | Complete Step 3 and Step 5 items |
| Closed test counter restarted | Opted-in testers dropped below 12 | Recruit more than 12 from the start; message testers before day 14 |
| "App not available in your country" on a tester's phone | Countries not selected for the track | Production → Countries/regions; test tracks inherit |
| Leader's beacon stops when the screen is off on a tester's phone | Foreground service not started or battery saver | FR-GATH-15 warning in the app; tester disables battery optimisation for the app |

## Documentation practices

| Type of change | Update |
|---|---|
| New constraint or principle | `CLAUDE.md` |
| Play setting, track or release step changed | This file |
| Hit a weird bug | `memory/gotcha-log.md` |
| Chose between approaches | Decisions table above, or an ADR once `docs/adr/` exists |
| Learned something non-obvious | `memory/MEMORY.md` or a topic file |

## Sources (2026-10-18)

- Target API level requirement: https://developer.android.com/google/play/requirements/target-sdk and https://support.google.com/googleplay/android-developer/answer/11926878
- Closed-testing rule for personal accounts created after 13 November 2023 (12 testers, 14 days; third-party summaries, verify against Play Console help): https://extendsclass.com/blog/google-plays-closed-testing-requirement-what-developers-need-to-know-in-2026 and https://www.testerscommunity.com/guides/how-many-testers-do-you-need-google-play
