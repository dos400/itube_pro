package uz.donfinity.itubepro.domain.usecase

import uz.donfinity.itubepro.domain.error.AppException
import uz.donfinity.itubepro.domain.model.ExtractedVideo
import uz.donfinity.itubepro.domain.repository.VideoRepository

/**
 * Hilt wires this up in :app (see UseCaseModule), so the domain module doesn't need any
 * DI annotations.
 */
class GetVideoStreamsUseCase(private val repository: VideoRepository) {

    /** @throws AppException */
    suspend operator fun invoke(url: String): ExtractedVideo {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) throw AppException.InvalidUrl()
        return repository.getVideo(trimmed)
    }
}
