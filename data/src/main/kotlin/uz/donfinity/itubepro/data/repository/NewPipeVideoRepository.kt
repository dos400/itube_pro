package uz.donfinity.itubepro.data.repository

import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import uz.donfinity.itubepro.data.cache.StreamInfoCache
import uz.donfinity.itubepro.data.di.IoDispatcher
import uz.donfinity.itubepro.data.mapper.toExtractedVideo
import uz.donfinity.itubepro.data.mapper.toVideoDetails
import uz.donfinity.itubepro.domain.error.AppException
import uz.donfinity.itubepro.domain.model.ExtractedVideo
import uz.donfinity.itubepro.domain.model.VideoDetails
import uz.donfinity.itubepro.domain.repository.VideoRepository

class NewPipeVideoRepository @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val cache: StreamInfoCache,
) : VideoRepository {

    private val youtube = ServiceList.YouTube

    override suspend fun getVideo(url: String): ExtractedVideo =
        extract(ioDispatcher) { streamInfo(url, forceRefresh = false).toExtractedVideo() }

    override suspend fun getDetails(url: String, forceRefresh: Boolean): VideoDetails =
        extract(ioDispatcher) { streamInfo(url, forceRefresh).toVideoDetails() }

    /** Blocking; call only inside [extract]. */
    private fun streamInfo(url: String, forceRefresh: Boolean): StreamInfo {
        // Check the URL first. Otherwise a pasted non-YouTube link fails deep inside
        // the extractor with a generic ParsingException.
        if (!youtube.streamLHFactory.acceptUrl(url)) throw AppException.InvalidUrl()
        if (!forceRefresh) cache.get(url)?.let { return it }
        return StreamInfo.getInfo(youtube, url).also { cache.put(url, it) }
    }
}
