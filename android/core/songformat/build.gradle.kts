// SPDX-License-Identifier: Apache-2.0
// core/songformat: parser, canonical form, hash, line list, library file, report codes,
// setlists, archive and snapshot reader (song-format sections 4–13). No Android, network or storage.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":core:model"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
}
