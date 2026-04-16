package com.wenhao.record.ui.dashboard

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wenhao.record.R
import com.wenhao.record.data.tracking.AutoTrackSession
import com.wenhao.record.data.tracking.AutoTrackUiState
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

    fun render(session: AutoTrackSession?, state: AutoTrackUiState, durationSeconds: Int) {
        val displayDistanceKm = session?.totalDistanceKm ?: 0.0
        val averageSpeed = if (durationSeconds > 0) {
            displayDistanceKm / (durationSeconds / 3600.0)
        } else {
            0.0
        }

        panelState = panelState.copy(
            distanceText = formatDistance(displayDistanceKm),
            durationText = formatDuration(durationSeconds),
            speedText = context.getString(R.string.compose_dashboard_speed_value, averageSpeed),
            autoTrackTitle = titleForState(state),
            autoTrackMeta = metaForState(state),
            statusLabel = statusForState(state),
            statusTone = toneForState(state),
            recordIconRes = if (state == AutoTrackUiState.TRACKING) {
                R.drawable.ic_stop_dashboard
            } else {
                R.drawable.ic_play_dashboard
            },
            isPulseActive = state == AutoTrackUiState.TRACKING || state == AutoTrackUiState.PREPARING,
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
        AutoTrackUiState.PREPARING -> context.getString(R.string.compose_dashboard_title_preparing)
        AutoTrackUiState.PAUSED_STILL -> context.getString(R.string.compose_dashboard_title_paused)
        AutoTrackUiState.SAVED_RECENTLY -> context.getString(R.string.compose_dashboard_title_saved)
        AutoTrackUiState.WAITING_PERMISSION -> context.getString(R.string.compose_dashboard_title_waiting_permission)
        AutoTrackUiState.DISABLED -> context.getString(R.string.compose_dashboard_title_disabled)
        else -> context.getString(R.string.compose_dashboard_title_idle)
    }

    private fun metaForState(state: AutoTrackUiState): String = when (state) {
        AutoTrackUiState.TRACKING -> context.getString(R.string.compose_dashboard_meta_tracking)
        AutoTrackUiState.PREPARING -> context.getString(R.string.compose_dashboard_meta_preparing)
        AutoTrackUiState.PAUSED_STILL -> context.getString(R.string.compose_dashboard_meta_paused)
        AutoTrackUiState.SAVED_RECENTLY -> context.getString(R.string.compose_dashboard_meta_saved)
        AutoTrackUiState.WAITING_PERMISSION -> context.getString(R.string.compose_dashboard_meta_waiting_permission)
        AutoTrackUiState.DISABLED -> context.getString(R.string.compose_dashboard_meta_disabled)
        else -> context.getString(R.string.compose_dashboard_meta_idle)
    }

    private fun statusForState(state: AutoTrackUiState): String = when (state) {
        AutoTrackUiState.TRACKING -> context.getString(R.string.compose_dashboard_status_tracking)
        AutoTrackUiState.PREPARING -> context.getString(R.string.compose_dashboard_status_preparing)
        AutoTrackUiState.PAUSED_STILL -> context.getString(R.string.compose_dashboard_status_paused)
        AutoTrackUiState.SAVED_RECENTLY -> context.getString(R.string.compose_dashboard_status_saved)
        AutoTrackUiState.WAITING_PERMISSION -> context.getString(R.string.compose_dashboard_status_waiting_permission)
        AutoTrackUiState.DISABLED -> context.getString(R.string.compose_dashboard_status_disabled)
        else -> context.getString(R.string.compose_dashboard_status_idle)
    }

    private fun toneForState(state: AutoTrackUiState): DashboardTone = when (state) {
        AutoTrackUiState.TRACKING,
        AutoTrackUiState.PREPARING -> DashboardTone.ACTIVE
        AutoTrackUiState.PAUSED_STILL,
        AutoTrackUiState.WAITING_PERMISSION,
        AutoTrackUiState.DISABLED -> DashboardTone.WARNING
        AutoTrackUiState.SAVED_RECENTLY -> DashboardTone.SUCCESS
        else -> DashboardTone.MUTED
    }

}
