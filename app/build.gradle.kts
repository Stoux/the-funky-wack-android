import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.sentry)
}

// Load secrets for API keys (not tracked in git)
// Uses secrets.prod.properties for release builds, falls back to secrets.properties
fun loadSecrets(isProd: Boolean): Properties = Properties().apply {
    val prodFile = rootProject.file("secrets.prod.properties")
    val defaultFile = rootProject.file("secrets.properties")

    when {
        isProd && prodFile.exists() -> load(prodFile.inputStream())
        defaultFile.exists() -> load(defaultFile.inputStream())
    }
}

android {
    namespace = "nl.stoux.tfw"
    compileSdk = 36

    defaultConfig {
        applicationId = "nl.stoux.tfw"
        minSdk = 30
        targetSdk = 36

        val basePlatformCode = 1_000_000_000
        versionCode = basePlatformCode + providers.gradleProperty("APP_VERSION_CODE").get().toInt()
        versionName = providers.gradleProperty("APP_VERSION_NAME").get()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            val secrets = loadSecrets(isProd = false)
            manifestPlaceholders["SENTRY_DSN"] = secrets.getProperty("SENTRY_DSN")
                ?: System.getenv("SENTRY_DSN")
                ?: ""
            manifestPlaceholders["SENTRY_ENVIRONMENT"] = "debug"
        }
        release {
            val secrets = loadSecrets(isProd = true)
            manifestPlaceholders["SENTRY_DSN"] = secrets.getProperty("SENTRY_DSN")
                ?: System.getenv("SENTRY_DSN")
                ?: ""
            manifestPlaceholders["SENTRY_ENVIRONMENT"] = "production"

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
    implementation(project(":feature:browser"))
    implementation(project(":feature:player"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // Material icons for NowPlayingBar
    implementation(libs.androidx.compose.material.icons.extended)

    // AppCompat + Material Components for XML theme used by MediaRouter
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Media3 controller to connect to playback service
    implementation(libs.media3.session)

    // Cast framework for CastOptionsProvider
    implementation(libs.play.services.cast.framework)

    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // Sentry Compose integration
    implementation(libs.sentry.compose.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}