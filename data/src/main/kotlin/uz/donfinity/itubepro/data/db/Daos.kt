package uz.donfinity.itubepro.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/*
 * Room generates the code for these interfaces at compile time (KSP). Queries are checked
 * against the schema while compiling, so a typo in a column name is a build error, not a crash.
 * Functions returning Flow re-run their query whenever a table they read changes.
 */

@Dao
interface VideoDao {
    @Upsert
    suspend fun upsert(video: VideoEntity)
}

/** Join result: a history row plus its video's display data. */
data class HistoryRow(
    @Embedded val video: VideoEntity,
    val lastWatchedAt: Instant,
    val positionMs: Long,
)

@Dao
interface HistoryDao {
    @Query(
        """
        SELECT v.*, h.lastWatchedAt, h.positionMs FROM watch_history h
        JOIN videos v ON v.url = h.videoUrl
        ORDER BY h.lastWatchedAt DESC
        """,
    )
    fun observe(): Flow<List<HistoryRow>>

    @Query("SELECT * FROM watch_history WHERE videoUrl = :url")
    suspend fun get(url: String): WatchHistoryEntity?

    @Upsert
    suspend fun upsert(entry: WatchHistoryEntity)

    @Query("UPDATE watch_history SET positionMs = :positionMs WHERE videoUrl = :url")
    suspend fun updatePosition(url: String, positionMs: Long)

    @Query("DELETE FROM watch_history WHERE videoUrl = :url")
    suspend fun delete(url: String)

    @Query("DELETE FROM watch_history")
    suspend fun clear()
}

@Dao
interface FavoriteDao {
    @Query("SELECT v.* FROM favorites f JOIN videos v ON v.url = f.videoUrl ORDER BY f.addedAt DESC")
    fun observe(): Flow<List<VideoEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE videoUrl = :url)")
    fun isFavorite(url: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE videoUrl = :url")
    suspend fun delete(url: String)
}

/** A playlist with its item count and cover (first video's thumbnail). */
data class PlaylistSummaryRow(
    val id: Long,
    val name: String,
    val itemCount: Int,
    val thumbnailUrl: String?,
)

@Dao
interface PlaylistDao {
    @Query(
        """
        SELECT p.id, p.name,
            (SELECT COUNT(*) FROM playlist_items i WHERE i.playlistId = p.id) AS itemCount,
            (SELECT v.thumbnailUrl FROM playlist_items i JOIN videos v ON v.url = i.videoUrl
                WHERE i.playlistId = p.id ORDER BY i.position LIMIT 1) AS thumbnailUrl
        FROM playlists p
        ORDER BY p.createdAt DESC
        """,
    )
    fun observeSummaries(): Flow<List<PlaylistSummaryRow>>

    @Query(
        """
        SELECT p.id, p.name,
            (SELECT COUNT(*) FROM playlist_items i WHERE i.playlistId = p.id) AS itemCount,
            (SELECT v.thumbnailUrl FROM playlist_items i JOIN videos v ON v.url = i.videoUrl
                WHERE i.playlistId = p.id ORDER BY i.position LIMIT 1) AS thumbnailUrl
        FROM playlists p WHERE p.id = :id
        """,
    )
    fun observeSummary(id: Long): Flow<PlaylistSummaryRow?>

    @Query(
        """
        SELECT v.* FROM playlist_items i JOIN videos v ON v.url = i.videoUrl
        WHERE i.playlistId = :id ORDER BY i.position
        """,
    )
    fun observeItems(id: Long): Flow<List<VideoEntity>>

    @Query("SELECT playlistId FROM playlist_items WHERE videoUrl = :url")
    fun playlistIdsContaining(url: String): Flow<List<Long>>

    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_items WHERE playlistId = :id")
    suspend fun nextPosition(id: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItem(item: PlaylistItemEntity)

    @Query("DELETE FROM playlist_items WHERE playlistId = :id AND videoUrl = :url")
    suspend fun deleteItem(id: Long, url: String)
}

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions ORDER BY name COLLATE NOCASE")
    fun observe(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions")
    suspend fun all(): List<SubscriptionEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM subscriptions WHERE channelUrl = :url)")
    fun isSubscribed(url: String): Flow<Boolean>

    @Upsert
    suspend fun upsert(subscription: SubscriptionEntity)

    @Query("DELETE FROM subscriptions WHERE channelUrl = :url")
    suspend fun delete(url: String)
}

@Dao
interface FeedDao {
    /** Undated items last; capped so a huge feed doesn't load everything at once. */
    @Query("SELECT * FROM feed_items ORDER BY uploadedAt IS NULL, uploadedAt DESC LIMIT 500")
    fun observe(): Flow<List<FeedItemEntity>>

    /** Replaces one channel's items atomically, so the feed never shows a half-updated channel. */
    @Transaction
    suspend fun replaceChannel(channelUrl: String, items: List<FeedItemEntity>) {
        deleteChannel(channelUrl)
        insertAll(items)
    }

    @Query("DELETE FROM feed_items WHERE channelUrl = :channelUrl")
    suspend fun deleteChannel(channelUrl: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<FeedItemEntity>)

    @Query("SELECT lastRefreshAt FROM feed_state WHERE id = 0")
    fun observeLastRefresh(): Flow<Instant?>

    @Upsert
    suspend fun setState(state: FeedStateEntity)
}
