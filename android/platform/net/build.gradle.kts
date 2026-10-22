// SPDX-License-Identifier: Apache-2.0
// platform/net: HTTPS fetch of release indexes and archives, conditional GET, download caps,
// the opt-in Wi-Fi update check with WorkManager (architecture A-14). Uses storage for staging.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "nl.pe1mew.zangbundel.platform.net"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:songformat"))
    implementation(project(":platform:storage"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
}
