package uz.donfinity.itubepro.data

import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import uz.donfinity.itubepro.data.network.OkHttpDownloader

/**
 * NewPipeExtractor keeps its configuration in static fields, so it has to be initialised
 * once per process before the first extraction. Call [initialize] from Application.onCreate.
 * That also covers process death, because Android always creates the Application before
 * restoring any Activity or Service.
 *
 * Idea from ref/NewPipe/app/src/main/java/org/schabi/newpipe/App.kt (NewPipe.init call).
 */
@Singleton
class NewPipeInitializer @Inject constructor(
    private val downloader: OkHttpDownloader,
) {
    private val initialized = AtomicBoolean(false)

    fun initialize() {
        if (!initialized.compareAndSet(false, true)) return
        val locale = Locale.getDefault()
        NewPipe.init(
            downloader,
            Localization.fromLocale(locale),
            // An empty country would make YouTube fall back to US content.
            locale.country.takeIf { it.isNotEmpty() }?.let(::ContentCountry)
                ?: ContentCountry.DEFAULT,
        )
    }
}
