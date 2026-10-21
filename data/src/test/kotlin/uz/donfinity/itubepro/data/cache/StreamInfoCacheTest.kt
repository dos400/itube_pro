package uz.donfinity.itubepro.data.cache

import java.util.concurrent.TimeUnit
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType

class StreamInfoCacheTest {

    // The cache takes its clock as a parameter, so we can move time forward instantly.
    private var now = 0L
    private val cache = StreamInfoCache { now }

    private fun info(id: String) = StreamInfo(0, "https://youtu.be/$id", "https://youtu.be/$id", StreamType.VIDEO_STREAM, id, id, 0)

    @Test
    fun `returns stored info until it expires after one hour`() {
        val info = info("a")
        cache.put("a", info)

        now += TimeUnit.MINUTES.toMillis(59)
        assertSame(info, cache.get("a"))

        now += TimeUnit.MINUTES.toMillis(2)
        assertNull(cache.get("a"))
    }

    @Test
    fun `evicts least recently used entry beyond capacity`() {
        repeat(30) { cache.put("v$it", info("v$it")) }
        cache.get("v0") // touch v0, so v1 becomes the oldest

        cache.put("new", info("new"))

        assertNotNull("recently used entry stays", cache.get("v0"))
        assertNull("oldest entry evicted", cache.get("v1"))
    }

    @Test
    fun `remove forgets the entry`() {
        cache.put("a", info("a"))
        cache.remove("a")
        assertNull(cache.get("a"))
    }
}
