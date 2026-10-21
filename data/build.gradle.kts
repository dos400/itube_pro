// The only module allowed to see NewPipeExtractor types. Everything it exposes uses
// :domain models.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    // SponsorBlock API responses are parsed with kotlinx.serialization.
    alias(libs.plugins.kotlin.serialization)
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

// Room writes a JSON description of every schema version here. Commit it: future
// migrations can then be checked against the exact previous schema.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    api(project(":domain"))

    implementation(libs.newpipe.extractor)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    coreLibraryDesugaring(libs.android.desugar)
    testImplementation(libs.junit)
}
