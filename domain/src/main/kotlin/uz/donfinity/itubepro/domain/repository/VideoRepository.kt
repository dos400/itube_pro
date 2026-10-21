package uz.donfinity.itubepro.domain.repository

import uz.donfinity.itubepro.domain.error.AppException
import uz.donfinity.itubepro.domain.model.ExtractedVideo
import uz.donfinity.itubepro.domain.model.VideoDetails

interface VideoRepository {
    /**
     * Extracts metadata and streams for a video URL. This is a network call, so it may
     * take several seconds.
     *
     * @throws AppException on any failure.
     */
    suspend fun getVideo(url: String): ExtractedVideo

    /**
     * Metadata, description and related videos for the detail screen.
     *
     * @param forceRefresh skip the in-memory cache (used by "Retry").
     * @throws AppException on any failure.
     */
    suspend fun getDetails(url: String, forceRefresh: Boolean = false): VideoDetails
}
