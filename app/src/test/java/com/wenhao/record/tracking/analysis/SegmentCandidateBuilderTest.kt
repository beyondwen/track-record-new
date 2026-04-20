package com.wenhao.record.tracking.analysis

import kotlin.test.Test
import kotlin.test.assertEquals

class SegmentCandidateBuilderTest {

    @Test
    fun `builder splits static and dynamic runs in order`() {
        val kindClass = Class.forName("com.wenhao.record.tracking.analysis.SegmentKind")
        val scoredPointClass = Class.forName("com.wenhao.record.tracking.analysis.ScoredPoint")
        val builderClass = Class.forName("com.wenhao.record.tracking.analysis.SegmentCandidateBuilder")

        val kindValueOf = kindClass.getMethod("valueOf", String::class.java)
        val staticKind = kindValueOf.invoke(null, "STATIC")
        val dynamicKind = kindValueOf.invoke(null, "DYNAMIC")

        val constructor = scoredPointClass.getConstructor(
            Long::class.javaPrimitiveType,
            Double::class.javaPrimitiveType,
            Double::class.javaPrimitiveType,
            kindClass,
            Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
        )
        val points = listOf(
            constructor.newInstance(1_000L, 30.0, 120.0, staticKind, 0.9f, 0.1f),
            constructor.newInstance(2_000L, 30.0, 120.0, staticKind, 0.85f, 0.15f),
            constructor.newInstance(3_000L, 30.0008, 120.0008, dynamicKind, 0.2f, 0.8f),
            constructor.newInstance(4_000L, 30.0016, 120.0016, dynamicKind, 0.1f, 0.9f),
            constructor.newInstance(5_000L, 30.0016, 120.0016, staticKind, 0.88f, 0.12f),
        )

        val builder = builderClass.getConstructor().newInstance()
        val segments = builderClass.getMethod("build", List::class.java).invoke(builder, points) as List<*>
        val segmentClass = Class.forName("com.wenhao.record.tracking.analysis.SegmentCandidate")
        val getKind = segmentClass.getMethod("getKind")

        assertEquals(
            listOf("STATIC", "DYNAMIC", "STATIC"),
            segments.map { requireNotNull(getKind.invoke(it)).toString() }
        )
    }
}
