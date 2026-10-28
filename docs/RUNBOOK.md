# Runbook

<!-- Operational principles + how-to. Loaded via the project file's "Before You Start" table.
     Today this file is about one thing: getting Zangbundel onto Google Play. Local
     development and build sections grow when code lands in android/. -->

Status 2026-10-18 (evening): the Google Play developer account exists (personal, created after 13 November 2023). The app entry **Zangbundel** exists in Play Console (app id 4974489515379733805, package `nl.pe1mew.zangbundel.app`). All nine App content declarations, the privacy policy, the store category and contact details are saved; the default (nl-NL) store listing texts are saved as a draft. Still open before the first upload: store graphics, the en-US listing translation, the upload keystore (Step 6). No app code exists yet; everything that needs an App Bundle waits for the first build.

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
| App signing key | The owner's own key, `zangbundel-appsigning.jks` (alias `zangbundel`), uploaded to Play App Signing at the first release and used for GitHub Releases and F-Droid's reproducible build | Decided 2026-10-20: one signature in every channel (`docs/FDROID-RUNBOOK.md` Step 3). Hard to change after the first upload. |
| Packaging | Android App Bundle (`.aab`), Play App Signing | Mandatory on Play for new apps. The bundle disables splits, so every Play install is one APK the app can share offline (`design/architecture.md` A-18, FRD FR-DIST-05, Q11). GitHub Releases carry an APK built from the release tag and signed with the app signing key, not the App Bundle Explorer APK: only the tag-built APK can be byte-identical to F-Droid's reproducible build (`docs/FDROID-RUNBOOK.md` Steps 3 and 6; fixed 2026-10-22). |
| Target audience | 13 and over; not designed for children | Selecting any under-13 age group puts the app under the Families policy with its own review. Scouts younger than 13 sing from a leader's or parent's phone. Revisit if the owner wants a Families listing. |
| Version scheme | `versionName` semantic (`1.0.0`); `versionCode` strictly increasing integer, `YYYYMMDDnn` (e.g. `2026101801`) | Play refuses a bundle whose `versionCode` is not higher than every previous upload on any track. |
| Play "automatic protection" | **Off** (chosen at app creation, 2026-10-18; changeable under App integrity) | It adds an installer check that makes a copy installed from outside Play refuse to run, which would defeat offline sharing of the app (FRD FR-DIST-05) and the sideloadable APK from GitHub Releases. |
| Public contact e-mail and IARC rating e-mail | `zangbundel@rfsee.nl` | Project address, shown on the listing; the IARC certificate goes there too. |

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

**Done 2026-10-18.** Play Console now asks for the package name at creation; `nl.pe1mew.zangbundel.app` was entered and reported available. The Gradle file must use exactly this `applicationId`, or the first upload is refused. Automatic protection was turned off in the same form (Decisions table).

### Step 3 — Dashboard tasks ("Set up your app")

Do these in Play Console before the first upload; they block release otherwise. Answers that follow from the design documents:

