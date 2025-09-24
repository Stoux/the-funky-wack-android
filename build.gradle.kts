// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.library) apply false
}

// Configure all subprojects that use the Kotlin Android plugin
subprojects {
    afterEvaluate {
        plugins.withId("org.jetbrains.kotlin.android") {
            the<org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension>().jvmToolchain(17)
        }

        // Also configure it for pure JVM libraries like :core:common might be
        plugins.withId("org.jetbrains.kotlin.jvm") {
            the<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension>().jvmToolchain(17)
        }
    }
}