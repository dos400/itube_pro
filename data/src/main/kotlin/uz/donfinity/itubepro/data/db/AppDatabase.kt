package uz.donfinity.itubepro.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.time.Instant

/**
 * Schema version history (JSON in data/schemas/). When changing an entity:
 * 1. bump [version],
 * 2. add a Migration (or an `autoMigrations` entry) from the previous version,
 * 3. commit the newly generated schema JSON.
 * We never use fallbackToDestructiveMigration: it would wipe the user's library.
 */
@Database(
    version = 1,
    exportSchema = true,
    entities = [
        VideoEntity::class,
        WatchHistoryEntity::class,
        FavoriteEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        SubscriptionEntity::class,
        FeedItemEntity::class,
        FeedStateEntity::class,
    ],
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao
    abstract fun historyDao(): HistoryDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun feedDao(): FeedDao
}

/** SQLite has no date type; store Instants as epoch milliseconds (sortable integers). */
class Converters {
    @TypeConverter
    fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)
}
