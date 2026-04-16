package com.wenhao.record.tracking.model

import kotlin.math.exp

object LinearModelRunner {

    fun score(config: LinearModelConfig, features: Map<String, Double>): Double {
        var raw = config.bias
        config.featureOrder.forEachIndexed { index, name ->
            val value = features[name] ?: 0.0
            val scale = config.scales[index]
            val normalized = if (scale == 0.0) 0.0 else (value - config.means[index]) / scale
            raw += normalized * config.weights[index]
        }
        return 1.0 / (1.0 + exp(-raw))
    }
}
