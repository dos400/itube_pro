package uz.donfinity.itubepro.domain.repository

import kotlinx.coroutines.flow.Flow
import uz.donfinity.itubepro.domain.model.SegmentCategory

interface SettingsRepository {
    /** When true the player loads only audio, which saves data and battery. */
    val audioOnly: Flow<Boolean>

    suspend fun setAudioOnly(enabled: Boolean)

    /** When a video ends, continue with the queued "up next" video. On by default. */
    val autoplayNext: Flow<Boolean>

    suspend fun setAutoplayNext(enabled: Boolean)

    /**
     * Skip sponsor segments etc. Off by default: turning it on sends a hash prefix of every
     * played video's ID to a third-party server (sponsor.ajay.app).
     */
    val sponsorBlockEnabled: Flow<Boolean>

    suspend fun setSponsorBlockEnabled(enabled: Boolean)

    /** Which categories to skip. Defaults to [SegmentCategory.DEFAULTS]. */
    val sponsorBlockCategories: Flow<Set<SegmentCategory>>

    suspend fun setSponsorBlockCategories(categories: Set<SegmentCategory>)
}
