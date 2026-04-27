package com.wenhao.record.ui.main

import com.wenhao.record.tracking.TrackingPhase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class RecordingHealthOverallStatus {
    READY,
    DEGRADED,
    BLOCKED,
}

enum class RecordingHealthItemSeverity {
    NORMAL,
    WARNING,
    ERROR,
}

enum class RecordingHealthItemKey {
    LOCATION,
    BACKGROUND_LOCATION,
    NOTIFICATION,
    BATTERY_OPTIMIZATION,
    TRACKING_SERVICE,
}

enum class RecordingHealthAction {
    REQUEST_LOCATION_PERMISSION,
    OPEN_APP_SETTINGS_FOR_BACKGROUND_LOCATION,
    REQUEST_NOTIFICATION_PERMISSION,
    OPEN_BATTERY_OPTIMIZATION_SETTINGS,
    START_BACKGROUND_TRACKING,
    STOP_BACKGROUND_TRACKING,
    SHOW_DIAGNOSTICS,
    NO_OP,
}

data class RecordingHealthItemUiState(
    val key: RecordingHealthItemKey,
    val title: String,
    val statusText: String,
    val riskText: String? = null,
    val severity: RecordingHealthItemSeverity,
    val action: RecordingHealthAction,
)

data class RecordingHealthDiagnosticSummary(
    val recordingStatusText: String,
    val latestPointText: String,
    val latestEventText: String,
    val serviceText: String,
)

data class RecordingHealthUiState(
    val overallStatus: RecordingHealthOverallStatus,
    val title: String,
    val summaryText: String,
    val items: List<RecordingHealthItemUiState>,
    val primaryAction: RecordingHealthAction,
    val primaryActionText: String,
    val diagnosticSummary: RecordingHealthDiagnosticSummary,
) {
    companion object {
        val EMPTY = RecordingHealthUiState(
            overallStatus = RecordingHealthOverallStatus.BLOCKED,
            title = "记录状态",
            summaryText = "请先完成必要授权后再开始记录",
            items = emptyList(),
            primaryAction = RecordingHealthAction.NO_OP,
            primaryActionText = "去修复",
            diagnosticSummary = RecordingHealthDiagnosticSummary(
                recordingStatusText = "待命，未开始记录",
                latestPointText = "暂无定位点",
                latestEventText = "正在整理诊断状态…",
                serviceText = "后台待命中",
            ),
        )
    }
}

data class RecordingHealthInputs(
    val hasLocationPermission: Boolean,
    val hasBackgroundLocationPermission: Boolean,
    val hasNotificationPermission: Boolean,
    val ignoresBatteryOptimizations: Boolean,
    val trackingEnabled: Boolean,
    val trackingActive: Boolean,
    val diagnosticsStatus: String,
    val diagnosticsEvent: String,
    val latestPointTimestampMillis: Long?,
)

