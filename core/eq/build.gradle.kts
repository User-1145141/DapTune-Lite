plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.weich.daptune.core.eq"
    compileSdk = 36
    defaultConfig { minSdk = 30 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets.getByName("test").resources.srcDir(rootProject.file("examples/profiles"))
}

dependencies {
    api(project(":core:model"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
