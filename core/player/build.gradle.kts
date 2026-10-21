// Playback: PlaybackService (MediaSessionService), ExoPlayer setup, MediaSource building.
// Depends only on :domain, never on :data or NewPipe.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "uz.donfinity.itubepro.core.player"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":domain"))

    // api: :app uses MediaController/PlayerView types that come from these.
    api(libs.androidx.media3.session)
    api(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.dash)
    // Live streams: YouTube serves them only as HLS.
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
