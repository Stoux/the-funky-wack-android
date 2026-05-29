plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
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

        val basePlatformCode = 2_000_000_000
        versionCode = basePlatformCode + providers.gradleProperty("APP_VERSION_CODE").get().toInt()
        versionName = providers.gradleProperty("APP_VERSION_NAME").get()

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

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":service:playback"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    // For Android Automotive specific functionality
    implementation(libs.androidx.car.app)
    implementation(libs.androidx.car.app.automotive)

    // Hilt DI for accessing shared repositories
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Media3 controller to control playback service from templates
    implementation(libs.media3.session)

    // Retrofit (for HttpException in login error handling)
    implementation(libs.retrofit)

    // Coroutines for background work
    implementation(libs.coroutines.android)

    // Compose for the device-code login activity
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.hilt.navigation.compose)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}