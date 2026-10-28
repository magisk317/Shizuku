import com.android.build.api.artifact.SingleArtifact
import org.gradle.api.file.DuplicatesStrategy

plugins {
    id("magisk.android.application")
    id("dev.rikka.tools.refine")
}

val shizukuApplicationId: String = rootProject.extra["shizukuApplicationId"] as String
val shizukuVersionName: String = rootProject.extra["shizukuVersionName"] as String
val shizukuVersionCode: Int = rootProject.extra["shizukuVersionCode"] as Int

android {
    namespace = "rikka.shizuku.shell"

    defaultConfig {
        versionCode = shizukuVersionCode
        versionName = shizukuVersionName
        buildConfigField("String", "MANAGER_APPLICATION_ID", "\"$shizukuApplicationId\"")
    }

    buildFeatures {
        buildConfig = true
    }

    dependenciesInfo {
        includeInApk = false
    }

    lint {
        checkReleaseBuilds = false
    }
}

// The manager APK ships this module's dex as assets/rish_shizuku*.dex. Reading
// the packaged APK through the artifact API wires the dependency on the
// packaging task, so the dex always comes from the variant being assembled.
androidComponents {
    onVariants { variant ->
        val variantName = variant.name.replaceFirstChar { it.uppercaseChar() }
        val apkDir = variant.artifacts.get(SingleArtifact.APK)
        val extractTask = tasks.register<Sync>("extractRishDex$variantName") {
            group = "shizuku"
            description = "Extract classes*.dex from the $variantName shell APK into the manager assets."
            from(zipTree(apkDir.map { it.asFileTree.matching { include("*.apk") }.singleFile })) {
                include("classes*.dex")
            }
            // One directory per variant in the consuming module: every variant writing into the
            // same folder let a release merge pick up the debug build's extra dex parts, and
            // Gradle 9 rejects the undeclared overlap between the two extract tasks.
            into(layout.projectDirectory.dir("../manager/build/generated/rishDex/${variant.name}"))
            // classes.dex -> rish_shizuku.dex, classes2.dex -> rish_shizuku2.dex, ...
            // The minified release build shrinks this module to a single dex, but
            // an unminified build splits it, and app_process needs every part.
            rename { name ->
                if (name == "classes.dex") {
                    "rish_shizuku.dex"
                } else {
                    "rish_shizuku${name.removePrefix("classes").removeSuffix(".dex")}.dex"
                }
            }
            // Sync already clears the destination, so a variant that stops splitting
            // does not keep shipping rish_shizuku4.dex from an older build.
        }
        tasks.matching { it.name == "assemble$variantName" }.configureEach { finalizedBy(extractTask) }
    }
}

// AGP 9's built-in Kotlin adds kotlin-stdlib to every Android module. This
// module is pure Java and its dex is what the manager ships as
// assets/rish_shizuku.dex, so the stdlib would only split it into a multidex
// the rish script cannot load.
configurations.configureEach {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
}

dependencies {
    implementation(libs.hidden.compat)
    compileOnly(libs.hidden.stub)
}
