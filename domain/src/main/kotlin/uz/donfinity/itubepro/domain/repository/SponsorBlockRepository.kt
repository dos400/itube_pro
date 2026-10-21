package uz.donfinity.itubepro.domain.repository

import uz.donfinity.itubepro.domain.error.AppException
import uz.donfinity.itubepro.domain.model.SegmentCategory
import uz.donfinity.itubepro.domain.model.SkipSegment

interface SponsorBlockRepository {
    /**
     * Community-submitted segments of [videoUrl] in the given [categories], unsorted and
     * unmerged (see SkipSegments.merge). Empty if nobody submitted any.
     *
     * @throws AppException on network or parsing failure. Callers should just play without skipping.
     */
    suspend fun segments(videoUrl: String, categories: Set<SegmentCategory>): List<SkipSegment>
}
