pluginManagement {
    includeBuild("build-logic")
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
        maven("https://jitpack.io") {
            name = "JitPack"
            content {
                includeGroupByRegex("com\\.github\\..*")
            }
        }
    }
}

rootProject.name = "Shizuku"

include(":common", ":server", ":starter", ":shell", ":manager")

// magisk-ui-kit is consumed for its root module only. Its billing and update
// submodules pull Play Billing and Play App Update, neither of which this app
// uses, so they are deliberately left out.
include(":magisk-ui-kit")
project(":magisk-ui-kit").projectDir = file("magisk-ui-kit")

// The api submodule is pinned third-party code: the parent build owns the module
// wiring so the submodule never regrows into a parallel root build with its own
// plugin and AGP versions (same contract as the embedded magisk-* submodules).
val apiRoot = "api"
listOf("aidl", "rish", "shared", "api", "provider", "server-shared").forEach { module ->
    include(":$module")
    project(":$module").projectDir = file("$apiRoot/$module")
}
