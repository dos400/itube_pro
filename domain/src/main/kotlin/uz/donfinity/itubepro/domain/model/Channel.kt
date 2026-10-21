package uz.donfinity.itubepro.domain.model

data class ChannelDetails(
    val url: String,
    val name: String,
    val avatarUrl: String?,
    val bannerUrl: String?,
    /** Null when hidden or unknown. */
    val subscriberCount: Long?,
    val verified: Boolean,
    val description: String,
)

/** A page of a channel's videos. [nextPage] is null on the last page. */
data class ChannelVideosPage(
    val items: List<VideoSummary>,
    val nextPage: PageToken?,
)

data class ChannelWithVideos(
    val channel: ChannelDetails,
    val firstPage: ChannelVideosPage,
)
