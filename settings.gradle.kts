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

// The api submodule is pinned third-party code: the parent build owns the module
// wiring so the submodule never regrows into a parallel root build with its own
// plugin and AGP versions (same contract as the embedded magisk-* submodules).
val apiRoot = "api"
listOf("aidl", "rish", "shared", "api", "provider", "server-shared").forEach { module ->
    include(":$module")
    project(":$module").projectDir = file("$apiRoot/$module")
}
