// SPDX-License-Identifier: Apache-2.0
// core/testing: simulated transport, fake clock, conformance corpus runner. A test fixture for the
// other core modules and for app's instrumented tests; it never ships (architecture section 4).
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":core:model"))
    api(project(":core:protocol"))
    implementation(project(":core:songformat"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.test)
    implementation(libs.lazysodium.java) // test-only crypto (architecture section 8)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
}
