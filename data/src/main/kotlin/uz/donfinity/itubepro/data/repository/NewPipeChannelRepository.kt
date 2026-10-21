package uz.donfinity.itubepro.data.repository

import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import uz.donfinity.itubepro.data.di.IoDispatcher
import uz.donfinity.itubepro.data.mapper.PageTokenCodec
import uz.donfinity.itubepro.data.mapper.preferredUrl
import uz.donfinity.itubepro.data.mapper.toVideoSummaries
import uz.donfinity.itubepro.domain.error.AppException
import uz.donfinity.itubepro.domain.model.ChannelDetails
import uz.donfinity.itubepro.domain.model.ChannelVideosPage
import uz.donfinity.itubepro.domain.model.ChannelWithVideos
import uz.donfinity.itubepro.domain.model.PageToken
import uz.donfinity.itubepro.domain.repository.ChannelRepository

/**
 * Same flow as ref/NewPipe (ChannelFragment + ChannelTabFragment / ExtractorHelper.getChannelTab):
 * ChannelInfo for the header and the list of tabs, then ChannelTabInfo for the "videos" tab.
 */
class NewPipeChannelRepository @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ChannelRepository {

    private val youtube = ServiceList.YouTube

    override suspend fun getChannel(channelUrl: String): ChannelWithVideos = extract(ioDispatcher) {
        if (!youtube.channelLHFactory.acceptUrl(channelUrl)) throw AppException.InvalidUrl()
        val info = ChannelInfo.getInfo(youtube, channelUrl)
        // Prefer the Videos tab. Some channels only have e.g. Shorts or Live, so fall back to
        // the first tab rather than showing nothing.
        val tab = info.tabs.firstOrNull { ChannelTabs.VIDEOS in it.contentFilters } ?: info.tabs.firstOrNull()
        val firstPage = tab?.let { handler ->
            val tabInfo = ChannelTabInfo.getInfo(youtube, handler)
            ChannelVideosPage(tabInfo.relatedItems.toVideoSummaries(), PageTokenCodec.encodeWithTab(handler, tabInfo.nextPage))
        } ?: ChannelVideosPage(emptyList(), nextPage = null)

        ChannelWithVideos(
            channel = ChannelDetails(
                url = info.url,
                name = info.name,
                avatarUrl = info.avatars.preferredUrl(Image.ResolutionLevel.MEDIUM),
                bannerUrl = info.banners.preferredUrl(Image.ResolutionLevel.MEDIUM),
                subscriberCount = info.subscriberCount.takeIf { it >= 0 },
                verified = info.isVerified,
                description = info.description.orEmpty(),
            ),
            firstPage = firstPage,
        )
    }

    override suspend fun moreVideos(page: PageToken): ChannelVideosPage = extract(ioDispatcher) {
        val (tab, next) = PageTokenCodec.decodeWithTab(page)
        val more = ChannelTabInfo.getMoreItems(youtube, tab, next)
        ChannelVideosPage(more.items.toVideoSummaries(), PageTokenCodec.encodeWithTab(tab, more.nextPage))
    }
}