| Task | Answer for Zangbundel | Source |
|---|---|---|
| Privacy policy | `https://pe1mew.github.io/Zangbundel/privacy/` (rendered copy of `docs/privacy-policy.md`, served by Pages). Public, not a PDF. | NFR-PRIV-01, FR-NET-03 |
| App access ("Inloggegevens") | **Answered "Yes, part of the app is restricted"**, because the form counts "actions on another device" and "QR codes" as restrictions, and joining a gathering needs a second phone or the leader's QR code. One instruction set, "Reviewer instructions (no login needed)", no username or password, text: no login, account, payment or code; leader mode works on one phone; joining needs a second phone or the QR code shown on the first; the optional join code is shown on the leader's screen. Update this text when the UI wording changes. | FR-BLE-02, FR-GATH-19 |
| Ads | No ads | |
| Content rating (IARC questionnaire) | **Submitted 2026-10-18**: category "All other app types"; downloaded objectionable content no; users exchange content **yes** (requests to the leader), user content not primary, no public nudity or violence, no block, no report, no chat moderation, interactions restrictable to invited people **yes**; online content **yes** (song libraries) with violence, sexuality, language and drugs all no; no age-restricted goods; no location sharing, purchases, rewards, browser, news. **Result: PEGI 3, ESRB Everyone, USK all ages, ClassInd all ages, IARC 3+, interactive element "users interact".** Re-run the questionnaire when a feature changes any of these answers. | |
| Target audience and content | **Done**: age groups 13–15, 16–17, 18+. Play skipped its further questions for this selection. Avoid child-styled graphics in the listing. | Decision above |
| News app | No | |
| COVID-19 contact tracing / status | No | |
| Data safety | **Done**: no data collected, no data shared; preview shows the privacy policy URL. See Step 4. | NFR-PRIV-01 |
| Advertising ID | **Done**: not used | |
| Government app | **Done**: No | |
| Financial features | **Done**: none | |
| Health | **Done**: no health features | |
| App category and contact details | **Done**: Music & Audio; e-mail `zangbundel@rfsee.nl`; website `https://github.com/pe1mew/Zangbundel`; no phone number. External marketing left at its default. | |
| Store listing | nl-NL texts saved as a **draft** 2026-10-18 (name, short and full description). Still needed: icon, feature graphic, phone screenshots, and the en-US translation (texts in `docs/store-listing/en-US.md`). | |

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

The opt-in campfire statistics (`design/guiDesign.md` D-60, assessment SP-16) were decided on 2026-10-19 as export only: the leader's device keeps them and the leader exports a file by hand, so the declaration is unaffected. If a submission to the developer's server is ever adopted after all, re-evaluate this declaration before the release that contains it. A submission is data transmitted off the device, which is Google's definition of "collect", and the record would be anonymous by design (no name, identifier, pseudonym, location or fine time); the open question is whether such an anonymous, opt-in record falls under any data type of the form at all, or counts as "App activity → App interactions, optional, not shared". Check the data types table of the Play Console help page cited above and the User Data policy's definition of personal and sensitive user data at that time. Export only, as a file through the share sheet, transmits nothing and needs no change.

### Step 5 — Permissions and policy declarations the first bundle will trigger

