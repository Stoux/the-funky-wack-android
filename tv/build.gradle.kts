plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "nl.stoux.tfw.tv"
    compileSdk = 36

    defaultConfig {
        applicationId = "nl.stoux.tfw"
        minSdk = 30
        targetSdk = 36

        // Platform version codes: phone=1B, automotive=2B, TV=300M (since 3B exceeds Int.MAX_VALUE)
        val basePlatformCode = 300_000_000
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
    implementation(project(":feature:player"))

    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // TV Compose
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt DI
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // Media3 controller
    implementation(libs.media3.session)

    // Coroutines
    implementation(libs.coroutines.android)

    // Serialization (for navigation)
    implementation(libs.serialization.json)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Image loading
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
