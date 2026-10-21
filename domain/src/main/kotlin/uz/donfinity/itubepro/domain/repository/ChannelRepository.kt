package uz.donfinity.itubepro.domain.repository

import uz.donfinity.itubepro.domain.error.AppException
import uz.donfinity.itubepro.domain.model.ChannelVideosPage
import uz.donfinity.itubepro.domain.model.ChannelWithVideos
import uz.donfinity.itubepro.domain.model.PageToken

interface ChannelRepository {
    /** Channel header plus the first page of its videos. @throws AppException */
    suspend fun getChannel(channelUrl: String): ChannelWithVideos

    /** The page after the one that returned [page]. @throws AppException */
    suspend fun moreVideos(page: PageToken): ChannelVideosPage
}