fun buildRecordingHealthUiState(inputs: RecordingHealthInputs): RecordingHealthUiState {
    val baseReady = inputs.hasLocationPermission &&
        inputs.hasBackgroundLocationPermission

    val items = buildList {
        add(
            RecordingHealthItemUiState(
                key = RecordingHealthItemKey.LOCATION,
                title = "定位权限",
                statusText = if (inputs.hasLocationPermission) "已授权" else "未授权",
                severity = if (inputs.hasLocationPermission) {
                    RecordingHealthItemSeverity.NORMAL
                } else {
                    RecordingHealthItemSeverity.ERROR
                },
                action = if (inputs.hasLocationPermission) {
                    RecordingHealthAction.NO_OP
                } else {
                    RecordingHealthAction.REQUEST_LOCATION_PERMISSION
                },
            )
        )
        add(
            RecordingHealthItemUiState(
                key = RecordingHealthItemKey.BACKGROUND_LOCATION,
                title = "后台定位",
                statusText = if (inputs.hasBackgroundLocationPermission) "已允许后台" else "需到系统设置开启",
                riskText = if (inputs.hasBackgroundLocationPermission) null else "锁屏后可能断记录",
                severity = if (inputs.hasBackgroundLocationPermission) {
                    RecordingHealthItemSeverity.NORMAL
                } else {
                    RecordingHealthItemSeverity.ERROR
                },
                action = if (inputs.hasBackgroundLocationPermission) {
                    RecordingHealthAction.NO_OP
                } else {
                    RecordingHealthAction.OPEN_APP_SETTINGS_FOR_BACKGROUND_LOCATION
                },
            )
        )
        add(
            RecordingHealthItemUiState(
                key = RecordingHealthItemKey.NOTIFICATION,
                title = "通知权限",
                statusText = if (inputs.hasNotificationPermission) "已允许" else "建议开启",
                riskText = if (inputs.hasNotificationPermission) null else "后台运行提示可能不可见",
                severity = if (inputs.hasNotificationPermission) {
                    RecordingHealthItemSeverity.NORMAL
                } else {
                    RecordingHealthItemSeverity.WARNING
                },
                action = if (inputs.hasNotificationPermission) {
                    RecordingHealthAction.NO_OP
                } else {
                    RecordingHealthAction.REQUEST_NOTIFICATION_PERMISSION
                },
            )
        )
        add(
            RecordingHealthItemUiState(
                key = RecordingHealthItemKey.BATTERY_OPTIMIZATION,
                title = "电池优化",
                statusText = if (inputs.ignoresBatteryOptimizations) "已忽略优化" else "可能被系统限制",
                riskText = if (inputs.ignoresBatteryOptimizations) null else "系统可能杀掉记录服务",
                severity = if (inputs.ignoresBatteryOptimizations) {
                    RecordingHealthItemSeverity.NORMAL
                } else {
                    RecordingHealthItemSeverity.WARNING
                },
                action = if (inputs.ignoresBatteryOptimizations) {
                    RecordingHealthAction.NO_OP
                } else {
                    RecordingHealthAction.OPEN_BATTERY_OPTIMIZATION_SETTINGS
                },
            )
        )
        add(
            RecordingHealthItemUiState(
                key = RecordingHealthItemKey.TRACKING_SERVICE,
                title = "后台记录服务",
                statusText = when {
                    inputs.trackingActive -> "记录中"
                    baseReady && inputs.trackingEnabled -> "记录中"
                    baseReady -> "未启动"
                    else -> "条件不足"
                },
                riskText = when {
                    inputs.trackingActive -> null
                    baseReady && inputs.trackingEnabled -> null
                    baseReady -> "点击开始后才会记录轨迹"
                    else -> null
                },
                severity = when {
                    inputs.trackingActive || inputs.trackingEnabled -> RecordingHealthItemSeverity.NORMAL
                    baseReady -> RecordingHealthItemSeverity.WARNING
                    else -> RecordingHealthItemSeverity.ERROR
                },
                action = when {
                    inputs.trackingActive || inputs.trackingEnabled -> RecordingHealthAction.NO_OP
                    baseReady -> RecordingHealthAction.START_BACKGROUND_TRACKING
                    else -> RecordingHealthAction.NO_OP
                },
            )
        )
    }

    val blocked = items.any { it.severity == RecordingHealthItemSeverity.ERROR }
    val degraded = !blocked && items.any { it.severity == RecordingHealthItemSeverity.WARNING }
    val overallStatus = when {
        blocked -> RecordingHealthOverallStatus.BLOCKED
        degraded -> RecordingHealthOverallStatus.DEGRADED
        else -> RecordingHealthOverallStatus.READY
    }
    val primaryAction = when {
        blocked -> firstRepairAction(items)
        inputs.trackingActive || inputs.trackingEnabled -> RecordingHealthAction.STOP_BACKGROUND_TRACKING
        else -> RecordingHealthAction.START_BACKGROUND_TRACKING
    }

    return RecordingHealthUiState(
        overallStatus = overallStatus,
        title = "记录状态",
        summaryText = when {
            blocked -> "请先完成必要授权后再开始记录"
            inputs.trackingActive || inputs.trackingEnabled -> "正在记录，结束时会整理并写入历史"
            degraded -> "当前可记录，但后台稳定性可能受系统限制"
            else -> "点击开始后才会记录轨迹"
        },
        items = items,
        primaryAction = primaryAction,
        primaryActionText = when (primaryAction) {
            RecordingHealthAction.SHOW_DIAGNOSTICS -> "查看诊断"
            RecordingHealthAction.STOP_BACKGROUND_TRACKING -> "结束记录"
            RecordingHealthAction.START_BACKGROUND_TRACKING -> "开始记录"
            RecordingHealthAction.NO_OP -> "查看诊断"
            else -> "去修复"
        },
        diagnosticSummary = RecordingHealthDiagnosticSummary(
            recordingStatusText = recordingStatusText(inputs),
            latestPointText = formatLatestPointText(inputs.latestPointTimestampMillis),
            latestEventText = inputs.diagnosticsEvent,
            serviceText = inputs.diagnosticsStatus,
        ),
    )
}

