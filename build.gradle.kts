import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import javax.inject.Inject

buildscript {
    configurations.all {
        resolutionStrategy {
            // Java 27 bytecode target: AGP 9.5.0-rc01 and the rikka refine plugin
            // resolve ASM 9.9, which caps at major 70 and rejects the Java 27
            // classes this build emits (dev.rikka.tools.refine then fails with
            // "Unsupported class file major version 71" while instrumenting).
            // Force the family on the classpath the plugins actually run on --
            // project-level forces do not reach it. Same workaround as the rest
            // of the magisk3171 chain.
            force("org.ow2.asm:asm:9.11.1")
            force("org.ow2.asm:asm-analysis:9.11.1")
            force("org.ow2.asm:asm-commons:9.11.1")
            force("org.ow2.asm:asm-tree:9.11.1")
            force("org.ow2.asm:asm-util:9.11.1")
        }
    }
}

plugins {
    id("magisk.android.application") apply false
    id("magisk.android.library") apply false
    id("magisk.app.signing") apply false
    id("magisk.app.packaging") apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.refine) apply false
}

abstract class GitCommitCountValueSource : ValueSource<Int, GitCommitCountValueSource.Params> {

    interface Params : ValueSourceParameters {
        val gitProjectDir: Property<String>
    }

    @get:Inject
    abstract val execOperations: ExecOperations

    override fun obtain(): Int = runCatching {
        val stdout = ByteArrayOutputStream()
        val result = execOperations.exec {
            commandLine("git", "-C", parameters.gitProjectDir.get(), "rev-list", "--count", "HEAD")
            standardOutput = stdout
            isIgnoreExitValue = true
        }
        if (result.exitValue == 0) stdout.toString().trim().toInt() else 0
    }.getOrDefault(0)
}

// Single source of truth for the three values every module used to reach into
// another module's DSL for. -PversionName / -PversionCode win so the release
// flow can pin an exact version, matching the rest of the magisk3171 chain.
val shizukuApplicationId = "moe.shizuku.privileged.api"

val shizukuVersionName: String = providers.gradleProperty("versionName")
    .orElse(libs.versions.versionName)
    .get()

val shizukuVersionCode: Int = providers.gradleProperty("versionCode")
    .map { requireNotNull(it.toIntOrNull()) { "Invalid -PversionCode=$it" } }
    .orElse(
        providers.of(GitCommitCountValueSource::class.java) {
            parameters.gitProjectDir.set(rootDir.absolutePath)
        }.map { if (it > 0) it else 1 },
    )
    .get()

extra.set("shizukuApplicationId", shizukuApplicationId)
extra.set("shizukuVersionName", shizukuVersionName)
extra.set("shizukuVersionCode", shizukuVersionCode)

// The pinned api submodule modules carry their own build files but no platform
// configuration -- upstream got that from a settings plugin the parent build
// never reads. Supply it from the shared catalog here.
val apiRootDir: File = rootDir.resolve("api")
val compileSdkSpec = libs.versions.compileSdk.get()
val sdkMajor = compileSdkSpec.substringBefore('.').toInt()
val sdkMinor = compileSdkSpec.substringAfter('.', "").toIntOrNull() ?: 0

subprojects {
    if (projectDir.startsWith(apiRootDir)) {
        plugins.withId("com.android.library") {
            extensions.configure<com.android.build.api.dsl.LibraryExtension> {
                compileSdk = sdkMajor
                if (sdkMinor > 0) compileSdkMinor = sdkMinor
                buildToolsVersion = libs.versions.buildTools.get()
                ndkVersion = libs.versions.ndk.get()
            }
        }
    }
}
