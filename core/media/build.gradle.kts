plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "wiki.kivo.core.media"
    compileSdk = 37
    buildToolsVersion = "37.0.0"
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    api(project(":core:model"))
    implementation(libs.core.ktx)
    implementation(libs.okhttp)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    api(libs.media3.exoplayer)
    api(libs.media3.ui)
    implementation(libs.media3.okhttp)
    implementation(libs.spine)
    implementation(libs.filament)
    implementation(libs.filament.gltfio)
    implementation(libs.filament.utils)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