| Permission / feature | Needed because | Play consequence |
|---|---|---|
| `BLUETOOTH_SCAN` with `neverForLocation`, `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT` (API 31+); `BLUETOOTH`, `BLUETOOTH_ADMIN`, `ACCESS_FINE_LOCATION` with `maxSdkVersion="30"` for API 26–30 | Discovery, broadcast carousel, GATT (BLE protocol sections 6–8) | No declaration form, but the in-app rationale is required (FR-GEN-05). The location permission on old devices needs an explanation in the listing's full description ("used only for Bluetooth scanning on Android 11 and lower; no location is read"). |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_CONNECTED_DEVICE` | Leader keeps advertising with the screen off (FR-GATH-15) | Play Console asks for a **foreground service type declaration**: a description and a short screen-recorded video of the leader flow. Prepare the video with the first upload. |
| `POST_NOTIFICATIONS` (API 33+) | Shows the leader the neutral "campfire running" notification of the foreground service (`design/architecture.md` A-06). Requested only from a leader, once, at the first start of a gathering, after the in-app explanation; never from a singer. The service runs without it. | None; a runtime permission without a declaration form (assessment SP-18). |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Library import and update (FR-LIB-10), setlist import from an address (D-59, FR-NET-01) | None. |
| `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` | Added by AndroidX WorkManager for the opt-in update check on Wi-Fi (FR-LIB-13, architecture A-14, assessment SP-29) | None; normal permissions. |
| Camera | **Not** requested in the baseline: the app shows QR codes (FR-DIST-01, FR-GATH-19); scanning uses the phone's camera app, which opens the join link (FR-GATH-23). If an in-app scanner is added later, `CAMERA` is requested only when the user taps "scan" and declared on Play with that rationale. | None in the baseline. |
| App Links for the join QR code | The join link `https://<project domain>/j#…` must open the app directly (FR-GATH-20, 22). | Host `/.well-known/assetlinks.json` on the project domain listing the app's package name and the **app signing key** SHA-256 (Step 6), plus the static install page. Domain is FRD Q13; until it exists, joining by QR works only with the `zangbundel:` scheme fallback. |
| `nl.pe1mew.zangbundel.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (declared and used) | Added by AndroidX Core for receivers registered at run time as not exported; a signature permission of the app itself, which no other app can hold | None. Found in the merged release manifest by check G-01, 2026-10-27. |
| `REQUEST_INSTALL_PACKAGES` | **Not** requested: sharing the app's own package (FR-DIST-05) goes through the system share sheet; the receiver's file manager performs the install. | Avoids a restricted-permission review. |
| `QUERY_ALL_PACKAGES`, accessibility services, SMS, contacts | Not requested. | |

**Foreground service declaration (prepared 2026-10-27, submitted 2026-10-28).** Play Console → App content → Foreground service permissions, type *Connected device*, task *Continue gegevensoverdracht naar een extern apparaat* (continuous data transfer to an external device; not *Autosleutel*, not *Overig*):

- *Describe the task:* "A campfire leader's phone broadcasts the song lyrics of a sing-along over Bluetooth Low Energy to the phones of the singers around the fire, without internet, and answers their short Bluetooth connections. The foreground service of type connectedDevice keeps this Bluetooth advertising and GATT server running while the leader's screen is off or the app is in the background, for the hours a campfire lasts. It starts when the leader starts a campfire or a singer joins one, and stops when the campfire ends. A notification 'Campfire running' shows for as long as it runs."
- *Impact if deferred or interrupted:* "The broadcast stops: the singers' phones no longer receive the song being sung, and the campfire ends for everyone."
- *Video:* `raw/foreground-service-leader.mp4` (69 s, 10 MB, on the owner's machine only; `raw/` is not in git). The leader starts a campfire, pushes the demo song *Lorem Ipsum!*, goes to the home screen and opens the notification shade, which shows *Kampvuur actief*; meanwhile two singers join; back in the app the leader's page shows them. Recorded on the A52 with Do Not Disturb on, so the shade shows nothing personal. Play asks for a link: upload it unlisted (YouTube or a shared drive) and paste the link.

### Step 5b — GitHub Pages for the join link (option 1, do now)

The join QR code (FRD FR-GATH-19 to 23, BLE section 6.5) encodes `https://pe1mew.github.io/Zangbundel/j/#<payload>` (trailing slash; without it Pages redirects first). The page lives in this repository at `docs/j/index.html`; `docs/.nojekyll` makes GitHub serve the folder as is.

- [x] Pages enabled 2026-10-18 via the GitHub API: source branch `main`, folder `/docs`, HTTPS enforced. Live at `https://pe1mew.github.io/Zangbundel/`; every push to `main` redeploys within a minute or two.
- [x] Opened `https://pe1mew.github.io/Zangbundel/j/` with and without a test fragment on 2026-10-18: install page, join page with gathering name, error page for a corrupt payload; no external resources loaded.
- [ ] Repeat the three checks on a phone's browser after the first app build exists, and tap "Open in Zangbundel". Open it with a test fragment (section 6.5 of the protocol gives the payload; `#` followed by base64url): the join page appears with the gathering name and an "Open in Zangbundel" button.
- [ ] The page loads no external resource and sends nothing: check the browser's network panel shows only the page itself.
- [ ] Enforce HTTPS is on in the Pages settings (default).

With only this step, Android 12 and later open the link in the browser first; the page's button launches the app through an `intent://` link, or Play when the app is missing. One extra tap, no verification needed.

### Step 5c — Prepare App Links verification (option 2, publish after the first upload)

