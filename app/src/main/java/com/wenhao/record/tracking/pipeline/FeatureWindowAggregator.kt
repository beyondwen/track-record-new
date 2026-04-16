package com.wenhao.record.tracking.pipeline

class FeatureWindowAggregator(
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val snapshots = ArrayDeque<SignalSnapshot>()

    fun append(snapshot: SignalSnapshot) {
        snapshots += snapshot
        trimBefore(maxOf(snapshot.timestampMillis, clock()) - WINDOW_180S_MS)
    }

    fun buildVector(): FeatureVector? {
        val latest = snapshots.lastOrNull() ?: return null
        val features = linkedMapOf<String, Double>()
        fillWindow(features, "30s", latest.timestampMillis - WINDOW_30S_MS)
        fillWindow(features, "60s", latest.timestampMillis - WINDOW_60S_MS)
        fillWindow(features, "180s", latest.timestampMillis - WINDOW_180S_MS)
        features["candidate_duration_seconds"] = latest.candidateStateDurationMillis / 1000.0
        features["protection_remaining_seconds"] = latest.protectionRemainingMillis / 1000.0
        features["is_recording"] = latest.isRecording.toBinaryDouble()
        return FeatureVector(
            timestampMillis = latest.timestampMillis,
            features = features,
            isRecording = latest.isRecording,
            phase = latest.phase,
        )
    }

    private fun fillWindow(
        features: MutableMap<String, Double>,
        suffix: String,
        windowStartMillis: Long,
    ) {
        val windowSnapshots = snapshots.filter { it.timestampMillis >= windowStartMillis }
        val sampleCount = windowSnapshots.size.toDouble()
        val accuracyValues = windowSnapshots.mapNotNull { it.accuracyMeters?.toDouble() }
        val speedValues = windowSnapshots.mapNotNull { it.speedMetersPerSecond?.toDouble() }
        val accelerationValues = windowSnapshots.mapNotNull { it.accelerationMagnitude?.toDouble() }

        features["sample_count_$suffix"] = sampleCount
        features["steps_$suffix"] = windowSnapshots.sumOf { it.stepDelta }.toDouble()
        features["accuracy_avg_$suffix"] = accuracyValues.averageOrZero()
        features["speed_avg_$suffix"] = speedValues.averageOrZero()
        features["acceleration_avg_$suffix"] = accelerationValues.averageOrZero()
        features["inside_frequent_place_${suffix}_ratio"] =
            windowSnapshots.count { it.insideFrequentPlace }.toRatio(sampleCount)
        features["wifi_changed_${suffix}_ratio"] =
            windowSnapshots.count { it.wifiChanged }.toRatio(sampleCount)
    }

    private fun trimBefore(cutoffMillis: Long) {
        while (snapshots.isNotEmpty() && snapshots.first().timestampMillis < cutoffMillis) {
            snapshots.removeFirst()
        }
    }

    private fun Iterable<Double>.averageOrZero(): Double {
        val values = toList()
        return if (values.isEmpty()) 0.0 else values.average()
    }

    private fun Int.toRatio(total: Double): Double {
        return if (total <= 0.0) 0.0 else this / total
    }

    private fun Boolean.toBinaryDouble(): Double = if (this) 1.0 else 0.0

    private companion object {
        const val WINDOW_30S_MS = 30_000L
        const val WINDOW_60S_MS = 60_000L
        const val WINDOW_180S_MS = 180_000L
    }
}
