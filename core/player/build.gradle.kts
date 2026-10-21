// Playback: PlaybackService (MediaSessionService), ExoPlayer setup, MediaSource building.
// Filled in during Phase 3. Depends only on :domain, never on :data or NewPipe.
plugins {
    alias(libs.plugins.android.library)
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
}