Verified App Links make the camera app open Zangbundel directly, skipping the browser. Android verifies by fetching `https://pe1mew.github.io/.well-known/assetlinks.json`, the **host root**, which GitHub Pages serves only from a repository named `pe1mew.github.io`. Preparation that needs no code is done:

- [x] `docs/app-links/assetlinks.json` is the file to publish (2026-10-27): the release package with the app signing key's SHA-256, known since option B, so it need not wait for the first upload. Test builds carry other package names and are not listed; `assetlinks.template.json` stays as the pattern.
- [ ] `docs/app-links/intent-filters.xml` holds the two intent filters (verified `https` form, `zangbundel:` scheme form) for the Android manifest.

When the app has been uploaded once (Step 6 records the app signing key SHA-256). **Done 2026-10-27**, steps 1 to 4 (`design/test-results/2026-10-27-internal-test-0.1.0.md`, D-04 direct route):

1. Create the repository `pe1mew/pe1mew.github.io` with Pages enabled on `main`, containing `.nojekyll` **at the repository root** and `.well-known/assetlinks.json` copied from `docs/app-links/assetlinks.json`. A `.nojekyll` anywhere else leaves Jekyll on, and Jekyll drops the `.well-known` folder. An `index.html` that redirects to `https://pe1mew.github.io/Zangbundel/` is optional.
2. Confirm `curl -s https://pe1mew.github.io/.well-known/assetlinks.json` returns the JSON with `Content-Type: application/json`.
3. Install a build that carries the `autoVerify` filter and check on the phone: `adb shell pm get-app-links nl.pe1mew.zangbundel.app` must show `pe1mew.github.io: verified`. Verification runs at install time; on a phone that installed the app before the file was published, `adb shell pm verify-app-links --re-verify nl.pe1mew.zangbundel.app` repeats it without a reinstall.
4. Scan a join code with the camera app: Zangbundel opens without a browser step.

Nothing in the QR code or in the page changes between option 1 and option 2.

### Step 6 — Signing

Play App Signing is mandatory, and Zangbundel uses it with **the owner's own app signing key** (decided 2026-10-20, `docs/FDROID-RUNBOOK.md` Step 3): one signature for Play, F-Droid, GitHub Releases and offline sharing, so a copy from any channel updates a copy from any other. Two keystores, both outside every repository:

- **App signing key: done 2026-10-20.** `zangbundel-appsigning.jks`, alias `zangbundel`, a key of its own (not shared with another app). It signs the APKs on GitHub Releases and is handed to Google once, encrypted, at the first upload. Certificate SHA-256: `57:86:7B:3F:C1:4D:1F:35:F6:38:40:A8:41:BB:05:F6:DD:FD:A9:C3:6D:C0:58:29:5F:87:F5:46:A2:C8:9B:10` (details in `docs/FDROID-RUNBOOK.md` Step 3).
- **Upload key: done 2026-10-20.** `zangbundel-upload.jks`, alias `upload`, what you sign each bundle with before uploading; registered with Play at the first upload. Certificate SHA-256: `C0:A3:C6:52:A5:93:B7:E0:CE:41:EB:21:97:F2:A7:6C:00:14:BD:64:31:D1:EB:86:7F:AA:21:70:8C:95:17:2A` (RSA 4096, SHA384withRSA, valid 2026-10-20 to 2056-10-12). Created with:

```bash
keytool -genkeypair -v -keystore zangbundel-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10950 -storetype PKCS12 -dname "CN=Remko Welling, O=PE1MEW, C=NL"
```

