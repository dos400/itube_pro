package uz.donfinity.itubepro.data.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import uz.donfinity.itubepro.data.repository.NewPipeVideoRepository
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
}
