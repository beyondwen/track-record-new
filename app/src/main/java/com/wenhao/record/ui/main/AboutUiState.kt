package com.wenhao.record.ui.main

data class AboutUiState(
    val appVersionLabel: String,
    val isCheckingUpdate: Boolean = false,
    val statusMessage: String? = null,
    val mapboxTokenInput: String = "",
    val hasConfiguredMapboxToken: Boolean = false,
    val workerBaseUrlInput: String = "",
    val uploadTokenInput: String = "",
    val hasConfiguredSampleUpload: Boolean = false,
    val isTestingWorkerConnectivity: Boolean = false,
)