fun deriveTrackingActive(
    isEnabled: Boolean,
    phase: TrackingPhase,
): Boolean {
    return isEnabled && phase != TrackingPhase.IDLE
}

fun compactRecordingHealthHighlights(
    state: RecordingHealthUiState,
    maxItems: Int = 2,
): List<RecordingHealthItemUiState> {
    val actionableItems = state.items.filter { it.severity != RecordingHealthItemSeverity.NORMAL }
    val filteredItems = if (
        actionableItems.any { it.key != RecordingHealthItemKey.TRACKING_SERVICE }
    ) {
        actionableItems.filter { it.key != RecordingHealthItemKey.TRACKING_SERVICE }
    } else {
        actionableItems
    }

    return filteredItems
        .asSequence()
        .sortedWith(
            compareBy<RecordingHealthItemUiState>(
                { severityPriority(it.severity) },
                { itemPriority(it.key) },
            )
        )
        .take(maxItems)
        .toList()
}

private fun firstRepairAction(items: List<RecordingHealthItemUiState>): RecordingHealthAction {
    val priority = listOf(
        RecordingHealthItemKey.LOCATION,
        RecordingHealthItemKey.BACKGROUND_LOCATION,
        RecordingHealthItemKey.NOTIFICATION,
        RecordingHealthItemKey.BATTERY_OPTIMIZATION,
        RecordingHealthItemKey.TRACKING_SERVICE,
    )
    return priority.firstNotNullOfOrNull { key ->
        items.firstOrNull { it.key == key && it.action != RecordingHealthAction.NO_OP }?.action
    } ?: RecordingHealthAction.NO_OP
}

private fun recordingStatusText(inputs: RecordingHealthInputs): String {
    return when {
        inputs.trackingActive -> "记录中，可手动结束"
        inputs.trackingEnabled -> "记录恢复中，等待定位服务"
        else -> "待命，未开始记录"
    }
}

private fun formatLatestPointText(timestampMillis: Long?): String {
    if (timestampMillis == null || timestampMillis <= 0L) {
        return "暂无定位点"
    }
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return "${formatter.format(Date(timestampMillis))} 已收到定位点"
}

private fun severityPriority(severity: RecordingHealthItemSeverity): Int {
    return when (severity) {
        RecordingHealthItemSeverity.ERROR -> 0
        RecordingHealthItemSeverity.WARNING -> 1
        RecordingHealthItemSeverity.NORMAL -> 2
    }
}

private fun itemPriority(key: RecordingHealthItemKey): Int {
    return when (key) {
        RecordingHealthItemKey.LOCATION -> 0
        RecordingHealthItemKey.BACKGROUND_LOCATION -> 1
        RecordingHealthItemKey.TRACKING_SERVICE -> 2
        RecordingHealthItemKey.NOTIFICATION -> 3
        RecordingHealthItemKey.BATTERY_OPTIMIZATION -> 4
    }
}
