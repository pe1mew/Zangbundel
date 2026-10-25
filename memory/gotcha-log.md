# Gotcha Log

<!-- Structured problem/solution journal. Append-only.
     Part of the self-learning loop: Capture → Surface → Promote → Retire.

     PROMOTION LIFECYCLE:
     - New entries start here (Capture phase)
     - At end-of-session, review for patterns (Surface phase)
     - When an entry recurs 2-3 times, promote it to the relevant topic file
       as an "if X, then Y" pattern (Promote phase). Word it without the
       first instance's domain, or it will not fire in the next one: "a result
       correct in its own terms is read as covering the claim it is used for",
       not "tests green != prod verified". Check for an existing row first.
     - When a gotcha's root cause is fixed, mark it [RESOLVED] IN THE HEADING
       (Retire phase) — `### Title (2026-08-12) [RESOLVED]`. Curation reads
       headings and the Promoted table, not bodies, so a status written in a
       body is invisible to it — and so is a resolved entry whose heading was
       never marked, which then reads as open forever. Nothing chases you:
       marking the heading is the only thing that keeps the log's open set
       meaningful.
     - Track what you've promoted in the "Promoted" section below

     When the root cause is fixed, mark it resolved here (don't delete).

     ARCHIVE WHEN IT GROWS; STILL DON'T DELETE. Once most of what a grep
     returns is resolved, MOVE entries that are resolved AND dated before the
     current month to gotcha-log-archive.md beside this file:
     - Select on the `[RESOLVED` PREFIX. Read the kept side, not only the count.
     - Never move `[OPEN]` or `[PARKED]` entries, however old.
     - The Promoted and Mechanized tables stay here.
     - End an entry at the next `##` OR `###` heading.
     - Move, never summarise: afterwards every heading is in exactly one of
       the two files and no body line is missing from both.
     - Point each file at the other, and point the project file's
       stuck-or-debugging row at both.

     This file is tracked in git, so its history is available: questions
     about CHANGE (when did a status move, was every entry present before a
     split present after) are answerable with `git log -p memory/gotcha-log.md`. -->

<!-- Template for new entries:

### [Short description] (YYYY-MM-DD)
**Problem**: What went wrong or was confusing.
**Root cause**: Why it happened.
**Fix**: What solved it.

     Write the lesson and the action, not the narrative of the session
     that found it — having just lived through it, you will overweight
     the detail.

     A real entry runs ~700-1,200 characters and that is fine, so long as
     the process reads headings first. ABOVE ~3,000 CHARACTERS is the signal
     worth acting on — that is a page, and a page belongs in a topic file.

     WHAT TO CUT: precision is a claim specific enough to check; verbosity is
     restatement, emphasis and narration of your own reasoning. Keep the wrong
     value and the right one, the mechanism, file and section names, how it
     was caught, and the rule as if/then. One statement per fact. Do not
     abbreviate words to save characters.

     For this project the likely first entries are spec-level: a cross-reference
     that silently went stale when a section was renumbered, a requirement ID
     cited before it was defined, two documents disagreeing on a number. Log
     them the same way — Problem, Root cause, Fix — and name the documents.
-->

### A GUI wish contradicts a MUST in the FRD (2026-10-19) [x3] [PROMOTED]
**Problem**: Three times in one session the owner described a screen in a way that a MUST requirement forbade: no library download instruction for singers (FR-LEAD-30, MUST), no singer list (FR-LEAD-40 said "chosen from the list of joined singers"), and statistics sent to the author's server (FR-GEN-08 says the app sends nothing to the developer). Each time the GUI decision was written first and the collision found afterwards, once by a follow-up session that had already started amending the FRD.
**Root cause**: The GUI document was written from the owner's description, and the FRD was consulted for citations, not for prohibitions. A MUST that forbids a design is only found when its text is read, not when its ID is cited.
**Fix**: Before writing a design decision from an owner's wish, grep the FRD for the area's requirements and read every MUST in it; when one collides, record the decision with an open question and let the owner choose between amending the FRD (withdraw or demote the requirement, version bump, dependants) and changing the design. Promoted to `memory/MEMORY.md` Recently Promoted.

### Java 8 reads PlantUML sources as cp1252 (2026-10-19)
**Problem**: The rendered title of `design/gui/singer-mode-flow.puml` showed `â€“` for an en dash; arrows and accented Dutch words would garble the same way.
**Root cause**: The PlantUML jar run on Java 8 (`java version 1.8.0_503`, the only Java on the machine) decodes source files with the platform default charset, cp1252 on Windows, not UTF-8.
**Fix**: Always render with `-charset UTF-8`. The render commands in `design/gui/README.md` and `design/ble/README.md` carry the flag; if a rendered diagram shows `â€`, the flag was dropped.

### PlantUML switch and elseif chains render side by side (2026-10-19)
**Problem**: An activity diagram with a twelve-case `switch` rendered 6616 pixels wide; the same content with an `elseif` chain was 5075 pixels wide. Both unreadable for an assessment.
**Root cause**: PlantUML lays every alternative branch horizontally; each case adds width. Independent `if … endif` blocks, one after another, stack vertically.
**Fix**: For an event loop, draw a ladder of independent `if (event?) then (yes) … endif` blocks inside `repeat`; `design/gui/singer-mode-flow.puml` is 905 × 4341 that way. Say in a comment that the events are independent, since the ladder reads as ordered.

### PNG output clipped at 4096 pixels (2026-10-19)
**Problem**: A PNG rendered at exactly 4096 pixels wide with the right part of the diagram missing; the SVG of the same source was complete (viewBox 6617 wide).
**Root cause**: PlantUML's default `PLANTUML_LIMIT_SIZE` is 4096 and it clips rather than failing.
**Fix**: Render with `java -DPLANTUML_LIMIT_SIZE=16384 -jar plantuml.jar …`, and compare the PNG dimensions with the SVG viewBox after every render; a PNG width of exactly 4096 is the symptom.

### PlantUML writes only the last -t format (2026-10-20)
**Problem**: `java -jar plantuml.jar -charset UTF-8 -tsvg -tpng seq-07-helper-code-delegate.puml` rewrote the PNG and left the SVG from the day before; the render commands in `design/ble/README.md`, `design/gui/README.md` and all eight `.puml` header comments had the same two-flag form.
**Root cause**: PlantUML keeps one output format; a second `-t` flag replaces the first instead of adding to it. Nothing warns.
**Fix**: Render each format in its own run. After a render, compare the modification times of `.svg` and `.png`, or grep the SVG for a changed label; the READMEs and header comments now say so.

### Parallel session rewrote spec files under an open edit (2026-10-19)
**Problem**: A batch of edits to `functional-requirements.md` and `song-format.md` failed with "file has been modified since read"; afterwards `git status` showed those files modified although `git diff` was empty.
**Root cause**: A follow-up session started from a proposal card worked in the same checkout at the same time, first amending and then reverting the same files. Its reverts rewrote them with LF endings in a checkout whose `core.autocrlf` expects CRLF, which git reports as modified until it next touches the files.
**Fix**: Re-read a file right before editing it when another session is linked and working in the same folder; treat an empty `git diff` with a "modified" status as a line-ending artefact; when two sessions touch one document, send the second a scope message and let it finish before editing the same file.

### local.properties with single backslashes mangles the SDK path (2026-10-22) [RESOLVED]
**Problem**: The first Gradle run failed with a Dutch Windows IOException ("De syntaxis van de bestandsnaam ... is onjuist") on `:app:compileDebugJavaWithJavac`, with no file named.
**Root cause**: `android/local.properties` is a Java properties file: `\U` and `\:` are escapes, so `C\:\Users\drasv\...` was read as `C:UsersdrasvAppDataLocalAndroidSdk`, a relative path AGP then tried to use.
**Fix**: Write `sdk.dir=C\:/Users/drasv/AppData/Local/Android/Sdk` (escaped colon, forward slashes); lint's PropertyEscape check flags the unescaped form. If a Gradle error is a bare Windows path-syntax exception, then check the properties files for backslashes first.

### lazysodium-android drags the JNA jar next to the JNA aar (2026-10-22) [RESOLVED]
**Problem**: `:app:checkDebugDuplicateClasses` failed with `com.sun.jna.*` found twice (jna aar and jna jar); and `cryptoSignVerifyDetached` refused a `Long` length.
**Root cause**: `lazysodium-android` declares a transitive dependency on the plain `jna` jar, while Android needs the `@aar` artefact with the native libraries. In the Android build of lazysodium, `cryptoSignVerifyDetached` takes an `int` message length while `cryptoSignDetached` and the box calls take `long`.
**Fix**: `implementation(libs.lazysodium.android) { exclude(group = "net.java.dev.jna", module = "jna") }` plus `implementation(libs.jna) { artifact { type = "aar" } }` in `android/platform/crypto/build.gradle.kts`; pass `message.size` without `.toLong()` to the verify call. If a native-backed library is added, then check its pom for a jar/aar double of the same module.

### Newest AndroidX requires compile SDK 37 (2026-10-22) [x2] [RESOLVED]
**Problem**: After pinning the versions lint reported as newest (core 1.19.1, lifecycle 2.11.0, navigation 2.10.2, Compose BOM 2026.09.00), `:app:checkDebugAarMetadata` failed with 32 "requires compile against version 37" issues.
**Root cause**: The aar metadata (`META-INF/com/android/build/gradle/aar-metadata.properties`, `minCompileSdk`) of those releases says 37; the machine has platforms 35 and 36 and the project's decided target is 36.
**Fix**: Downloaded the candidate aars from `dl.google.com/dl/android/maven2` and read `minCompileSdk` directly: core 1.18.0, lifecycle 2.10.0, navigation 2.9.8 and Compose BOM 2026.06.01 (Compose 1.11.4) are the newest at 36; pinned those and said so in `libs.versions.toml`. Raising `compileSdk` to 37 is a decision for the owner (RUNBOOK Decisions keep target 36). If lint's "newer version available" is followed blindly, then expect the compile-SDK ceiling to bite; check `minCompileSdk` before bumping.
**Again 2026-10-25**: lazysodium-java had been bumped to 5.2.0 the same way while nothing used it; the first test that did failed resolution because 5.2.0 targets Java 21 and the build runs on JDK 17. Pinned at 5.1.4. Generalised: a version bump taken from lint's list is untested until something compiles and runs against it; check the artefact's minimum JVM and compile SDK, and bump only what is in use.

### Verification metadata written from a warm cache misses parent and BOM poms (2026-10-22) [RESOLVED]
**Problem**: The first CI run of `android.yml` failed at configuration: "Dependency verification failed for configuration 'classpath': kotlinx-coroutines-bom-1.8.0.pom", although `./gradlew check` passed locally with the same `gradle/verification-metadata.xml`.
**Root cause**: The metadata had been written with `--write-verification-metadata sha256` after several builds had already filled `~/.gradle/caches`; Gradle recorded the artefacts it resolved in that run and silently left out three poms it only had from the cache (`kotlinx-coroutines-bom` 1.8.0 and 1.10.2, `guava-parent` 33.2.1-jre). CI starts from an empty cache, fetches them, and finds no checksum.
**Fix**: Regenerate with every dependency re-fetched and the configuration cache off: `./gradlew --no-configuration-cache --refresh-dependencies --write-verification-metadata sha256 assembleDebug check assembleRelease`; the three missing components appeared. If the metadata is regenerated after a dependency change, then always pass `--refresh-dependencies`, and read the CI run of the push rather than trusting the local pass.

### Verification metadata written on Windows lacks the Linux aapt2 jar (2026-10-22) [RESOLVED]
**Problem**: The second CI run failed in `:platform:ble:androidApis`: "One artifact failed verification: aapt2-8.13.2-14304508-linux.jar", after the pom gap had been closed.
**Root cause**: AGP resolves `com.android.tools.build:aapt2` with a classifier for the host OS; a write pass on this machine records only `-windows.jar`. The Ubuntu runner fetches `-linux.jar`, which has no entry. The same holds for any future artifact with an OS classifier.
**Fix**: Google Maven publishes `.sha256` sidecars: added the `-linux.jar` and `-osx.jar` entries by hand (origin says so) next to the Windows one in `android/gradle/verification-metadata.xml`. Write mode keeps hand-added entries, so a regeneration does not drop them, but a new AGP version brings a new aapt2 version and needs the two sidecars fetched again. If CI fails verification on an artifact whose name ends in an OS, then fetch its sidecar checksum from the repository and add it; do not loosen verification.

### A failed Python write empties the file it was rewriting (2026-10-25) [RESOLVED]
**Problem**: An edit script read `StateTest.kt`, built new text, and called `open(p, 'w').write(s)`; the write raised `UnicodeEncodeError` (a lone surrogate from a `\uD83D` escape in the Python string) and left the file with one line.
**Root cause**: `open(..., 'w')` truncates before `write` runs, so an exception during encoding loses the old content.
**Fix**: Restored from the last commit with `git show HEAD:<path> > <path>` (read-only). If an edit carries non-ASCII or escape sequences, then use the Edit tool, or encode the text to bytes first and open the file only after that succeeded.

### A size limit stated with its reason is false when computed (2026-10-25) [x5] [PROMOTED]
**Problem**: Three numbers in `design/ble-protocol.md` failed the first time code computed them: the extended frame of up to 1000 bytes in one manufacturer-data field cannot be sent (one AD structure holds 255 bytes; measured 251 on a Samsung A52); legal values make a 558-byte state body against the 440-byte MUST of 9.4; Control op 8's 432 bytes of data "so the sealed write stays within 512" make a 527-byte write.
**Root cause**: Each limit was written with its justification but the worst case was never summed: field encodings, CBOR headers, the seal overhead, the AD header. Reading the sentence makes it look checked.
**Fix**: Protocol v0.14 (247-byte frames), v0.16 (417-byte chunks), v0.17 (fit rule, largest body 405 bytes); each figure is now pinned by a unit test that builds the worst case. Promoted to `memory/MEMORY.md` Recently Promoted.
**Again after promotion, 2026-10-25**: ble-protocol 6.5 gave the join link as 60–130 characters of URL and QR version 5 or 6; the encoder makes 99 to 170 (223 with accented names), versions 6 to 9 (11). The 60–130 was the base64 part alone. Caught by the rule itself: the encoder's test asserts the computed sizes. Protocol v0.19.

**Again, 2026-10-25 (GATT)**: 9.7's DATA chunks of MTU − 6 make 514-byte notifications at MTU 517, but an attribute value is at most 512 bytes and Android cut every full chunk by 2; and the HEADER, about 40 bytes, cannot fit the 20 bytes of MTU 23 that 8.2 requires. The first showed only on the phones (6 bytes missing from a 2 kB song), the second only when a test asserted the size. Fixed: chunks capped at 509 (v0.22); the HEADER is ble-protocol Q22.

### ADB on the test phones: sleeping screens and Samsung's hidden switch (2026-10-25) [RESOLVED]
**Problem**: `adb shell am start -W` hung for minutes on the Huawei; the Samsung A52 showed no ADB interface in Windows although USB debugging was on.
**Root cause**: The Huawei's screen had gone to sleep behind its lock screen, and `-W` waits for a launch that cannot complete. The Samsung (One UI 6.1) exposes debugging only after an unplug and replug, and Auto Blocker blocks commands over USB.
**Fix**: The owner unlocked both phones and set stay-awake while charging; for the Samsung: Auto Blocker off, authorisations revoked, cable replugged. If a launch over ADB hangs, then check `dumpsys power` for `mWakefulness` and the lock screen before anything else; if Windows lists a Samsung only as MTP and modem, then replug and check Auto Blocker.

### Bare section references escape the check (2026-10-25) [RESOLVED]
**Problem**: Error `0x8A` in ble-protocol 8.3 pointed at "(9.9)", a section that never existed, and `scripts/check-design.sh` passed.
**Root cause**: The check matches only references written "section N.M"; the protocol mostly cites bare numbers in parentheses.
**Fix**: Corrected by hand in protocol v0.16. A wider check is proposed in the Mechanized table. If a design document cites "(N.M)", then confirm the heading exists; the script will not.

### Git Bash rewrites paths for adb, or with MSYS_NO_PATHCONV not at all (2026-10-25)
**Problem**: `adb shell cat /sdcard/ui.xml` failed with `C:/Program Files/Git/sdcard/ui.xml`; with `MSYS_NO_PATHCONV=1` set to stop that, `adb install /c/Users/...apk` failed with "failed to stat".
**Root cause**: MSYS converts any argument that looks like a POSIX path for a Windows program: device paths get the Git root prefixed. Switching conversion off also leaves `/c/...` host paths unconverted, which adb.exe cannot open.
**Fix**: Set `MSYS_NO_PATHCONV=1` for adb commands and give host files as `C:/...` paths.

### Git converts CRLF in byte-exact test vectors (2026-10-25)
**Problem**: The song vector `non-canonical.cho` (BOM, CRLF, trailing spaces) was staged with its CRLFs turned into LFs (`git ls-files --eol`: `i/lf w/crlf`); committed like that it would no longer test CRLF, and the test would still pass.
**Root cause**: `core.autocrlf` is `true` on this machine and the repository had no attribute for the vectors.
**Fix**: `.gitattributes` marks `conformance/songs/*.cho` `-text`; re-stage with `git add --renormalize`, then compare the staged blob with the file (`cmp <(git show :path) path`). Any new byte-exact fixture needs the same rule.

### Python's zipfile quietly repairs attack vectors (2026-10-25)
**Problem**: Archive vectors for a backslash name and an encrypted entry, built with Python `zipfile` on Windows, looked right when read back with `zipfile`, but its reading turns `\` into `/` again, and `writestr` resets `flag_bits`, so the encrypted vector had no encryption flag.
**Root cause**: `ZipInfo` replaces `os.sep` with `/` and `_open_to_write` rewrites the flags; reading the file back through the same library hides both.
**Fix**: Check attack vectors on their raw bytes (central and local headers), not through the library that wrote them; patch flags in both headers after writing. Each vector's `.expected.json` carries its SHA-256 so a regenerated or converted file is caught.

## Promoted

<!-- Track gotchas that have been promoted to topic files or the memory index.
     This helps you avoid re-promoting and shows the loop is working.

     STATUS TAGS:
     - [PROMOTED] — lesson was moved up the stack (to a topic file, memory index, or project file)
     - [RESOLVED] — root cause was fixed; entry stays as history. Put it in
       the heading. Same for a recurrence count: `[x3]` in the heading, so the
       promotion step can see it without opening the entry.

     OCCURRENCES is the column that survives compression. Keep incrementing
     AFTER promotion: a promoted pattern that recurs means the promotion did
     not take. Date each recurrence briefly, so a rising rate is visible.

| Date | Gotcha | Occurrences | Promoted to |
|------|--------|-------------|-------------|
| YYYY-MM-DD | [short description] | 1 | `topic-file.md` |
| YYYY-MM-DD | [a pattern that kept recurring] | **3** — YYYY-MM-DD first; YYYY-MM-DD again; YYYY-MM-DD after promotion | project file, [rule name] |

-->

| Date | Gotcha | Occurrences | Promoted to |
|------|--------|-------------|-------------|
| 2026-10-19 | A GUI wish contradicts a MUST in the FRD | **3** — 2026-10-19 download instruction (FR-LEAD-30); 2026-10-19 singer list (FR-LEAD-40); 2026-10-19 statistics server (FR-GEN-08) | `memory/MEMORY.md` Recently Promoted |
| 2026-10-25 | A size limit stated with its reason is false when computed | **4** - 2026-10-25 extended frame (v0.14); 2026-10-25 state body (v0.17); 2026-10-25 op 8 chunk (v0.16); 2026-10-25 after promotion: join-link size and QR version (v0.19) | `memory/MEMORY.md` Recently Promoted |

## Mechanized

<!-- Review findings promoted to a deterministic check. The destination for
     `review-changes` Step 3.1. Separate from Promoted above because the
     destinations differ: a gotcha becomes PROSE an agent reads, a review
     finding becomes a CHECK that runs.

     STATUS:
     - `proposed` — the shape is named, no check exists yet
     - `live` — the check exists AND a seeded positive has made it go red.
       Nothing becomes `live` on the author's read of it.
     - `rejected` — triaged and judged NOT mechanizable; the Check cell
       carries the reason. Kept, not deleted.
     - `retired` — the class can no longer occur; say what removed it

     OCCURRENCES here counts sightings AFTER the row went `live` only.
     A lens finding a class its own live check covers means the check does
     not fire on the real shape. Investigate the check, not the finding.

     A `proposed` row's Check cell names the path the check WILL live at and
     marks it with the placeholder marker, immediately after the path, in
     that path's own cell. -->

| Date | Finding shape | Check | Status | Occurrences |
|------|---------------|-------|--------|-------------|
| 2026-10-18 | A requirement ID cited in a design document that no FRD entry defines, or defined twice | `scripts/check-design.sh` | live | 0 |
| 2026-10-18 | A "section N.M" reference in `design/ble-protocol.md` pointing at a heading that no longer exists after renumbering | `scripts/check-design.sh` | live | 1 (2026-10-25: caught "section 9.9" in a change row) |
| 2026-10-25 | A bare "(N.M)" reference in `design/ble-protocol.md` pointing at a heading that does not exist (the "(9.9)" of error `0x8A`) | `scripts/check-design.sh`, extended to parenthesised numbers | proposed | 1 |
| 2026-10-18 | A downstream spec whose "Depends on" header names an FRD version other than the FRD's current one | `scripts/check-design.sh` | live | 0 |
