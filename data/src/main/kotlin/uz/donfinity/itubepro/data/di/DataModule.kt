package uz.donfinity.itubepro.data.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import uz.donfinity.itubepro.data.db.AppDatabase
import uz.donfinity.itubepro.data.repository.NewPipeChannelRepository
import uz.donfinity.itubepro.data.repository.NewPipePlaybackRepository
import uz.donfinity.itubepro.data.repository.NewPipeSearchRepository
import uz.donfinity.itubepro.data.repository.NewPipeSubscriptionRepository
import uz.donfinity.itubepro.data.repository.RoomFavoritesRepository
import uz.donfinity.itubepro.data.repository.RoomHistoryRepository
import uz.donfinity.itubepro.data.repository.RoomPlaylistRepository
import uz.donfinity.itubepro.data.repository.SponsorBlockApiRepository
import uz.donfinity.itubepro.data.repository.NewPipeVideoRepository
import uz.donfinity.itubepro.data.settings.DataStoreSettingsRepository
import uz.donfinity.itubepro.domain.repository.ChannelRepository
import uz.donfinity.itubepro.domain.repository.FavoritesRepository
import uz.donfinity.itubepro.domain.repository.HistoryRepository
import uz.donfinity.itubepro.domain.repository.PlaybackRepository
import uz.donfinity.itubepro.domain.repository.PlaylistRepository
import uz.donfinity.itubepro.domain.repository.SearchRepository
import uz.donfinity.itubepro.domain.repository.SettingsRepository
import uz.donfinity.itubepro.domain.repository.SponsorBlockRepository
import uz.donfinity.itubepro.domain.repository.SubscriptionRepository
import uz.donfinity.itubepro.domain.repository.VideoRepository

/** Marks the dispatcher for blocking I/O. Tests can swap in a test dispatcher. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * One OkHttpClient for the whole app (extractor, and later Coil). Each client owns a
     * connection pool and thread pool, so sharing one saves sockets and memory.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Exactly one DataStore instance per file per process, or DataStore throws. Hence @Singleton. */
    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }

    /** One database instance per process. Room keeps a connection pool behind it. */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "itubepro.db").build()

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindVideoRepository(impl: NewPipeVideoRepository): VideoRepository

    @Binds
    @Singleton
    abstract fun bindSearchRepository(impl: NewPipeSearchRepository): SearchRepository

    @Binds
    @Singleton
    abstract fun bindPlaybackRepository(impl: NewPipePlaybackRepository): PlaybackRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindHistoryRepository(impl: RoomHistoryRepository): HistoryRepository

    @Binds
    @Singleton
    abstract fun bindFavoritesRepository(impl: RoomFavoritesRepository): FavoritesRepository

    @Binds
    @Singleton
    abstract fun bindPlaylistRepository(impl: RoomPlaylistRepository): PlaylistRepository

    @Binds
    @Singleton
    abstract fun bindSubscriptionRepository(impl: NewPipeSubscriptionRepository): SubscriptionRepository

    @Binds
    @Singleton
    abstract fun bindSponsorBlockRepository(impl: SponsorBlockApiRepository): SponsorBlockRepository

    @Binds
    @Singleton
    abstract fun bindChannelRepository(impl: NewPipeChannelRepository): ChannelRepository
}
