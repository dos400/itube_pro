package uz.donfinity.itubepro.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.donfinity.itubepro.domain.model.MediaStream
import uz.donfinity.itubepro.domain.model.MediaStream.AudioTrackType
import uz.donfinity.itubepro.domain.model.MediaStream.Delivery
import uz.donfinity.itubepro.domain.model.MediaStream.Kind

/** Our counterpart of ref/NewPipe/app/src/test/.../util/ListHelperTest.java. */
class StreamSelectorTest {

    private var nextId = 0

    private fun video(height: Int, codec: String, kind: Kind = Kind.VIDEO_ONLY, delivery: Delivery = Delivery.PROGRESSIVE) =
        MediaStream(
            id = (nextId++).toString(),
            kind = kind,
            delivery = delivery,
            content = "https://example.com/$height-$codec",
            format = null,
            codec = codec,
            qualityLabel = "${height}p",
            bitrate = height * 1000,
            height = height,
            audioTrackName = null,
        )

    private fun audio(bitrate: Int, type: AudioTrackType? = null) = MediaStream(
        id = (nextId++).toString(),
        kind = Kind.AUDIO,
        delivery = Delivery.PROGRESSIVE,
        content = "https://example.com/a$bitrate",
        format = null,
        codec = "opus",
        qualityLabel = "",
        bitrate = bitrate,
        height = 0,
        audioTrackName = null,
        audioTrackType = type,
    )

    @Test
    fun `prefers H264 family when it reaches 1080p, sorted low to high`() {
        val streams = listOf(
            video(1080, "avc1.640028"), video(360, "avc1.4D401E"), video(720, "avc1.4D401F"),
            video(2160, "vp09.00.51.08"), video(1080, "vp09.00.40.08"),
            audio(128_000),
        )

        val selection = StreamSelector.select(streams)

        assertEquals(listOf(360, 720, 1080), selection.videos.map { it.height })
        assertTrue(selection.videos.all { it.codec!!.startsWith("avc1") })
    }

    @Test
    fun `falls back to VP9 when H264 tops out far below`() {
        val streams = listOf(video(360, "avc1.4D401E"), video(1080, "vp9"), video(720, "vp9"), audio(128_000))

        val selection = StreamSelector.select(streams)

        assertEquals(listOf(720, 1080), selection.videos.map { it.height })
    }

    @Test
    fun `picks original audio track over a higher-bitrate dub`() {
        val original = audio(128_000, AudioTrackType.ORIGINAL)
        val streams = listOf(video(720, "avc1"), audio(160_000, AudioTrackType.DUBBED), original)

        assertEquals(original, StreamSelector.select(streams).audio)
    }

    @Test
    fun `uses best muxed stream when there is no separate audio`() {
        val streams = listOf(video(240, "avc1", Kind.MUXED), video(360, "avc1", Kind.MUXED), video(1080, "avc1"))

        val selection = StreamSelector.select(streams)

        assertEquals(listOf(360), selection.videos.map { it.height })
        assertNull("muxed streams already contain audio", selection.audio)
    }

    @Test
    fun `audio only content has no video`() {
        val selection = StreamSelector.select(listOf(audio(64_000), audio(128_000)))

        assertTrue(selection.videos.isEmpty())
        assertEquals(128_000, selection.audio?.bitrate)
    }

    @Test
    fun `ignores HLS streams`() {
        val streams = listOf(video(720, "avc1", delivery = Delivery.HLS), audio(128_000))

        assertTrue(StreamSelector.select(streams).videos.isEmpty())
    }
}
