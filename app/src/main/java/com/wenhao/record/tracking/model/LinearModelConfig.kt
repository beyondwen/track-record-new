package com.wenhao.record.tracking.model

data class LinearModelConfig(
    val bias: Double,
    val featureOrder: List<String>,
    val weights: List<Double>,
    val means: List<Double>,
    val scales: List<Double>,
) {
    init {
        val size = featureOrder.size
        require(weights.size == size) { "weights size must match featureOrder size" }
        require(means.size == size) { "means size must match featureOrder size" }
        require(scales.size == size) { "scales size must match featureOrder size" }
    }
}
