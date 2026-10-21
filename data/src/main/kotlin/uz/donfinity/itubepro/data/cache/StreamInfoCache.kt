package uz.donfinity.itubepro.data.cache

import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import org.schabi.newpipe.extractor.stream.StreamInfo

/**
 * Small in-memory LRU cache of extraction results, keyed by URL. The detail screen and the
 * player (Phase 3) both need a StreamInfo for the same video, so this saves a second
 * multi-second network round trip.
 *
 * Idea from ref/NewPipe/app/src/main/java/org/schabi/newpipe/util/InfoCache.java (60-entry
 * LRU) and ServiceHelper.kt (1 hour expiry for YouTube). Stream URLs stay valid for a few
 * hours, so 1 hour leaves a safe margin.
 */
@Singleton
class StreamInfoCache internal constructor(private val clock: () -> Long) {

    @Inject constructor() : this(System::currentTimeMillis)

    private class Entry(val info: StreamInfo, val storedAt: Long)

    // accessOrder = true turns LinkedHashMap into an LRU: reads move an entry to the end,
    // and removeEldestEntry drops the least recently used entry from the front.
    private val entries = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>) =
            size > MAX_ENTRIES
    }

    @Synchronized
    fun get(url: String): StreamInfo? {
        val entry = entries[url] ?: return null
        if (clock() - entry.storedAt > EXPIRY_MILLIS) {
            entries.remove(url)
            return null
        }
        return entry.info
    }

    @Synchronized
    fun put(url: String, info: StreamInfo) {
        entries[url] = Entry(info, clock())
    }

    @Synchronized
    fun remove(url: String) {
        entries.remove(url)
    }

    private companion object {
        const val MAX_ENTRIES = 30
        val EXPIRY_MILLIS = TimeUnit.HOURS.toMillis(1)
    }
}
