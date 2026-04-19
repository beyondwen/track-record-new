package com.wenhao.record.tracking.analysis

import kotlin.test.Test
import kotlin.test.assertTrue

class PointSignalClassifierTest {

    @Test
    fun `classifier prefers static for small radius drift`() {
        val analyzedPointClass = Class.forName("com.wenhao.record.tracking.analysis.AnalyzedPoint")
        val classifierClass = Class.forName("com.wenhao.record.tracking.analysis.PointSignalClassifier")
        val scoreClass = Class.forName("com.wenhao.record.tracking.analysis.PointSignalScore")

        val constructor = analyzedPointClass.getConstructor(
            Long::class.javaPrimitiveType,
            Double::class.javaPrimitiveType,
            Double::class.javaPrimitiveType,
            Float::class.javaObjectType,
            Float::class.javaObjectType,
            String::class.java,
            Float::class.javaObjectType,
            String::class.java,
        )
        val points = listOf(
            constructor.newInstance(1_000L, 30.0, 120.0, 45f, 0.2f, "STILL", 0.9f, "wifi-a"),
            constructor.newInstance(2_000L, 30.00002, 120.00002, 50f, 0.1f, "STILL", 0.85f, "wifi-a"),
            constructor.newInstance(3_000L, 30.00001, 120.00001, 42f, 0.0f, "STILL", 0.88f, "wifi-a"),
        )

        val classifier = classifierClass.getConstructor().newInstance()
        val score = classifierClass.getMethod("classify", List::class.java).invoke(classifier, points)
        val staticScore = scoreClass.getMethod("getStaticScore").invoke(score) as Float
        val dynamicScore = scoreClass.getMethod("getDynamicScore").invoke(score) as Float

        assertTrue(staticScore > dynamicScore, "expected staticScore > dynamicScore but got $staticScore <= $dynamicScore")
    }

    @Test
    fun `classifier prefers dynamic for sustained movement`() {
        val analyzedPointClass = Class.forName("com.wenhao.record.tracking.analysis.AnalyzedPoint")
        val classifierClass = Class.forName("com.wenhao.record.tracking.analysis.PointSignalClassifier")
        val scoreClass = Class.forName("com.wenhao.record.tracking.analysis.PointSignalScore")

        val constructor = analyzedPointClass.getConstructor(
            Long::class.javaPrimitiveType,
            Double::class.javaPrimitiveType,
            Double::class.javaPrimitiveType,
            Float::class.javaObjectType,
            Float::class.javaObjectType,
            String::class.java,
            Float::class.javaObjectType,
            String::class.java,
        )
        val points = listOf(
            constructor.newInstance(1_000L, 30.0, 120.0, 8f, 1.8f, "WALKING", 0.92f, "wifi-a"),
            constructor.newInstance(61_000L, 30.0009, 120.0009, 7f, 1.9f, "WALKING", 0.9f, "wifi-b"),
            constructor.newInstance(121_000L, 30.0018, 120.0018, 9f, 2.0f, "WALKING", 0.91f, "wifi-c"),
        )

        val classifier = classifierClass.getConstructor().newInstance()
        val score = classifierClass.getMethod("classify", List::class.java).invoke(classifier, points)
        val staticScore = scoreClass.getMethod("getStaticScore").invoke(score) as Float
        val dynamicScore = scoreClass.getMethod("getDynamicScore").invoke(score) as Float

        assertTrue(dynamicScore > staticScore, "expected dynamicScore > staticScore but got $dynamicScore <= $staticScore")
    }
}
