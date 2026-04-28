package com.wenhao.record.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wenhao.record.R
import com.wenhao.record.data.tracking.AutoTrackUiState
import com.wenhao.record.data.tracking.TrackPoint
import com.wenhao.record.data.tracking.TrackingRuntimeSnapshot
import com.wenhao.record.map.GeoMath
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val _panelState = MutableStateFlow(
        DashboardScreenUiState(
            isRecordTabSelected = true,
            distanceText = "0.00",
            durationText = "00:00",
            speedText = application.getString(R.string.compose_dashboard_speed_value, 0.0),
            autoTrackTitle = application.getString(R.string.compose_dashboard_title_idle),
            autoTrackMeta = application.getString(R.string.compose_dashboard_meta_idle),
            statusLabel = application.getString(R.string.compose_dashboard_status_idle),
            statusTone = DashboardTone.MUTED,
        )
    )
    val panelState: StateFlow<DashboardScreenUiState> = _panelState.asStateFlow()

    private val _overlayState = MutableStateFlow(
        DashboardOverlayUiState(
            gpsLabel = application.getString(R.string.dashboard_gps_searching),
            gpsTone = DashboardTone.WARNING,
            diagnosticsTitle = application.getString(R.string.compose_dashboard_diagnostics_title),
            diagnosticsCompactBody = application.getString(R.string.dashboard_diagnostics_loading),
            locateVisible = true,
        )
    )
    val overlayState: StateFlow<DashboardOverlayUiState> = _overlayState.asStateFlow()

    fun setRecordTabSelected(isRecord: Boolean) {
        val nextState = _panelState.value.copy(isRecordTabSelected = isRecord)
        if (nextState != _panelState.value) {
            _panelState.value = nextState
        }
    }

    fun render(
        runtimeSnapshot: TrackingRuntimeSnapshot?,
        state: AutoTrackUiState,
        todayTrackPoints: List<TrackPoint>,
    ) {
        val displayDistanceKm = todayTrackPoints.zipWithNext { first, second ->
            GeoMath.distanceMeters(first, second).toDouble()
        }.sum() / 1_000.0
        val durationSeconds = if (todayTrackPoints.size >= 2) {
            ((todayTrackPoints.last().timestampMillis - todayTrackPoints.first().timestampMillis)
                .coerceAtLeast(0L) / 1_000L).toInt()
        } else {
            0
        }
        val averageSpeed = if (durationSeconds > 0) {
            displayDistanceKm / (durationSeconds / 3600.0)
        } else {
            0.0
        }

        val nextState = _panelState.value.copy(
            distanceText = formatDistance(displayDistanceKm),
            durationText = formatDuration(durationSeconds),
            speedText = getApplication<Application>().getString(R.string.compose_dashboard_speed_value, averageSpeed),
            autoTrackTitle = titleForState(state),
            autoTrackMeta = metaForState(state),
            statusLabel = statusForState(state),
            statusTone = toneForState(state),
        )
        if (nextState != _panelState.value) {
            _panelState.value = nextState
        }
    }

    fun updateGpsStatusBadge(label: String, dotColorRes: Int) {
        val nextState = _overlayState.value.copy(
            gpsLabel = label,
            gpsTone = when (dotColorRes) {
                R.color.dashboard_badge_green -> DashboardTone.SUCCESS
                R.color.dashboard_badge_yellow -> DashboardTone.WARNING
                R.color.dashboard_badge_red -> DashboardTone.WARNING
                else -> DashboardTone.MUTED
            }
        )
        if (nextState != _overlayState.value) {
            _overlayState.value = nextState
        }
    }

    fun updateDiagnostics(
        title: String,
        body: String,
        compactBody: String = body,
        isVisible: Boolean = true,
    ) {
        val nextState = _overlayState.value.copy(
            diagnosticsTitle = if (isVisible) title else "",
            diagnosticsCompactBody = if (isVisible) compactBody else "",
        )
        if (nextState != _overlayState.value) {
            _overlayState.value = nextState
        }
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
        AutoTrackUiState.TRACKING -> getApplication<Application>().getString(R.string.compose_dashboard_title_tracking)
        AutoTrackUiState.WAITING_PERMISSION -> getApplication<Application>().getString(R.string.compose_dashboard_title_waiting_permission)
        else -> getApplication<Application>().getString(R.string.compose_dashboard_title_idle)
    }

    private fun metaForState(state: AutoTrackUiState): String = when (state) {
        AutoTrackUiState.TRACKING -> getApplication<Application>().getString(R.string.compose_dashboard_meta_tracking)
        AutoTrackUiState.WAITING_PERMISSION -> getApplication<Application>().getString(R.string.compose_dashboard_meta_waiting_permission)
        else -> getApplication<Application>().getString(R.string.compose_dashboard_meta_idle)
    }

    private fun statusForState(state: AutoTrackUiState): String = when (state) {
        AutoTrackUiState.TRACKING -> getApplication<Application>().getString(R.string.compose_dashboard_status_tracking)
        AutoTrackUiState.WAITING_PERMISSION -> getApplication<Application>().getString(R.string.compose_dashboard_status_waiting_permission)
        else -> getApplication<Application>().getString(R.string.compose_dashboard_status_idle)
    }

    private fun toneForState(state: AutoTrackUiState): DashboardTone = when (state) {
        AutoTrackUiState.TRACKING -> DashboardTone.ACTIVE
        AutoTrackUiState.WAITING_PERMISSION -> DashboardTone.WARNING
        else -> DashboardTone.MUTED
    }
}
