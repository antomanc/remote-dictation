plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "dev.remotedictation"
    compileSdk = 35
    ndkVersion = "27.3.13750724" // also used to strip the native libraries

    defaultConfig {
        applicationId = "dev.remotedictation"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        // Fire TV sticks are ARM; skipping x86 keeps the APK smaller.
        ndk.abiFilters += listOf("armeabi-v7a", "arm64-v8a")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions.jvmTarget = "17"
    buildFeatures.buildConfig = true
    // whisper.cpp streams its model straight out of the APK, which is fast only when uncompressed.
    androidResources.noCompress += "bin"
}

dependencies {
    implementation(project(":capture-remote"))
    implementation(project(":stt-vosk"))
    implementation(project(":stt-whisper"))
    implementation(project(":action-type-text"))
}
