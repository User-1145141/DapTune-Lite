plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.weich.daptune.core.model"
    compileSdk = 36
    defaultConfig { minSdk = 30 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
