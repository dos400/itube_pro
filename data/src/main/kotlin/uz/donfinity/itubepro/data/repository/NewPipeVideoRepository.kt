package uz.donfinity.itubepro.data.repository

import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import uz.donfinity.itubepro.data.di.IoDispatcher
import uz.donfinity.itubepro.data.error.toAppException
import uz.donfinity.itubepro.data.mapper.toExtractedVideo
import uz.donfinity.itubepro.domain.error.AppException
import uz.donfinity.itubepro.domain.model.ExtractedVideo
import uz.donfinity.itubepro.domain.repository.VideoRepository

class NewPipeVideoRepository @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : VideoRepository {

    private val youtube = ServiceList.YouTube

    override suspend fun getVideo(url: String): ExtractedVideo = withContext(ioDispatcher) {
        try {
            // Check the URL first. Otherwise a pasted non-YouTube link fails deep inside
            // the extractor with a generic ParsingException.
            if (!youtube.streamLHFactory.acceptUrl(url)) throw AppException.InvalidUrl()
            StreamInfo.getInfo(youtube, url).toExtractedVideo()
        } catch (e: CancellationException) {
            // Never swallow cancellation, or the coroutine keeps running after the
            // screen that started it is gone.
            throw e
        } catch (e: Exception) {
            throw e.toAppException()
        }
    }
}
