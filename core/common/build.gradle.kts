import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
}

// Load secrets for API keys (not tracked in git).
// Uses secrets.prod.properties for release builds, falls back to secrets.properties.
fun loadSecrets(isProd: Boolean): Properties = Properties().apply {
    val prodFile = rootProject.file("secrets.prod.properties")
    val defaultFile = rootProject.file("secrets.properties")
    when {
        isProd && prodFile.exists() -> load(prodFile.inputStream())
        defaultFile.exists() -> load(defaultFile.inputStream())
    }
}

fun reverbAppKey(isProd: Boolean): String =
    loadSecrets(isProd).getProperty("REVERB_APP_KEY")
        ?: System.getenv("REVERB_APP_KEY")
        ?: ""

android {
    namespace = "nl.stoux.tfw.core.common"
    compileSdk = 36

    defaultConfig {
        minSdk = 30

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            buildConfigField("String", "REVERB_APP_KEY", "\"${reverbAppKey(isProd = false)}\"")
        }
        release {
            buildConfigField("String", "REVERB_APP_KEY", "\"${reverbAppKey(isProd = true)}\"")

            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    // Existing AndroidX base deps (optional in core lib)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Room (database)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Retrofit (networking) + Kotlinx Serialization converter
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)

    // Kotlinx Serialization (JSON)
    implementation(libs.serialization.json)

    // OkHttp (HTTP client)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Encrypted SharedPreferences (for auth tokens and device ID)
    implementation(libs.androidx.security.crypto)

    // Pusher Java client (talks to Laravel Reverb for listen-along)
    implementation(libs.pusher.java.client)

    // Hilt DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Test deps
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
