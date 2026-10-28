# Merge request to fdroiddata (FDROID-RUNBOOK Step 9)

What the owner submits to `https://gitlab.com/fdroid/fdroiddata`. Prepared 2026-10-28 for 0.2.0, filled into fdroiddata's template for a new app. Submitted as !54679 and closed by a maintainer the same evening on the inclusion policy's Quality point 6, 3 months of development (FDROID-RUNBOOK Step 9). For the resubmission from 2027-01-18, update the version, the commit and the tag in the screenshot links, and link the `fastlane/metadata/android/` folder at the tag, since the reviewer did not find it.

**The file.** `metadata/nl.pe1mew.zangbundel.app.yml` is the copy in this folder **without its comment lines** (`grep -v '^ *#'`): fdroiddata's CI requires the file exactly as `fdroid rewritemeta` writes it, and rewritemeta removes comments and changes nothing else (checked in the container test of 2026-10-28). **Upload that file, do not paste it** into GitLab's editor: on 2026-10-28 pasting from Windows gave CRLF line endings and a block twice, and fdroiddata's pipeline failed on both (*duplication of key*, LF required). Make it with `grep -v '^ *#' docs/fdroid/nl.pe1mew.zangbundel.app.yml | tr -d '\r' > nl.pe1mew.zangbundel.app.yml` and use GitLab's *Upload file* or *Replace*.

**The branch.** Not the fork's `master`, which GitLab protects in a fork: a branch `nl.pe1mew.zangbundel.app`, unprotected, in a public fork, so maintainers can push fixes.

