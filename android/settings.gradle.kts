// SPDX-License-Identifier: Apache-2.0
// Module layout per design/architecture.md section 4.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Zangbundel"

include(":app")
include(":core:model")
include(":core:songformat")
include(":core:protocol")
include(":core:testing")
include(":platform:ble")
include(":platform:crypto")
include(":platform:storage")
include(":platform:net")
