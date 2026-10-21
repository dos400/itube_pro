// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    dependencies {
        // AGP 9 compiles Kotlin itself and ships with an older Kotlin Gradle Plugin (KGP).
        // Putting our KGP on the classpath makes AGP use the Kotlin version from the catalog.
        // https://developer.android.com/build/releases/agp-9-0-0-release-notes#runtime-dependency-on-kotlin-gradle-plugin-upgrade
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
