// SPDX-License-Identifier: Apache-2.0
// Root build: plugins are declared here once and applied per module.

// Kotlin 2.4 needs R8 9.1.29 or newer; AGP 8.13 bundles R8 8.13, which cannot read Kotlin 2.4's metadata and
// warned about every class (developer.android.com/build/kotlin-support). A newer R8 on the build classpath wins.
buildscript {
    repositories { google() }
    dependencies { classpath(libs.r8) }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
