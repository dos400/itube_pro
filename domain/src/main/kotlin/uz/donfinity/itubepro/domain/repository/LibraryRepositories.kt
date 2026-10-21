package uz.donfinity.itubepro.domain.repository

import java.time.Instant
import kotlinx.coroutines.flow.Flow
import uz.donfinity.itubepro.domain.error.AppException
import uz.donfinity.itubepro.domain.model.FeedItem
import uz.donfinity.itubepro.domain.model.FeedRefreshResult
import uz.donfinity.itubepro.domain.model.HistoryItem
import uz.donfinity.itubepro.domain.model.Playlist
import uz.donfinity.itubepro.domain.model.PlaylistWithItems
import uz.donfinity.itubepro.domain.model.Subscription
import uz.donfinity.itubepro.domain.model.VideoSummary

/*
 * Local library, stored on the device. Every observeX() is a Flow that emits again whenever
 * the underlying data changes, so screens update themselves after any write.
 */

interface HistoryRepository {
    /** Most recently watched first. */
    fun observeHistory(): Flow<List<HistoryItem>>

    /** Called when a video starts playing: adds it to history or moves it to the top. */
    suspend fun recordWatch(video: VideoSummary)

    suspend fun savePosition(videoUrl: String, positionMs: Long)

    /** Where to resume [videoUrl], or null if it was never watched. */
    suspend fun positionOf(videoUrl: String): Long?

    suspend fun remove(videoUrl: String)

    suspend fun clear()
}

interface FavoritesRepository {
    /** Most recently added first. */
    fun observeFavorites(): Flow<List<VideoSummary>>

    fun isFavorite(videoUrl: String): Flow<Boolean>

    suspend fun setFavorite(video: VideoSummary, favorite: Boolean)
}

interface PlaylistRepository {
    fun observePlaylists(): Flow<List<Playlist>>

    /** Emits null if the playlist was deleted. */
    fun observePlaylist(id: Long): Flow<PlaylistWithItems?>

    /** IDs of the playlists that contain [videoUrl] (for the "Save to playlist" checkboxes). */
    fun playlistsContaining(videoUrl: String): Flow<Set<Long>>

    /** @return the new playlist's ID. */
    suspend fun create(name: String): Long

    suspend fun rename(id: Long, name: String)

    suspend fun delete(id: Long)

    /** Adds at the end. Adding a video that's already in the playlist does nothing. */
    suspend fun addVideo(playlistId: Long, video: VideoSummary)

    suspend fun removeVideo(playlistId: Long, videoUrl: String)
}

interface SubscriptionRepository {
    /** Alphabetical. */
    fun observeSubscriptions(): Flow<List<Subscription>>

    fun isSubscribed(channelUrl: String): Flow<Boolean>

    suspend fun subscribe(subscription: Subscription)

    /** Also removes that channel's videos from the feed. */
    suspend fun unsubscribe(channelUrl: String)

    /** Newest first, from all subscribed channels. */
    fun observeFeed(): Flow<List<FeedItem>>

    /** When the feed was last refreshed successfully, or null if never. */
    fun observeLastFeedRefresh(): Flow<Instant?>

    /**
     * Downloads the latest videos of every subscribed channel. One failing channel doesn't
     * fail the whole refresh; it's counted in [FeedRefreshResult.failedChannels].
     *
     * @throws AppException only if nothing could be refreshed because of a general error
     *   (e.g. no network).
     */
    suspend fun refreshFeed(): FeedRefreshResult
}
