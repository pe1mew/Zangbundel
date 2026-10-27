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
    testImplementation(project(":core:testing")) // conformance vectors (test plan 3.2)
}

// Fuzzing (test plan 3.4, architecture A-21, release check G-09): Jazzer targets in src/fuzz, on the JUnit 5
// platform in their own source set so the JUnit 4 tests stay as they are. Without JAZZER_FUZZ=1 every target
// runs its fixed and seed inputs (part of `check`); with JAZZER_FUZZ=1 Jazzer fuzzes one target per run,
// chosen with --tests, for the budget set in -Pfuzz.seconds (scripts/fuzz.sh).
val fuzz: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
configurations[fuzz.implementationConfigurationName].extendsFrom(configurations.implementation.get())
configurations[fuzz.runtimeOnlyConfigurationName].extendsFrom(configurations.runtimeOnly.get())
dependencies {
    "fuzzImplementation"(libs.jazzer.junit)
    "fuzzImplementation"(project(":core:testing"))
    "fuzzRuntimeOnly"(libs.junit.jupiter.engine)
    "fuzzRuntimeOnly"(libs.junit.platform.launcher)
}
val fuzzTest by tasks.registering(Test::class) {
    description = "Runs the Jazzer targets; fuzzes when JAZZER_FUZZ=1."
    group = "verification"
    testClassesDirs = fuzz.output.classesDirs
    classpath = fuzz.runtimeClasspath
    useJUnitPlatform()
    (project.findProperty("fuzz.seconds") as String?)?.let { systemProperty("jazzer.max_duration", it + "s") }
    environment("JAZZER_FUZZ", System.getenv("JAZZER_FUZZ") ?: "")
    outputs.upToDateWhen { false }
}
tasks.named("check") { dependsOn(fuzzTest) }
