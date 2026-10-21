package uz.donfinity.itubepro

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import okhttp3.OkHttpClient
import uz.donfinity.itubepro.data.NewPipeInitializer

/**
 * @HiltAndroidApp generates the app-wide (singleton) dependency container and attaches
 * it to this Application. Fields marked @Inject are filled in during super.onCreate().
 *
 * Implementing [SingletonImageLoader.Factory] tells Coil how to build its global
 * ImageLoader. We give it the same OkHttpClient as the extractor, so both share one
 * connection pool (same idea as ref/NewPipe/.../App.kt).
 */
@HiltAndroidApp
class ItubeApp : Application(), SingletonImageLoader.Factory {

    @Inject
    lateinit var newPipeInitializer: NewPipeInitializer

    @Inject
    lateinit var okHttpClient: OkHttpClient

    override fun onCreate() {
        super.onCreate()
        newPipeInitializer.initialize()
    }

    // Called lazily on the first image request, which is always after onCreate().
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient })) }
            .crossfade(true)
            .build()
}
