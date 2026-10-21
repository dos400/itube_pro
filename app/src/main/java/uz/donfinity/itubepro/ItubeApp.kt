package uz.donfinity.itubepro

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import uz.donfinity.itubepro.data.NewPipeInitializer

/**
 * @HiltAndroidApp generates the app-wide (singleton) dependency container and attaches
 * it to this Application. Fields marked @Inject are filled in during super.onCreate().
 */
@HiltAndroidApp
class ItubeApp : Application() {

    @Inject
    lateinit var newPipeInitializer: NewPipeInitializer

    override fun onCreate() {
        super.onCreate()
        newPipeInitializer.initialize()
    }
}
