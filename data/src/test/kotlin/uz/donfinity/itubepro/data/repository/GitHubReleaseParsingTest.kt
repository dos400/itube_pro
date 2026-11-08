package uz.donfinity.itubepro.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uz.donfinity.itubepro.domain.model.AppRelease

class GitHubReleaseParsingTest {

    @Test
    fun `release with apk is parsed`() {
        val body = """
            {
              "tag_name": "v57",
              "name": "itube pro 1.57",
              "draft": false,
              "assets": [
                { "name": "notes.txt", "browser_download_url": "https://x/notes.txt", "size": 10 },
                { "name": "itubepro-57.apk", "browser_download_url": "https://x/itubepro-57.apk", "size": 31000000 }
              ]
            }
        """.trimIndent()
        assertEquals(
            AppRelease(57, "itube pro 1.57", "https://x/itubepro-57.apk", 31_000_000),
            parseLatestRelease(body),
        )
    }

    @Test
    fun `release without apk is ignored`() {
        assertNull(parseLatestRelease("""{ "tag_name": "v3", "assets": [] }"""))
    }

    @Test
    fun `tag must be v plus a number`() {
        assertEquals(12, versionCodeFromTag("v12"))
        assertNull(versionCodeFromTag("v1.2"))
        assertNull(versionCodeFromTag("12"))
        assertNull(versionCodeFromTag("release-12"))
    }

    @Test
    fun `missing release name falls back to the tag`() {
        val body = """{ "tag_name": "v5", "assets": [ { "name": "a.apk", "browser_download_url": "u" } ] }"""
        assertEquals(AppRelease(5, "v5", "u", -1), parseLatestRelease(body))
    }
}
