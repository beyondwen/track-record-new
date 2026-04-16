package com.wenhao.record.tracking.model

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [35])
class DecisionModelRepositoryTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        DecisionModelRepository.clearImportedBundle(context)
    }

    @After
    fun tearDown() {
        DecisionModelRepository.clearImportedBundle(context)
    }

    @Test
    fun `falls back to default bundle when no imported file exists`() {
        val bundle = DecisionModelRepository.loadBundle(context)

        assertEquals(-2.2, bundle.startModel.bias, 0.0001)
        assertEquals(0.8, bundle.thresholdConfig.startThreshold, 0.0001)
        assertEquals(2, bundle.thresholdConfig.startTriggerCount)
    }

    @Test
    fun `imports bundle and loads custom values`() {
        val rawJson = """
            {
              "version": 1,
              "start_model": {
                "bias": -0.5,
                "feature_order": ["steps_30s", "speed_avg_30s"],
                "weights": [0.1, 0.2],
                "means": [1.0, 2.0],
                "scales": [3.0, 4.0],
                "target_field": "start_target",
                "sample_count": 12
              },
              "stop_model": {
                "bias": 0.7,
                "feature_order": ["steps_30s", "speed_avg_30s"],
                "weights": [-0.3, -0.4],
                "means": [5.0, 6.0],
                "scales": [7.0, 8.0],
                "target_field": "stop_target",
                "sample_count": 12
              },
              "feature_config": {
                "feature_order": ["steps_30s", "speed_avg_30s"],
                "missing_value": 0.0,
                "version": 2
              },
              "threshold_config": {
                "start_threshold": 0.66,
                "stop_threshold": 0.91,
                "start_trigger_count": 3,
                "stop_trigger_count": 5,
                "start_protection_millis": 60000,
                "minimum_recording_millis": 90000
              }
            }
        """.trimIndent()

        DecisionModelRepository.importBundle(context, rawJson)
        val bundle = DecisionModelRepository.loadBundle(context)

        assertEquals(-0.5, bundle.startModel.bias, 0.0001)
        assertEquals(0.7, bundle.stopModel.bias, 0.0001)
        assertEquals(0.66, bundle.thresholdConfig.startThreshold, 0.0001)
        assertEquals(3, bundle.thresholdConfig.startTriggerCount)
        assertEquals(2, bundle.featureConfig.version)
        assertTrue(DecisionModelRepository.bundleFile(context).exists())
    }
}
