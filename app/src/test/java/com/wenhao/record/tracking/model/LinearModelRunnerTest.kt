package com.wenhao.record.tracking.model

import org.junit.Assert.assertTrue
import org.junit.Test

class LinearModelRunnerTest {

    @Test
    fun `score uses ordered features and sigmoid`() {
        val config = LinearModelConfig(
            bias = -1.0,
            featureOrder = listOf("speed_avg_30s", "steps_30s"),
            weights = listOf(0.5, 0.25),
            means = listOf(0.0, 0.0),
            scales = listOf(1.0, 1.0),
        )

        val score = LinearModelRunner.score(
            config = config,
            features = mapOf(
                "speed_avg_30s" to 2.0,
                "steps_30s" to 4.0,
            ),
        )

        assertTrue(score > 0.5)
    }
}
