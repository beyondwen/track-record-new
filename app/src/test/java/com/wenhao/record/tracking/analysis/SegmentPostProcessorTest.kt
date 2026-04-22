package com.wenhao.record.tracking.analysis

import kotlin.test.Test
import kotlin.test.assertEquals

class SegmentPostProcessorTest {

    @Test
    fun `refine recovers short dynamic burst into static`() {
        val processor = SegmentPostProcessor()
        val segments = listOf(
            SegmentCandidate(
                kind = SegmentKind.STATIC,
                startTimestamp = 0L,
                endTimestamp = 300_000L,
                pointCount = 6,
            ),
            SegmentCandidate(
                kind = SegmentKind.DYNAMIC,
                startTimestamp = 301_000L,
                endTimestamp = 340_000L,
                pointCount = 2,
            ),
            SegmentCandidate(
                kind = SegmentKind.STATIC,
                startTimestamp = 341_000L,
                endTimestamp = 780_000L,
                pointCount = 8,
            ),
        )

        val refined = processor.refine(segments)

        assertEquals(1, refined.size)
        assertEquals(SegmentKind.STATIC, refined.single().kind)
        assertEquals(0L, refined.single().startTimestamp)
        assertEquals(780_000L, refined.single().endTimestamp)
    }

    @Test
    fun `refine merges brief static gap inside dynamic movement`() {
        val processor = SegmentPostProcessor()
        val segments = listOf(
            SegmentCandidate(
                kind = SegmentKind.DYNAMIC,
                startTimestamp = 0L,
                endTimestamp = 420_000L,
                pointCount = 7,
            ),
            SegmentCandidate(
                kind = SegmentKind.STATIC,
                startTimestamp = 421_000L,
                endTimestamp = 470_000L,
                pointCount = 2,
            ),
            SegmentCandidate(
                kind = SegmentKind.DYNAMIC,
                startTimestamp = 471_000L,
                endTimestamp = 920_000L,
                pointCount = 7,
            ),
        )

        val refined = processor.refine(segments)

        assertEquals(1, refined.size)
        assertEquals(SegmentKind.DYNAMIC, refined.single().kind)
        assertEquals(0L, refined.single().startTimestamp)
        assertEquals(920_000L, refined.single().endTimestamp)
    }

    @Test
    fun `refine recovers edge uncertain segment into neighboring static stay`() {
        val processor = SegmentPostProcessor()
        val segments = listOf(
            SegmentCandidate(
                kind = SegmentKind.UNCERTAIN,
                startTimestamp = 0L,
                endTimestamp = 50_000L,
                pointCount = 2,
            ),
            SegmentCandidate(
                kind = SegmentKind.STATIC,
                startTimestamp = 51_000L,
                endTimestamp = 420_000L,
                pointCount = 6,
            ),
        )

        val refined = processor.refine(segments)

        assertEquals(1, refined.size)
        assertEquals(SegmentKind.STATIC, refined.single().kind)
        assertEquals(0L, refined.single().startTimestamp)
        assertEquals(420_000L, refined.single().endTimestamp)
    }
}
