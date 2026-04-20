package com.wenhao.record.ui.dashboard

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wenhao.record.R
import com.wenhao.record.data.history.HistoryItem
import com.wenhao.record.data.tracking.AutoTrackUiState
import com.wenhao.record.data.tracking.TrackingRuntimeSnapshot
import kotlin.math.roundToInt

data class DashboardOverlayUiState(
    val gpsLabel: String = "",
    val gpsTone: DashboardTone = DashboardTone.MUTED,
    val diagnosticsTitle: String = "",
    val diagnosticsCompactBody: String = "",
    val locateVisible: Boolean = true,
)

class DashboardUiController(
    private val context: Context,
) {
    var panelState by mutableStateOf(
        DashboardScreenUiState(
            isRecordTabSelected = true,
            distanceText = "0.00",
            durationText = "00:00",
            speedText = context.getString(R.string.compose_dashboard_speed_value, 0.0),
            autoTrackTitle = context.getString(R.string.compose_dashboard_title_idle),
            autoTrackMeta = context.getString(R.string.compose_dashboard_meta_idle),
            statusLabel = context.getString(R.string.compose_dashboard_status_idle),
            statusTone = DashboardTone.MUTED,
            recordIconRes = R.drawable.ic_play_dashboard,
            isPulseActive = false,
        )
    )
        private set

    var overlayState by mutableStateOf(
        DashboardOverlayUiState(
            gpsLabel = context.getString(R.string.dashboard_gps_searching),
            gpsTone = DashboardTone.WARNING,
            diagnosticsTitle = context.getString(R.string.compose_dashboard_diagnostics_title),
            diagnosticsCompactBody = context.getString(R.string.dashboard_diagnostics_loading),
            locateVisible = true,
        )
    )
        private set

    fun setRecordTabSelected(isRecord: Boolean) {
        panelState = panelState.copy(isRecordTabSelected = isRecord)
    }

    fun render(
        runtimeSnapshot: TrackingRuntimeSnapshot?,
        state: AutoTrackUiState,
        todayHistoryItems: List<HistoryItem>,
    ) {
        val displayDistanceKm = todayHistoryItems.sumOf { it.distanceKm }
        val durationSeconds = todayHistoryItems.sumOf { it.durationSeconds }
        val averageSpeed = if (durationSeconds > 0) {
            displayDistanceKm / (durationSeconds / 3600.0)
        } else {
            0.0
        }
        val isTracking = runtimeSnapshot?.isEnabled == true

        panelState = panelState.copy(
            distanceText = formatDistance(displayDistanceKm),
            durationText = formatDuration(durationSeconds),
            speedText = context.getString(R.string.compose_dashboard_speed_value, averageSpeed),
            autoTrackTitle = titleForState(state),
            autoTrackMeta = metaForState(state),
            statusLabel = statusForState(state),
            statusTone = toneForState(state),
            recordIconRes = if (isTracking) {
                R.drawable.ic_stop_dashboard
            } else {
                R.drawable.ic_play_dashboard
            },
            isPulseActive = isTracking,
            controlTitle = controlTitleForState(state),
            controlBody = controlBodyForState(state),
        )
    }

    fun updateGpsStatusBadge(label: String, dotColorRes: Int) {
        overlayState = overlayState.copy(
            gpsLabel = label,
            gpsTone = when (dotColorRes) {
                R.color.dashboard_badge_green -> DashboardTone.SUCCESS
                R.color.dashboard_badge_yellow -> DashboardTone.WARNING
                R.color.dashboard_badge_red -> DashboardTone.WARNING
                else -> DashboardTone.MUTED
            }
        )
    }

    fun updateDiagnostics(
        title: String,
        body: String,
        compactBody: String = body,
        isVisible: Boolean = true,
    ) {
        overlayState = overlayState.copy(
            diagnosticsTitle = if (isVisible) title else "",
            diagnosticsCompactBody = if (isVisible) compactBody else "",
        )
        panelState = panelState.copy(
            autoTrackMeta = if (isVisible) panelState.autoTrackMeta else panelState.autoTrackMeta,
        )
    }

    private fun formatDuration(durationSeconds: Int): String {
        val hours = durationSeconds / 3600
        val minutes = (durationSeconds % 3600) / 60
        val seconds = durationSeconds % 60
        return buildString {
            if (hours > 0) {
                appendTwoDigits(hours)
                append(':')
            }
            appendTwoDigits(minutes)
            append(':')
            appendTwoDigits(seconds)
        }
    }

    private fun formatDistance(distanceKm: Double): String {
        val scaled = (distanceKm * 100).roundToInt().coerceAtLeast(0)
        val integerPart = scaled / 100
        val decimalPart = scaled % 100
        return buildString {
            append(integerPart)
            append('.')
            appendTwoDigits(decimalPart)
        }
    }

    private fun StringBuilder.appendTwoDigits(value: Int) {
        if (value < 10) append('0')
        append(value)
    }

    private fun titleForState(state: AutoTrackUiState): String = when (state) {
        AutoTrackUiState.TRACKING -> context.getString(R.string.compose_dashboard_title_tracking)
        AutoTrackUiState.SAVED_RECENTLY -> context.getString(R.string.compose_dashboard_title_saved)
        AutoTrackUiState.WAITING_PERMISSION -> context.getString(R.string.compose_dashboard_title_waiting_permission)
        else -> context.getString(R.string.compose_dashboard_title_idle)
    }

    private fun metaForState(state: AutoTrackUiState): String = when (state) {
        AutoTrackUiState.TRACKING -> context.getString(R.string.compose_dashboard_meta_tracking)
        AutoTrackUiState.SAVED_RECENTLY -> context.getString(R.string.compose_dashboard_meta_saved)
        AutoTrackUiState.WAITING_PERMISSION -> context.getString(R.string.compose_dashboard_meta_waiting_permission)
        else -> context.getString(R.string.compose_dashboard_meta_idle)
    }

    private fun statusForState(state: AutoTrackUiState): String = when (state) {
        AutoTrackUiState.TRACKING -> context.getString(R.string.compose_dashboard_status_tracking)
        AutoTrackUiState.SAVED_RECENTLY -> context.getString(R.string.compose_dashboard_status_saved)
        AutoTrackUiState.WAITING_PERMISSION -> context.getString(R.string.compose_dashboard_status_waiting_permission)
        else -> context.getString(R.string.compose_dashboard_status_idle)
    }

    private fun toneForState(state: AutoTrackUiState): DashboardTone = when (state) {
        AutoTrackUiState.TRACKING -> DashboardTone.ACTIVE
        AutoTrackUiState.WAITING_PERMISSION -> DashboardTone.WARNING
        AutoTrackUiState.SAVED_RECENTLY -> DashboardTone.SUCCESS
        else -> DashboardTone.MUTED
    }

    private fun controlTitleForState(state: AutoTrackUiState): String = when (state) {
        AutoTrackUiState.TRACKING -> "采集中"
        AutoTrackUiState.WAITING_PERMISSION -> "等待权限"
        AutoTrackUiState.SAVED_RECENTLY -> "已写入历史"
        else -> "待命中"
    }

    private fun controlBodyForState(state: AutoTrackUiState): String = when (state) {
        AutoTrackUiState.TRACKING -> "正在高频采点，移动段会在静止后自动分析并写入历史记录。"
        AutoTrackUiState.WAITING_PERMISSION -> "先授予定位权限，后台采点和延迟分析才能正常工作。"
        AutoTrackUiState.SAVED_RECENTLY -> "最近一段轨迹已完成分析并写入历史记录。"
        else -> "当前未检测到有效移动，系统持续低功耗采点并等待状态变化。"
    }

}
