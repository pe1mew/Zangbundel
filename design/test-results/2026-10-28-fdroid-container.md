# F-Droid build-container test – 2026-10-28

FDROID-RUNBOOK Step 8 in F-Droid's own build environment: the image `registry.gitlab.com/fdroid/fdroidserver:buildserver` (digest `sha256:1ea8bef1…26797e0dac`, Debian 13.7, JDK 21, no NDK) under Docker Desktop on the owner's PC, with fdroidserver `cdfdce8` (2026-10-27) and fdroiddata `838a63f41` (2026-10-28) cloned fresh. Built: commit `8400515` (versionName 0.1.1, versionCode 2026102701), the first pushed commit with `keepDebugSymbols`, via the metadata file with that commit and `Binaries` commented out, since no GitHub release exists yet. **Result: pass after one change to the metadata; F-Droid's build is byte-identical to CI's and the PC's.**

| Check | Result |
|---|---|
| `fdroid readmeta`, `lint` | **Pass**, no warning |
| `fdroid rewritemeta` | **Pass**; it changes nothing but dropping the file's comments |
| `fdroid checkupdates --allow-dirty` | *No matching tags found*: no `v` tag exists yet (the only tag is the library-form `v2026.10.2`, which the pattern leaves out on purpose). Expected; to repeat after the first release tag |
| `fdroid build`, first try | **Fail**: *Cannot find a Java installation … languageVersion=17*. Every module sets `jvmToolchain(17)`; the image has only JDK 21 |
| The fix | A `sudo:` block in the metadata that adds Debian 12's archive and installs `openjdk-17-jdk-headless` from it, as other current recipes do (fdroiddata has 927 recipes installing JDK 17). Plain `apt-get install openjdk-17-jdk-headless` fails on Debian 13 (*Unable to locate package*). The lines run only with `--on-server`, as fdroiddata's CI builds; without it fdroid skips them with a warning |
| `fdroid build --on-server --no-tarball` | **Pass**: Gradle 8.14.6 (from the wrapper's `distributionUrl`, via `gradlew-fdroid`), *BUILD SUCCESSFUL in 3m 48s*. fdroid removed `gradle-wrapper.jar` and the signing config from `android/app/build.gradle.kts` before building; neither changed the output |
| **The APK against CI's** | 5 715 114 bytes, SHA-256 `1a54e632bd5e03a7…24adf0c67`: **byte-identical** to CI's unsigned APK of `8400515` (run 40652610313) and of `d63af14` (run 40585404811), and to the PC's build of `fb6fea5` (2026-10-27). Three builders, three systems, one APK |
| `fdroid scanner` | **Pass**: 0 problems |
| `scripts/fdroid-container-test.sh` | The steps as a script, run once with `--commit 8400515 --no-binaries --compare` CI's APK: the same results, *identical*, exit 0 |

**Found on the way:** fdroiddata cloned on Windows and mounted into the container fails, because Windows Git writes its symlinks as text files and the folder belongs to another user; the script clones inside the container. `fdroid build` exits 0 even when the build fails, so the script reads its output. `update-java-alternatives -s java-1.17.0-openjdk-amd64` warns that `jconsole` is missing (a headless JDK has none) and carries on.

**Not run:** the metadata's own Builds entry `6faaf6f` (no `keepDebugSymbols`; F-Droid's server has no NDK, so it would match CI's build of that commit, not tested); the `Binaries` check and the signature copy, which need the owner-signed APK of a release; F-Droid's real build server, which runs the same image.