**The title.** `New app: Zangbundel` (the template's *New app: app name*).

**The description** (paste in place of the template):

---

## Abstract for new app inclusion

### Please introduce the functions and usage of your app in summary, and also attach some screenshots.

Zangbundel ("songbook" in Dutch) is a songbook for singing together around a campfire, made for scouting groups. One phone is the campfire leader: it chooses the songs and sends the lyrics over Bluetooth Low Energy to every phone nearby. The others sing along and see each song as the leader sends it. During the campfire there is no internet, no Wi-Fi and no Bluetooth pairing; singers join from a list of campfires nearby or by scanning the leader's QR code.

The app contains no lyrics. Songs come from song libraries the leader adds, each with its own licence; the default library is https://github.com/pe1mew/Zangbundel-bibliotheek. Leaders prepare setlists, and after each campfire the list of songs sung is kept as a memory. Dutch and English, with German in review.

![Home](https://raw.githubusercontent.com/pe1mew/Zangbundel/v0.2.0/fastlane/metadata/android/en-US/images/phoneScreenshots/1.png)
![Screenshot 2](https://raw.githubusercontent.com/pe1mew/Zangbundel/v0.2.0/fastlane/metadata/android/en-US/images/phoneScreenshots/2.png)
![Screenshot 3](https://raw.githubusercontent.com/pe1mew/Zangbundel/v0.2.0/fastlane/metadata/android/en-US/images/phoneScreenshots/3.png)

### Please elaborate why you wrote this app. Please highlight the unique functions it provides over other similar apps in F-Droid.

Scouting groups sing from printed songbooks that are hard to read in the dark and go out of date. Zangbundel puts the current song on every phone around the fire, readable in the dark, without depending on a network at a campsite. What it adds: the leader's phone broadcasts to any number of singers over BLE alone (a broadcast carousel with repair over short GATT connections); every song is signed by the leader's key, so no one else can put text into a campfire; the app is made for users under 18, so it sends nothing to its makers, keeps no names and needs no account (privacy policy: https://pe1mew.github.io/Zangbundel/privacy/); and lyrics stay outside the app, in libraries with their own licences.

## Checklist

### Policy

* [x] The app complies with the [inclusion policy](https://f-droid.org/docs/Inclusion_Policy). _Pay special attention to Quality point 6._ Code Apache-2.0, graphics CC BY-SA 4.0; until 2026-10-20 the code also carried the Commons Clause, removed so the app is free software; every tag is later (`license.md`, history paragraph). All dependencies come from Maven Central and Google's Maven repository; no Google Play Services, Firebase or other proprietary library.
* [x] The original app author has been notified (and does not oppose the inclusion). I am the author.
* [x] The upstream app source code repo contains the app metadata in a [Fastlane](https://gitlab.com/snippets/1895688) or [Triple-T](https://gitlab.com/snippets/1901490) folder structure. `fastlane/metadata/android/` with `en-US` and `nl-NL`: title, short and full description, changelogs, icon, feature graphic and five screenshots.

### Docs

* [x] Please read [the guide](https://gitlab.com/fdroid/fdroiddata/-/blob/master/CONTRIBUTING.md) first if this is your first contribution.
* [x] Please make sure your metadata follows the best practice in [our templates](https://gitlab.com/fdroid/fdroiddata/tree/master/templates).
* [x] Please read the [Build Metadata Reference](https://f-droid.org/docs/Build_Metadata_Reference/) and make sure your metadata is valid.
* [x] Please read the [Quick Start Guide](https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/).

### Merge Request Setup

* [x] The title of this merge request should follow "New app: app name" format.
* [x] Please make sure your fdroiddata fork is public and your branch is not protected. See <https://docs.gitlab.com/user/project/repository/branches/protected/>.
* [x] Please read [our Git guide](https://gitlab.com/fdroid/wiki/-/wikis/Tips-for-fdroiddata-contributors/Git-Usage) if you don't know how to rebase your branch. Don't rebase your branch if there is no conflict.
* [x] All related [fdroiddata](https://gitlab.com/fdroid/fdroiddata/issues) and [RFP issues](https://gitlab.com/fdroid/rfp/issues) have been referenced in this merge request. There are none.
* [x] Please only submit one app in one MR.

### Metadata

* [x] Metadata must be put in `metadata/<applicationId>.yml`.
* [x] Metadata must be a valid YAML file.
* [x] Metadata must use LF as line ending.
* [x] Don't add summary/description/changelog/images or anything that should be provided in upstream repo. Please check the Changes tab to make sure there is no other unrelated files added in the MR.
* [x] Releases are tagged and auto update is enabled unless there is a special reason. Tags `v<versionName>`; `UpdateCheckMode: Tags` with a pattern that leaves out the date-style tags of the song library.
* [x] There is an issue tracker and contact info of the author so that we can report bugs and contact the author.
* [x] An AuthorName must be added. It doesn't need to be the real name.
* [x] External repos are added as git submodules instead of srclibs. There are none.
* [x] Enable [Reproducible Builds](https://f-droid.org/docs/Reproducible_Builds). `Binaries` and `AllowedAPKSigningKeys` are set. Built in `registry.gitlab.com/fdroid/fdroidserver:buildserver` with `fdroid build --on-server`: the unsigned APK is byte-identical to the project's CI build and to a build on my PC (SHA-256 `d92492d8…a7b65f9aced6`), and fdroid reported "compared built binary to supplied reference binary successfully" for the signed GitHub APK. `fdroid lint`, `rewritemeta` and `scanner` (0 problems) pass.
* [x] Setup abi split if the APK is large and the splitted ones can be much smaller. Not set up, on purpose: the native libraries (libsodium and JNA, in seven ABIs) are 2.6 MB of the 5.8 MB APK, so per-ABI APKs would be about 3.6 MB; but the app shares its own installed APK offline from phone to phone, to a phone that may have another ABI, so it needs the one APK that runs everywhere, and one APK also keeps the Play, GitHub and F-Droid builds identical.
* [x] Only the latest versions should be kept in the metadata before it's merged. If you update the metadata, please replace the old versions with the new ones.
* [x] Don't add any disabled versions in the metadata.
* [x] The `commit` field should be the full hash. Please don't use tag or branch in commit.

Notes for reviewers: the `sudo` block installs JDK 17 from Debian 12, because the build asks Gradle for a JDK 17 toolchain (as its CI uses) and the Debian 13 buildserver has only JDK 21. The only native code is the prebuilt libsodium (through lazysodium-android) and JNA from Maven Central, and AndroidX DataStore's small native part; the app compiles no native code itself.

### Pipeline

* [ ] All pipelines should pass.
* [ ] All warnings and errors in the Reports tab should be fixed or explained.
* [x] F-Droid CI runners are under GitLab's FOSS program, so there's no need for you to pay for any CI time. If Gitlab starts asking for phone numbers or credit cards don't submit anything, just leave a note in the MR so we know we need to trigger the CI.

/label ~"New App"

---

After opening it: watch the merge request's pipeline, tick the two pipeline boxes once it passes, and answer reviewers' questions; FDROID-RUNBOOK Step 9 lists the likely ones.
