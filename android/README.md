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
| `core/songformat` | Kotlin/JVM | canonical form, hash, limits, report codes; parser, line list, archive reader to come |
| `core/protocol` | Kotlin/JVM | `Transport` and `Crypto` interfaces, protocol constants; CBOR, frames, carousel, state machines to come |
| `core/testing` | Kotlin/JVM | fake clock, simulated transport skeleton, conformance corpus (test fixture, never ships) |
| `platform/ble` | Android | `AndroidTransport` with capability detection; Bluetooth permissions |
| `platform/crypto` | Android | `SodiumCrypto` on lazysodium-android |
| `platform/storage` | Android | Room database (one table so far), `StartupSweep` |
| `platform/net` | Android | `HttpsFetcher`: HTTPS only, no http redirects, fixed user agent, size cap |
| `app` | Android | `ZangbundelApp`, `AppContainer`, `MainActivity`, Compose theme, `GatheringService`, manifest, resources |

Versions are pinned in `gradle/libs.versions.toml`; a new dependency needs a row in architecture
section 8 in the same change. `gradle/verification-metadata.xml` is regenerated with
`./gradlew --write-verification-metadata sha256 help` whenever a dependency changes.

Every source file starts with `SPDX-License-Identifier: Apache-2.0`.
