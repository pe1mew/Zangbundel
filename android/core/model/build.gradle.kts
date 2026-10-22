// SPDX-License-Identifier: Apache-2.0
// core/model: ids, song references, setlist entries, gathering and role states.
// May depend on the Kotlin standard library only (architecture section 4).
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
}
