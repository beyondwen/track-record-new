package com.wenhao.record.ui.main

enum class AboutSettingsRowId {
    WORKER_CONFIG,
    SYNC_DIAGNOSTICS,
}

data class AboutSettingsRowModel(
    val id: AboutSettingsRowId,
    val title: String,
    val subtitle: String,
    val statusLabel: String,
)

fun buildAboutSettingsRows(state: AboutUiState): List<AboutSettingsRowModel> {
    return listOf(
        AboutSettingsRowModel(
            id = AboutSettingsRowId.WORKER_CONFIG,
            title = "Worker 配置",
            subtitle = "配置 URL 和 Token",
            statusLabel = if (state.hasConfiguredSampleUpload) "已连接" else "未连接",
        ),
        AboutSettingsRowModel(
            id = AboutSettingsRowId.SYNC_DIAGNOSTICS,
            title = "同步诊断",
            subtitle = "查看队列、缓存和上传状态",
            statusLabel = if (state.syncDiagnostics.outboxFailedCount > 0) "有失败" else "可查看",
        ),
    )
}
