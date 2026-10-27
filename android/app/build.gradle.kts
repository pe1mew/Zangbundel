// SPDX-License-Identifier: Apache-2.0
// app: Application and AppContainer, MainActivity, Compose screens, ViewModels, GatheringService,
// QR rendering, share sheet, resources and locales (design/architecture.md sections 4 and 9).
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Release signing reads keystore.properties next to the Android project, never committed (RUNBOOK Step 6).
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) keystorePropertiesFile.inputStream().use { load(it) }
}
val hasKeystore = keystorePropertiesFile.exists()

android {
    namespace = "nl.pe1mew.zangbundel.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "nl.pe1mew.zangbundel.app" // permanent once uploaded to Play (RUNBOOK Decisions)
        minSdk = 26
        targetSdk = 36
        versionCode = 2026102201 // YYYYMMDDnn, strictly increasing
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isPseudoLocalesEnabled = true // guiDesign D-78
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasKeystore) signingConfig = signingConfigs.getByName("release")
        }
        // Field-test build (architecture A-20): release settings plus the local radio log; never published.
        create("fieldtest") {
            initWith(getByName("release"))
            applicationIdSuffix = ".fieldtest"
            matchingFallbacks += listOf("release")
            // The owner's key when keystore.properties is there; otherwise the debug key, so a developer can install it.
            // Its application id differs from the store app's, so neither signature ever meets the other.
            signingConfig = signingConfigs.getByName(if (hasKeystore) "release" else "debug")
        }
    }

    // Developer and field-test tools (the radio check of test-plan F-01) exist in debug and
    // fieldtest only; release compiles the empty stand-in in src/release (assessment SP-23).
    sourceSets {
        listOf("debug", "fieldtest").forEach { name ->
            maybeCreate(name).apply {
                java.srcDir("src/radiocheck/kotlin")
                res.srcDir("src/radiocheck/res")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = false // no build time, user or path in the APK (A-19)
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Every install is one APK, so the app can share itself (A-18).
    bundle {
        language { enableSplit = false }
        density { enableSplit = false }
        abi { enableSplit = false }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        error += listOf("MissingTranslation", "ExtraTranslation") // A-16
        // versionCode is YYYYMMDDnn by decision (RUNBOOK Decisions); it stays below Play's 2100000000 until 2100.
        disable += "HighAppVersionCode"
        warningsAsErrors = false
        abortOnError = true
    }

    packaging {
        resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1")
    }
}

kotlin {
    jvmToolchain(17)
}

// bundleRelease is the Play upload path: it must not run unsigned (A-19).
tasks.matching { it.name == "bundleRelease" || it.name == "bundleFieldtest" }.configureEach {
    doFirst {
        if (!hasKeystore) throw GradleException(
            "keystore.properties is missing next to android/: a bundle for Play must be signed with the app signing key (docs/RUNBOOK.md Step 6)."
        )
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:songformat"))
    implementation(project(":core:protocol"))
    implementation(project(":platform:ble"))
    implementation(project(":platform:crypto"))
    implementation(project(":platform:storage"))
    implementation(project(":platform:net"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    // A-14: the opt-in update check on Wi-Fi is the app's one scheduled job (FR-LIB-13, assessment SP-29).
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.zxing.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
