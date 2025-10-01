plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "nl.stoux.tfw.automotive"
    compileSdk = 36

    defaultConfig {
        applicationId = "nl.stoux.tfw"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":service:playback"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    // For Android Automotive specific functionality
    implementation(libs.androidx.car.app.automotive)

    // Hilt DI for accessing shared repositories
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Media3 controller to control playback service from templates
    implementation(libs.media3.session)

    // Coroutines for background work
    implementation(libs.coroutines.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}