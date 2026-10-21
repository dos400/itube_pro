// The only module allowed to see NewPipeExtractor types. Everything it exposes uses
// :domain models.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "uz.donfinity.itubepro.data"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // NewPipeExtractor calls Java APIs newer than API 26 (e.g. Stream.toList() is API 34).
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    api(project(":domain"))

    implementation(libs.newpipe.extractor)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    coreLibraryDesugaring(libs.android.desugar)
    testImplementation(libs.junit)
}