- [x] App signing keystore created, outside every repository (verified 2026-10-20: the file exists in the owner's secrets folder, which is not a git repository; no keystore in either Zangbundel repository).
- [x] App signing keystore and its passwords are in the password manager and on an offline backup (confirmed by the owner 2026-10-20). Losing it ends updates in every channel; there is no reset for it.
- [x] Upload keystore created, outside every repository (verified 2026-10-20: `zangbundel-upload.jks` exists next to the app signing keystore in the owner's secrets folder; certificate read by the owner and recorded above).
- [x] Upload keystore and its password are in the password manager and on the offline backup (confirmed by the owner 2026-10-20). Losing the upload key means a key-reset request through Play support with a new key.
- [ ] Create `keystore.properties` next to the Android project (ignored by git) with `storeFile`, `storePassword`, `keyAlias`, `keyPassword` for the upload key; the Gradle signing config reads it and fails loudly when absent.
- [x] **App signing set to the owner's key, 2026-10-27, before any upload.** Play had generated its own app signing key when the app was created on 2026-10-18 (fingerprints `94:DF:…`, `40:B7:…`, `7B:5C:…`), so the choice was no longer offered at the first upload. The route in Play Console as of 2026-10-27: *Beveiligd met Play* (the old *App-integriteit* page points there) → *Play Store-beveiliging* → app signing → **Sleutel wijzigen** → confirm (with no release yet this replaces the key; it is not an upgrade) → **Een sleutel exporteren en uploaden via Java-keystore**. Download the PEPK tool and the encryption key there, then run `java -jar pepk.jar --keystore=<…>\zangbundel-appsigning.jks --alias=zangbundel --output=<…>\encrypted_private_key.zip --include-cert --rsa-aes-encryption --encryption-key-path=<…>\encryption_public_key.pem` **with Java 11 or newer** (the `java` on the PATH was Java 8 and failed with class file version 55; use JDK 17 by its full path). Upload the ZIP, upload the upload certificate (`keytool -export -rfc -keystore zangbundel-upload.jks -alias upload -file upload_certificate.pem`, SHA-256 `C0:A3:C6:…:95:17:2A`), save. Verified: the *Digital Asset Links JSON* on that page shows `57:86:7B:3F:…:A2:C8:9B:10`, the same as `docs/app-links/assetlinks.json`. The encrypted ZIP may be deleted; the keystore stays the original.

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

Texts are ready in `docs/store-listing/nl-NL.md` and `docs/store-listing/en-US.md` (app name, short and full description, release-notes template); the nl-NL texts are entered in Play Console as a draft (2026-10-18). To add English: Store listing → language selector → add en-US and paste from the en-US file. Graphics go in a non-committed folder or in Git LFS. Required assets:

| Asset | Spec |
|---|---|
| App icon | **Uploaded 2026-10-22:** `docs/store-listing/graphics/icon-512.png` (512 × 512, 32-bit PNG, full square; Play applies its own mask), rendered from the master `design/icon/zangbundel-icon.svg`, chosen from proposal 06 "Vuurboek in de kring" on 2026-10-22 (replacing proposal 02 of 2026-10-18). Upload under Store listing → App icon. The adaptive launcher layers for the Android project are `design/icon/ic_launcher_foreground.svg` and `ic_launcher_background.svg` (Android Studio → Image Asset). |
| Feature graphic | **Uploaded 2026-10-22** (replacing the earlier upload with the 02 artwork): `docs/store-listing/graphics/feature-1024x500.png`, rendered from `design/icon/feature-graphic.svg` (icon artwork, name, Dutch tagline; no photo, so no consent question). Upload under Store listing → Feature graphic. A photo variant stays possible later; get consent from recognisable people, especially minors, first. |
| Phone screenshots | 2 to 8; 16:9 or 9:16; each side 320–3840 px; actual screenshots of the app, night theme included. **Made 2026-10-27, not uploaded:** `docs/store-listing/graphics/phone-1-leader.png` (the leader's page), `phone-2-singer.png` (a singer's page), `phone-3-drawer.png` (the leader's drawer with the live figures), `phone-4-install-qr.png` (the install panel; its QR code is the plain install address), `phone-5-songbook.png` (the songbook in the leader's sheet), Dutch UI, night theme, on the A52 and S23. The phones are 20:9, so each screenshot is scaled unchanged to 1920 px high and centred on a 1080 × 1920 canvas in the app's night blue `#0B2545`. Only the library's demo song *Lorem Ipsum!* is on screen: the repository holds no lyrics (CLAUDE.md), and other songs appear by title only. The join QR code was left out: from a test build it carries the test marker `?app=fieldtest`. Upload under Store listing → Phone screenshots for nl-NL; for en-US the same files serve until English ones are taken |
| 7-inch and 10-inch tablet screenshots | Recommended since tablets are supported (FR 2.4) |
| Short description | ≤ 80 characters, nl-NL and en-US |
| Full description | ≤ 4000 characters, nl-NL and en-US; include the Bluetooth and old-Android location sentence from Step 5 |
| Release notes | ≤ 500 characters per language per release |

The photos in `raw/` (untracked, 2–5 MB each) can serve the feature graphic after cropping; they are not screenshots and cannot go in the screenshot slots.

### Step 9 — Release flow (once code exists)

1. Bump `versionName` and `versionCode`; add the release to `changelog.md` under a dated heading, and its release note, headed `### <versionName> (<versionCode>)`, to `docs/store-listing/nl-NL.md` and `en-US.md`; then `bash scripts/store-metadata.sh` for F-Droid's copy.
2. Run the checks (`bash scripts/check-design.sh` for the design; the Android unit tests once they exist).
3. Build the release bundle: `./gradlew bundleRelease` with `keystore.properties` present. Never upload a debuggable build.
4. Upload to **Internal testing**; wait for the pre-launch report; install from the Play link on two phones and run a gathering between them.
5. Promote the same bundle to **Closed testing**, or in the closed-test draft add it with *Toevoegen uit bibliotheek* (a draft without a bundle gives "geen app-bundels toegevoegd"); write release notes in nl and en. First done 2026-10-28: 0.1.1 (2026102701) on *Gesloten test – Alpha*, submitted for review.
6. After the 14 days and production access: promote to **Production** with a staged rollout; watch Android vitals (crashes, ANRs) for a week before 100 %.
7. Build the APK from the release tag `v<versionName>` and sign it with the **app signing key**, not the upload key that `keystore.properties` names for the bundle: the commands are in `docs/FDROID-RUNBOOK.md` Step 6 (a fresh clone of the tag, `assembleRelease`, compared with CI's build, signed with apksigner 34, v2 and v3 only). `apksigner verify --print-certs` must show `57:86:7B:…:9B:10` (check G-08). Name it `zangbundel-<versionName>.apk` and attach it to the GitHub release of that tag, together with the production release. This is the file F-Droid's `Binaries` URL points at (`docs/FDROID-RUNBOOK.md` Steps 6 and 7); do not use the App Bundle Explorer APK, which cannot be byte-identical to F-Droid's build.

### Post-deploy verification

- [ ] Install from the Play Store link on a phone that was not used for development; open the app; start a gathering; join it from a second phone.
- [ ] Check the pre-launch report for crashes on the device matrix and for the permission rationale screens.
- [ ] Confirm the signing certificate of the installed app equals the recorded app signing key SHA-256.

## Common problems

| Symptom | Likely cause | Fix |
|---|---|---|
| Upload refused: "version code already used" | `versionCode` not higher than a previous upload on any track, including deleted releases | Bump `versionCode`; never reuse |
| Warning: "versiecode … aanzienlijk hoger dan je vorige versiecode" | The `YYYYMMDDnn` scheme (Decisions) looks large to Play | *Toch doorgaan*: the scheme's highest code, 2099123199, stays under Play's limit of 2 100 000 000 |
| Warning: "App Bundle bevat native code en je hebt geen foutopsporingssymbolen geüpload" | The only native code is the prebuilt libsodium, JNA and DataStore libraries from Maven Central, which ship without symbol files; the app compiles no native code itself | Ignore and continue: there is nothing to upload, and crashes in the app's own Kotlin code are readable without it (first seen 2026-10-28, 0.2.0) |
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
