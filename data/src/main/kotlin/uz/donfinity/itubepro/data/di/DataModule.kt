package uz.donfinity.itubepro.data.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import uz.donfinity.itubepro.data.repository.NewPipePlaybackRepository
import uz.donfinity.itubepro.data.repository.NewPipeSearchRepository
import uz.donfinity.itubepro.data.repository.NewPipeVideoRepository
import uz.donfinity.itubepro.data.settings.DataStoreSettingsRepository
import uz.donfinity.itubepro.domain.repository.PlaybackRepository
import uz.donfinity.itubepro.domain.repository.SearchRepository
import uz.donfinity.itubepro.domain.repository.SettingsRepository
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
}
