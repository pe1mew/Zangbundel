# Zangbundel – Android project

The Gradle project of the app, laid out as `design/architecture.md` section 4 prescribes. The
application module is `app/`; F-Droid builds it with `subdir: android/app`.

## Build

Requirements: JDK 17, Android SDK with platform 36 and build-tools 36.0.0 (`ANDROID_HOME` set or
`local.properties` with `sdk.dir`). The Gradle wrapper downloads Gradle itself.

```bash
cd android
./gradlew assembleDebug        # debug APK, pseudo-locales on, application id suffix .debug
./gradlew check                # unit tests of every module, lint (missing translations are errors)
./gradlew assembleRelease      # unsigned when keystore.properties is absent
./gradlew bundleRelease        # refuses without keystore.properties (docs/RUNBOOK.md Step 6)
```

`keystore.properties` lives next to this file and is ignored by git:

```
storeFile=/absolute/path/zangbundel-appsigning.jks
storePassword=...
keyAlias=...
keyPassword=...
```

## Modules

| Module | Kind | Content |
|---|---|---|
| `core/model` | Kotlin/JVM | ids, song references, setlist entries, gathering and role states |
| `core/songformat` | Kotlin/JVM | canonical form, hash, limits, report codes; the song parser (`SongParser`, `Song`: metadata, sections, canonical line list, the 11.3 codes a single file can raise, the BLE licence summary), the library file (`LibraryFileParser`, `Library`) and the import check across a library's files (`LibraryImport`), release archives and snapshots (`ArchiveImport` on a bounded `ZipReader`), setlists (`SetlistParser`, `SetlistResolver`, `SetlistWriter`), the release index and the URLs a leader pastes (`ReleaseIndex`, `LibraryUrl`) |
| `core/protocol` | Kotlin/JVM | `Transport` and `Crypto` interfaces, protocol constants, bounded CBOR codec (`cbor/`), erasure code (`fec/`), coded frame codec, framer and reassembler (`frame/`), section 9 payloads, the signed state, DEFLATE and sealed payloads (`payload/`), `ProtocolError` with the 8.3 codes, the carousel scheduler (`carousel/`), singer, leader and slot state machines with `SingerEngine` and `LeaderSession` that run them against a `Transport` (`engine/`), the GATT service, client and Data stream (`gatt/`), beacon and join-link codecs (`beacon/`) |
| `core/testing` | Kotlin/JVM | fake clock, `JvmSodiumCrypto` (lazysodium-java), `SimulatedAir` and `SimulatedTransport` (advertising with loss, GATT with slots and latency, in virtual time), conformance corpus (test fixture, never ships) |
| `platform/ble` | Android | `AndroidTransport`: beacon, legacy and extended data sets, continuous scan, GATT server and client (`AndroidGatt.kt`); `AdvertisingProbe` for the radio check; Bluetooth permissions |
| `platform/crypto` | Android | `SodiumCrypto` on lazysodium-android |
| `platform/storage` | Android | Room database (libraries, songs with an FTS4 search table, setlists, the import report), the install pipeline (`InstallPlan`, `LibraryFiles`: staging and swap, `LibraryInstaller`, `RoomLibraryIndex`), `LibraryStorage` (the app's way in; Room stays inside), `StartupSweep`; JVM tests for the pipeline, the DAO waits for instrumented tests |
| `platform/net` | Android | `HttpsFetcher`: HTTPS only, no http redirects, fixed user agent, size cap |
| `app` | Android | `ZangbundelApp`, `AppContainer`, `MainActivity`, Compose theme, `GatheringService`, singer settings that live only as long as the process (`settings/`), adding and updating libraries (`library/LibraryRepository` over `HttpsDownloads`), the configuration-screen backdrop, the home screen and the library screen (`ui/ConfigurationScreen`, `ui/HomeScreen`, `ui/library/`, guiDesign D-21, D-50, D-51), the reception bar island (`ui/singer/ReceptionIsland`, guiDesign D-81), manifest, resources; `src/radiocheck` (debug and fieldtest only) holds the radio check and two-phone test screens, `src/release` their empty stand-ins |

Versions are pinned in `gradle/libs.versions.toml`; a new dependency needs a row in architecture
section 8 in the same change. `gradle/verification-metadata.xml` is regenerated with
`./gradlew --no-configuration-cache --refresh-dependencies --write-verification-metadata sha256 assembleDebug check assembleRelease`
whenever a dependency changes; `--refresh-dependencies` matters, or poms already in the local cache are left out.
The `aapt2` entries for `-linux` and `-osx` are added by hand from Google Maven's `.sha256` sidecars (a Windows
machine records only `-windows`); a new AGP version needs them fetched again.

Every source file starts with `SPDX-License-Identifier: Apache-2.0`.
