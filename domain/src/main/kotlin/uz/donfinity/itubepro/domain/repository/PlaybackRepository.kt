package uz.donfinity.itubepro.domain.repository

import uz.donfinity.itubepro.domain.error.AppException
import uz.donfinity.itubepro.domain.model.PlaybackSource

interface PlaybackRepository {
    /**
     * Extracts and selects the streams to play for [videoUrl].
     *
     * @param forceRefresh bypass the cache. Use it after HTTP 403 (expired or IP-bound URL).
     * @throws AppException
     */
    suspend fun resolve(videoUrl: String, forceRefresh: Boolean = false): PlaybackSource
}
