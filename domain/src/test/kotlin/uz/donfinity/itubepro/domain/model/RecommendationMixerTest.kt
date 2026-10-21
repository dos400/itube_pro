package uz.donfinity.itubepro.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationMixerTest {

    private fun v(id: String, channel: String = "ch-$id") =
        VideoSummary("https://y/$id", id, channel, null, null, null, null, isLive = false)

    @Test
    fun `interests take turns near the top`() {
        val related = listOf(listOf(v("a1"), v("a2")), listOf(v("b1"), v("b2")))

        val mixed = RecommendationMixer.mix(related, fresh = emptyList(), exclude = emptySet())

        assertEquals(listOf("a1", "b1", "a2", "b2"), mixed.map { it.title })
    }

    @Test
    fun `watched videos and duplicates are left out`() {
        val related = listOf(listOf(v("a"), v("b")), listOf(v("b"), v("c")))

        val mixed = RecommendationMixer.mix(related, emptyList(), exclude = setOf("https://y/a"))

        assertEquals(listOf("b", "c"), mixed.map { it.title })
    }

    @Test
    fun `one channel cannot fill the list`() {
        val related = listOf((1..6).map { v("x$it", channel = "same") })

        val mixed = RecommendationMixer.mix(related, emptyList(), emptySet(), maxPerChannel = 2)

        assertEquals(2, mixed.size)
    }

    @Test
    fun `every n-th slot is a subscription video`() {
        val related = listOf((1..3).map { v("r$it") })
        val fresh = listOf(v("f1"), v("f2"))

        val mixed = RecommendationMixer.mix(related, fresh, emptySet(), freshEvery = 2)

        assertEquals(listOf("r1", "f1", "r2", "f2", "r3"), mixed.map { it.title })
        assertTrue(mixed.size <= 60)
    }
}
