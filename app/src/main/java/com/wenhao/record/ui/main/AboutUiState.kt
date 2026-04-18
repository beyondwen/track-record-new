package com.wenhao.record.ui.main

data class AboutUiState(
    val appVersionLabel: String,
    val isCheckingUpdate: Boolean = false,
    val statusMessage: String? = null,
    val mapboxTokenInput: String = "",
    val hasConfiguredMapboxToken: Boolean = false,
)
