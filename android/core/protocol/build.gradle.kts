// SPDX-License-Identifier: Apache-2.0
// core/protocol: CBOR codec, documents, frames, reassembly, carousel, state machines,
// Transport and Crypto interfaces (design/ble-protocol.md). No Android, network, storage or crypto library.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:songformat"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":core:testing")) // JvmSodiumCrypto, fake clock
}
