package uz.donfinity.itubepro.data.repository

import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import uz.donfinity.itubepro.data.di.IoDispatcher
import uz.donfinity.itubepro.data.mapper.toVideoSummaries
import uz.donfinity.itubepro.domain.model.VideoSummary
import uz.donfinity.itubepro.domain.repository.TrendingRepository

/**
 * Kiosks are NewPipe's name for YouTube's public lists (ref/NewPipe KioskFragment /
 * ExtractorHelper.getKioskInfo). The default kiosk ("Trending") is tried first. If it fails or
 * comes back empty, the music chart is used instead.
 */
class NewPipeTrendingRepository @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : TrendingRepository {

    private val youtube = ServiceList.YouTube

    override suspend fun trending(): List<VideoSummary> = extract(ioDispatcher) {
        val kiosks = youtube.kioskList
        var lastError: Exception? = null
        for (id in listOf(kiosks.defaultKioskId, FALLBACK_KIOSK).distinct()) {
            try {
                val url = kiosks.getListLinkHandlerFactoryByType(id).fromId(id).url
                val items = KioskInfo.getInfo(youtube, url).relatedItems.toVideoSummaries()
                if (items.isNotEmpty()) return@extract items
            } catch (e: Exception) {
                lastError = e
            }
        }
        lastError?.let { throw it }
        emptyList()
    }

    private companion object {
        const val FALLBACK_KIOSK = "trending_music"
    }
}
