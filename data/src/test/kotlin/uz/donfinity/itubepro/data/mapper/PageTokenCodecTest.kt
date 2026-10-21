package uz.donfinity.itubepro.data.mapper

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.schabi.newpipe.extractor.Page

class PageTokenCodecTest {

    @Test
    fun `round trip keeps url, id and body`() {
        val page = Page("https://www.youtube.com/youtubei/v1/search", "token-123", byteArrayOf(1, 2, 3))

        val decoded = PageTokenCodec.decode(PageTokenCodec.encode(page)!!)

        assertEquals(page.url, decoded.url)
        assertEquals(page.id, decoded.id)
        assertArrayEquals(page.body, decoded.body)
    }

    @Test
    fun `invalid or missing page has no token`() {
        assertNull(PageTokenCodec.encode(null))
        assertNull(PageTokenCodec.encode(Page(null as String?)))
    }
}
