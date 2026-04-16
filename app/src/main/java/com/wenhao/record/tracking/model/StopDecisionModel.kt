package com.wenhao.record.tracking.model

import com.wenhao.record.tracking.pipeline.FeatureVector

class StopDecisionModel(
    private val config: LinearModelConfig,
) {
    fun score(vector: FeatureVector): Double = LinearModelRunner.score(config, vector.features)
}
