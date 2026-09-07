plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "wiki.kivo.app"
    compileSdk = 37
    buildToolsVersion = "37.0.0"
    defaultConfig {
        // 基础验收包身份；正式包名及签名发布前再由站长保管与确定。
        applicationId = "wiki.kivo.app.preview"
        minSdk = 26
        targetSdk = 37
        versionCode = 5
        versionName = "0.4.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 本地验收使用独立调试证书；正式发行必须替换，禁止伪称正式签名。
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    // 工程与安装包共用许可正文，避免后续升级只更新其中一份。
    sourceSets.getByName("main").assets.srcDir("../licenses")
    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:content"))
    implementation(project(":core:media"))
    implementation(project(":feature:home"))
    implementation(project(":feature:account"))
    implementation(project(":feature:character"))
    implementation(project(":feature:organization"))
    implementation(libs.activity)
    implementation(libs.core.ktx)
    implementation(libs.splashscreen)
    implementation(libs.lifecycle.compose)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.navigation)
    implementation(libs.navigation.runtime)
    implementation(libs.navigation.ui)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.coil.network)
    implementation(libs.okhttp)
    debugImplementation(libs.compose.tooling)
    debugImplementation(libs.compose.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.test)
    androidTestImplementation(libs.android.test.runner)
    androidTestImplementation(libs.android.test.junit)
    androidTestImplementation(libs.android.test.rules)
    androidTestImplementation(libs.espresso)
}
