package uz.donfinity.itubepro.data.mapper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DashManifestMergerTest {

    /** Same structure as YoutubeProgressiveDashManifestCreator output (checked in Phase 4). */
    private fun manifest(itag: Int, height: Int) = """
        <?xml version="1.0" encoding="UTF-8" standalone="no"?>
        <MPD xmlns="urn:mpeg:DASH:schema:MPD:2011" mediaPresentationDuration="PT213S" type="static">
          <Period>
            <AdaptationSet id="0" mimeType="video/mp4" subsegmentAlignment="true">
              <Representation id="$itag" codecs="avc1" bandwidth="${height * 1000}" height="$height" width="${height * 16 / 9}">
                <BaseURL>https://example.com/videoplayback?itag=$itag&amp;x=1</BaseURL>
                <SegmentBase indexRange="700-900"><Initialization range="0-699"/></SegmentBase>
              </Representation>
            </AdaptationSet>
          </Period>
        </MPD>
    """.trimIndent()

    private fun representationIds(xml: String) =
        Regex("<Representation[^>]*\\bid=\"(\\d+)\"").findAll(xml).map { it.groupValues[1] }.toList()

    @Test
    fun `merges all representations into the first adaptation set`() {
        val merged = DashManifestMerger.merge(listOf(manifest(134, 360), manifest(136, 720), manifest(137, 1080)))

        assertEquals(listOf("134", "136", "137"), representationIds(merged))
        assertEquals("still one AdaptationSet", 1, Regex("<AdaptationSet").findAll(merged).count())
        assertEquals("each quality keeps its own file URL", 3, Regex("<BaseURL>").findAll(merged).count())
        assertTrue("escaped & survives serialization", merged.contains("itag=137&amp;x=1"))
    }

    @Test
    fun `skips duplicate itags`() {
        val merged = DashManifestMerger.merge(listOf(manifest(136, 720), manifest(136, 720)))

        assertEquals(listOf("136"), representationIds(merged))
    }

    @Test
    fun `single manifest is returned unchanged`() {
        val only = manifest(136, 720)

        assertSame(only, DashManifestMerger.merge(listOf(only)))
    }
}
