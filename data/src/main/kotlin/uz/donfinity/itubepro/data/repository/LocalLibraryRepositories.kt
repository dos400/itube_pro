package uz.donfinity.itubepro.data.repository

import androidx.room.withTransaction
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import uz.donfinity.itubepro.data.db.AppDatabase
import uz.donfinity.itubepro.data.db.FavoriteEntity
import uz.donfinity.itubepro.data.db.PlaylistEntity
import uz.donfinity.itubepro.data.db.PlaylistItemEntity
import uz.donfinity.itubepro.data.db.PlaylistSummaryRow
import uz.donfinity.itubepro.data.db.VideoEntity
import uz.donfinity.itubepro.data.db.WatchHistoryEntity
import uz.donfinity.itubepro.domain.model.HistoryItem
import uz.donfinity.itubepro.domain.model.Playlist
import uz.donfinity.itubepro.domain.model.PlaylistWithItems
import uz.donfinity.itubepro.domain.model.VideoSummary
import uz.donfinity.itubepro.domain.repository.FavoritesRepository
import uz.donfinity.itubepro.domain.repository.HistoryRepository
import uz.donfinity.itubepro.domain.repository.PlaylistRepository

/*
 * Room's suspend DAO functions run on Room's own background executor, so these repositories
 * don't need withContext(Dispatchers.IO). Flows are also queried off the main thread.
 */

class RoomHistoryRepository @Inject constructor(
    private val db: AppDatabase,
) : HistoryRepository {

    private val history = db.historyDao()

    override fun observeHistory(): Flow<List<HistoryItem>> = history.observe().map { rows ->
        rows.map { HistoryItem(it.video.toSummary(), it.lastWatchedAt, it.positionMs) }
    }

    override suspend fun recordWatch(video: VideoSummary) {
        // Two writes that must succeed together: the video's display data, then the history row.
        db.withTransaction {
            db.videoDao().upsert(video.toEntity())
            val existing = history.get(video.url)
            history.upsert(
                WatchHistoryEntity(
                    videoUrl = video.url,
                    lastWatchedAt = Instant.now(),
                    positionMs = existing?.positionMs ?: 0,
                    watchCount = (existing?.watchCount ?: 0) + 1,
                ),
            )
        }
    }

    override suspend fun savePosition(videoUrl: String, positionMs: Long) = history.updatePosition(videoUrl, positionMs)

    override suspend fun positionOf(videoUrl: String): Long? = history.get(videoUrl)?.positionMs

    override suspend fun remove(videoUrl: String) = history.delete(videoUrl)

    override suspend fun clear() = history.clear()
}

class RoomFavoritesRepository @Inject constructor(
    private val db: AppDatabase,
) : FavoritesRepository {

    private val favorites = db.favoriteDao()

    override fun observeFavorites(): Flow<List<VideoSummary>> =
        favorites.observe().map { list -> list.map { it.toSummary() } }

    override fun isFavorite(videoUrl: String): Flow<Boolean> = favorites.isFavorite(videoUrl).distinctUntilChanged()

    override suspend fun setFavorite(video: VideoSummary, favorite: Boolean) {
        if (favorite) {
            db.withTransaction {
                db.videoDao().upsert(video.toEntity())
                favorites.insert(FavoriteEntity(video.url, Instant.now()))
            }
        } else {
            favorites.delete(video.url)
        }
    }
}

class RoomPlaylistRepository @Inject constructor(
    private val db: AppDatabase,
) : PlaylistRepository {

    private val playlists = db.playlistDao()

    override fun observePlaylists(): Flow<List<Playlist>> =
        playlists.observeSummaries().map { rows -> rows.map { it.toDomain() } }

    override fun observePlaylist(id: Long): Flow<PlaylistWithItems?> =
        combine(playlists.observeSummary(id), playlists.observeItems(id)) { summary, items ->
            summary?.let { PlaylistWithItems(it.toDomain(), items.map { item -> item.toSummary() }) }
        }

    override fun playlistsContaining(videoUrl: String): Flow<Set<Long>> =
        playlists.playlistIdsContaining(videoUrl).map { it.toSet() }.distinctUntilChanged()

    override suspend fun create(name: String): Long =
        playlists.insert(PlaylistEntity(name = name.trim(), createdAt = Instant.now()))

    override suspend fun rename(id: Long, name: String) = playlists.rename(id, name.trim())

    override suspend fun delete(id: Long) = playlists.delete(id)

    override suspend fun addVideo(playlistId: Long, video: VideoSummary) {
        db.withTransaction {
            db.videoDao().upsert(video.toEntity())
            playlists.insertItem(PlaylistItemEntity(playlistId, video.url, playlists.nextPosition(playlistId)))
        }
    }

    override suspend fun removeVideo(playlistId: Long, videoUrl: String) = playlists.deleteItem(playlistId, videoUrl)

    private fun PlaylistSummaryRow.toDomain() = Playlist(id, name, itemCount, thumbnailUrl)
}

internal fun VideoSummary.toEntity() = VideoEntity(
    url = url,
    title = title,
    uploaderName = uploaderName,
    thumbnailUrl = thumbnailUrl,
    durationSeconds = durationSeconds,
    isLive = isLive,
)

internal fun VideoEntity.toSummary() = VideoSummary(
    url = url,
    title = title,
    uploaderName = uploaderName,
    thumbnailUrl = thumbnailUrl,
    durationSeconds = durationSeconds,
    viewCount = null,
    uploadedText = null,
    isLive = isLive,
)
