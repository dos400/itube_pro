package uz.donfinity.itubepro.domain.repository

import uz.donfinity.itubepro.domain.error.AppException
import uz.donfinity.itubepro.domain.model.VideoSummary

interface TrendingRepository {
    /** YouTube's public trending/chart videos (no account involved). @throws AppException */
    suspend fun trending(): List<VideoSummary>
}
