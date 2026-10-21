package uz.donfinity.itubepro.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    /** When true the player loads only audio, which saves data and battery. */
    val audioOnly: Flow<Boolean>

    suspend fun setAudioOnly(enabled: Boolean)
}
