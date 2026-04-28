package com.wenhao.record.ui.main

data class SyncDiagnosticsUiState(
    val rawPointCount: Int = 0,
    val todayDisplayPointCount: Int = 0,
    val outboxPendingCount: Int = 0,
    val outboxInProgressCount: Int = 0,
    val outboxFailedCount: Int = 0,
    val lastError: String? = null,
    val isRefreshing: Boolean = false,
)

data class SyncDiagnosticsRowModel(
    val title: String,
    val value: String,
    val description: String,
)

fun buildSyncDiagnosticsRows(state: SyncDiagnosticsUiState): List<SyncDiagnosticsRowModel> {
    return listOf(
        SyncDiagnosticsRowModel(
            title = "原始采集队列",
            value = "${state.rawPointCount} 个点",
            description = "等待上传或保留追溯的 raw 点",
        ),
        SyncDiagnosticsRowModel(
            title = "今日展示缓存",
            value = "${state.todayDisplayPointCount} 个点",
            description = "记录页地图当前订阅的 today_display_point",
        ),
        SyncDiagnosticsRowModel(
            title = "待上传",
            value = "${state.outboxPendingCount} 项",
            description = "sync_outbox 中等待处理的任务",
        ),
        SyncDiagnosticsRowModel(
            title = "上传中",
            value = "${state.outboxInProgressCount} 项",
            description = "sync_outbox 中正在处理的任务",
        ),
        SyncDiagnosticsRowModel(
            title = "失败",
            value = state.lastError ?: "${state.outboxFailedCount} 项",
            description = "sync_outbox 最近失败信息",
        ),
    )
}
