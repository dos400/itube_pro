plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// versionCode = number of commits on HEAD, so every new commit builds a higher version and
// Android accepts it as an update. release.yml tags each GitHub release v<versionCode> the
// same way, and the in-app updater compares against it. Falls back to 1 without git.
val commitCount: Int = runCatching {
    providers.exec { commandLine("git", "rev-list", "--count", "HEAD") }
        .standardOutput.asText.get().trim().toInt()
}.getOrDefault(1)

// GitHub "owner/name" to update from. CI passes -PupdateRepo=$GITHUB_REPOSITORY; local
// builds leave it empty, which turns the update check off.
val updateRepo: String = providers.gradleProperty("updateRepo").getOrElse("")

android {
    namespace = "uz.donfinity.itubepro"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "uz.donfinity.itubepro"
        minSdk = 26
        targetSdk = 37
        versionCode = commitCount
        versionName = "1.$commitCount"
        buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
            // Signed with the debug key on purpose: the phone already has a debug build
            // signed with ~/.android/debug.keystore, and Android only installs an update
            // signed with the same key. CI restores that same keystore from a secret.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Required because :data (NewPipeExtractor) uses Java APIs above minSdk.
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":core:player"))
    // Only Hilt needs :data at compile time (to see its @Module classes). UI code must
    // never import from uz.donfinity.itubepro.data.* except the app-start initializer.
    implementation(project(":data"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    coreLibraryDesugaring(libs.android.desugar)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
