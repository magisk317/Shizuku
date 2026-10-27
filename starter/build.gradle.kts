plugins {
    id("magisk.android.library")
    id("dev.rikka.tools.refine")
}

val shizukuApplicationId: String = rootProject.extra["shizukuApplicationId"] as String

android {
    namespace = "rikka.shizuku.starter"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        buildConfigField("String", "MANAGER_APPLICATION_ID", "\"$shizukuApplicationId\"")
    }
}

dependencies {
    implementation(project(":common"))
    implementation(project(":shared"))
    implementation(project(":server-shared"))
    compileOnly(project(":provider"))
    implementation(libs.androidx.annotation)
    implementation(libs.hidden.compat)
    compileOnly(libs.hidden.stub)
    implementation(libs.refine.runtime)
}
