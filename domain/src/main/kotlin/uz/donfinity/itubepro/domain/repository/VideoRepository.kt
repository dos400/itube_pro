package uz.donfinity.itubepro.domain.repository

import uz.donfinity.itubepro.domain.error.AppException
import uz.donfinity.itubepro.domain.model.ExtractedVideo

interface VideoRepository {
    /**
     * Extracts metadata and streams for a video URL. This is a network call, so it may
     * take several seconds.
     *
     * @throws AppException on any failure.
     */
    suspend fun getVideo(url: String): ExtractedVideo
}
