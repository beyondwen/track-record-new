package com.wenhao.record.tracking.model

import android.content.Context
import com.wenhao.record.tracking.decision.DecisionSmoother
import com.wenhao.record.tracking.decision.TrackingDecisionEngine
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class DecisionFeatureConfig(
    val featureOrder: List<String>,
    val missingValue: Double,
    val version: Int,
)

data class DecisionThresholdConfig(
    val startThreshold: Double,
    val stopThreshold: Double,
    val startTriggerCount: Int,
    val stopTriggerCount: Int,
    val startProtectionMillis: Long,
    val minimumRecordingMillis: Long,
)

data class DecisionModelBundle(
    val startModel: LinearModelConfig,
    val stopModel: LinearModelConfig,
    val featureConfig: DecisionFeatureConfig,
    val thresholdConfig: DecisionThresholdConfig,
)

object DecisionModelRepository {
    private const val MODEL_DIR = "decision-model"
    private const val BUNDLE_FILE = "decision-model-bundle.json"

    fun bundleFile(context: Context): File {
        return File(context.applicationContext.filesDir, "$MODEL_DIR/$BUNDLE_FILE")
    }

    fun clearImportedBundle(context: Context) {
        bundleFile(context).delete()
    }

    fun importBundle(context: Context, rawJson: String) {
        parseBundle(rawJson)
        val file = bundleFile(context)
        file.parentFile?.mkdirs()
        file.writeText(rawJson.trimIndent().trim() + "\n")
    }

    fun loadBundle(context: Context): DecisionModelBundle {
        val file = bundleFile(context)
        if (!file.exists()) {
            return defaultBundle()
        }
        return runCatching {
            parseBundle(file.readText())
        }.getOrElse {
            defaultBundle()
        }
    }

    fun buildDecisionEngine(context: Context): TrackingDecisionEngine {
        val bundle = loadBundle(context)
        return TrackingDecisionEngine(
            startModel = StartDecisionModel(bundle.startModel),
            stopModel = StopDecisionModel(bundle.stopModel),
            smoother = DecisionSmoother(
                startTriggerCount = bundle.thresholdConfig.startTriggerCount,
                stopTriggerCount = bundle.thresholdConfig.stopTriggerCount,
                startThreshold = bundle.thresholdConfig.startThreshold,
                stopThreshold = bundle.thresholdConfig.stopThreshold,
                startProtectionMillis = bundle.thresholdConfig.startProtectionMillis,
                minimumRecordingMillis = bundle.thresholdConfig.minimumRecordingMillis,
            ),
        )
    }

    internal fun defaultBundle(): DecisionModelBundle {
        val featureOrder = listOf(
            "speed_avg_30s",
            "steps_30s",
            "inside_frequent_place_180s_ratio",
        )
        return DecisionModelBundle(
            startModel = LinearModelConfig(
                bias = -2.2,
                featureOrder = featureOrder,
                weights = listOf(1.1, 0.08, -0.6),
                means = listOf(0.0, 0.0, 0.0),
                scales = listOf(1.0, 1.0, 1.0),
            ),
            stopModel = LinearModelConfig(
                bias = 2.0,
                featureOrder = featureOrder,
                weights = listOf(-1.2, -0.12, 0.6),
                means = listOf(0.0, 0.0, 0.0),
                scales = listOf(1.0, 1.0, 1.0),
            ),
            featureConfig = DecisionFeatureConfig(
                featureOrder = featureOrder,
                missingValue = 0.0,
                version = 1,
            ),
            thresholdConfig = DecisionThresholdConfig(
                startThreshold = 0.8,
                stopThreshold = 0.9,
                startTriggerCount = 2,
                stopTriggerCount = 4,
                startProtectionMillis = 180_000L,
                minimumRecordingMillis = 120_000L,
            ),
        )
    }

    internal fun parseBundle(rawJson: String): DecisionModelBundle {
        val root = JSONObject(rawJson)
        val startModel = parseModel(root.getJSONObject("start_model"))
        val stopModel = parseModel(root.getJSONObject("stop_model"))
        val featureConfig = root.optJSONObject("feature_config")?.let(::parseFeatureConfig)
            ?: DecisionFeatureConfig(
                featureOrder = startModel.featureOrder,
                missingValue = 0.0,
                version = 1,
            )
        val thresholdConfig = parseThresholdConfig(root.getJSONObject("threshold_config"))
        return DecisionModelBundle(
            startModel = startModel,
            stopModel = stopModel,
            featureConfig = featureConfig,
            thresholdConfig = thresholdConfig,
        )
    }

    private fun parseModel(json: JSONObject): LinearModelConfig {
        return LinearModelConfig(
            bias = json.getDouble("bias"),
            featureOrder = json.getJSONArray("feature_order").toStringList(),
            weights = json.getJSONArray("weights").toDoubleList(),
            means = json.getJSONArray("means").toDoubleList(),
            scales = json.getJSONArray("scales").toDoubleList(),
        )
    }

    private fun parseFeatureConfig(json: JSONObject): DecisionFeatureConfig {
        return DecisionFeatureConfig(
            featureOrder = json.getJSONArray("feature_order").toStringList(),
            missingValue = json.optDouble("missing_value", 0.0),
            version = json.optInt("version", 1),
        )
    }

    private fun parseThresholdConfig(json: JSONObject): DecisionThresholdConfig {
        return DecisionThresholdConfig(
            startThreshold = json.getDouble("start_threshold"),
            stopThreshold = json.getDouble("stop_threshold"),
            startTriggerCount = json.getInt("start_trigger_count"),
            stopTriggerCount = json.getInt("stop_trigger_count"),
            startProtectionMillis = json.getLong("start_protection_millis"),
            minimumRecordingMillis = json.getLong("minimum_recording_millis"),
        )
    }

    private fun JSONArray.toStringList(): List<String> {
        return buildList(length()) {
            for (index in 0 until length()) {
                add(getString(index))
            }
        }
    }

    private fun JSONArray.toDoubleList(): List<Double> {
        return buildList(length()) {
            for (index in 0 until length()) {
                add(getDouble(index))
            }
        }
    }
}
