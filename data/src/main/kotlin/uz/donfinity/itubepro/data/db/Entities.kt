package uz.donfinity.itubepro.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/*
 * Schema overview (idea from ref/NewPipe/.../database: one shared "streams" table that other
 * tables reference; ours uses the video URL as the key):
 *
 *   videos ◄── watch_history   (1 row per video: last watched + resume position)
 *          ◄── favorites
 *          ◄── playlist_items ──► playlists
 *   subscriptions ◄── feed_items  (feed rows live and die with their subscription)
 *
 * ForeignKey + CASCADE lets the database clean up for us: deleting a playlist deletes its
 * items, and unsubscribing deletes that channel's feed.
 */

/** Display data for any video we store locally, shared by history, favorites and playlists. */
@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey val url: String,
    val title: String,
    val uploaderName: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long?,
    val isLive: Boolean,
)

@Entity(
    tableName = "watch_history",
    foreignKeys = [ForeignKey(VideoEntity::class, ["url"], ["videoUrl"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("lastWatchedAt")],
)
data class WatchHistoryEntity(
    @PrimaryKey val videoUrl: String,
    val lastWatchedAt: Instant,
    val positionMs: Long,
    val watchCount: Int,
)

@Entity(
    tableName = "favorites",
    foreignKeys = [ForeignKey(VideoEntity::class, ["url"], ["videoUrl"], onDelete = ForeignKey.CASCADE)],
)
data class FavoriteEntity(
    @PrimaryKey val videoUrl: String,
    val addedAt: Instant,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Instant,
)

/** The (playlistId, videoUrl) primary key means a video appears in a playlist at most once. */
@Entity(
    tableName = "playlist_items",
    primaryKeys = ["playlistId", "videoUrl"],
    foreignKeys = [
        ForeignKey(PlaylistEntity::class, ["id"], ["playlistId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(VideoEntity::class, ["url"], ["videoUrl"], onDelete = ForeignKey.CASCADE),
    ],
    // Foreign key columns should be indexed, or every delete in the parent table scans
    // this whole table (Room warns about it at compile time).
    indices = [Index("videoUrl")],
)
data class PlaylistItemEntity(
    val playlistId: Long,
    val videoUrl: String,
    val position: Int,
)

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val channelUrl: String,
    val name: String,
    val avatarUrl: String?,
    val subscribedAt: Instant,
)

@Entity(
    tableName = "feed_items",
    foreignKeys = [ForeignKey(SubscriptionEntity::class, ["channelUrl"], ["channelUrl"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("channelUrl"), Index("uploadedAt")],
)
data class FeedItemEntity(
    @PrimaryKey val videoUrl: String,
    val channelUrl: String,
    val title: String,
    val uploaderName: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long?,
    val viewCount: Long?,
    val uploadedAt: Instant?,
    val isLive: Boolean,
)

/** Single-row table: when the feed was last refreshed. */
@Entity(tableName = "feed_state")
data class FeedStateEntity(
    @PrimaryKey val id: Int = 0,
    val lastRefreshAt: Instant,
)
