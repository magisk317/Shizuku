plugins {
    id("magisk.android.library")
}

android {
    namespace = "rikka.shizuku.common"
}

dependencies {
    compileOnly(libs.hidden.stub)
}
