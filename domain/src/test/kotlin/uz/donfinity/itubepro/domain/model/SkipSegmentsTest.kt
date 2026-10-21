package uz.donfinity.itubepro.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import uz.donfinity.itubepro.domain.model.SegmentCategory.INTRO
import uz.donfinity.itubepro.domain.model.SegmentCategory.SELF_PROMO
import uz.donfinity.itubepro.domain.model.SegmentCategory.SPONSOR

class SkipSegmentsTest {

    @Test
    fun `merges overlapping segments and keeps the first category`() {
        val merged = SkipSegments.merge(
            listOf(SkipSegment(10_000, 30_000, SPONSOR), SkipSegment(20_000, 40_000, SELF_PROMO)),
        )

        assertEquals(listOf(SkipSegment(10_000, 40_000, SPONSOR)), merged)
    }

    @Test
    fun `chains segments separated by less than the gap`() {
        val merged = SkipSegments.merge(
            listOf(SkipSegment(0, 5_000, INTRO), SkipSegment(5_500, 9_000, SPONSOR)),
        )

        assertEquals(listOf(SkipSegment(0, 9_000, INTRO)), merged)
    }

    @Test
    fun `keeps distant segments apart and sorts them`() {
        val late = SkipSegment(60_000, 70_000, SPONSOR)
        val early = SkipSegment(0, 5_000, INTRO)

        assertEquals(listOf(early, late), SkipSegments.merge(listOf(late, early)))
    }

    @Test
    fun `drops empty and inverted segments`() {
        val merged = SkipSegments.merge(listOf(SkipSegment(5_000, 5_000, SPONSOR), SkipSegment(9_000, 1_000, SPONSOR)))

        assertEquals(emptyList<SkipSegment>(), merged)
    }

    @Test
    fun `contained segment does not shorten the chain`() {
        val merged = SkipSegments.merge(listOf(SkipSegment(0, 60_000, SPONSOR), SkipSegment(10_000, 20_000, INTRO)))

        assertEquals(listOf(SkipSegment(0, 60_000, SPONSOR)), merged)
    }
}
