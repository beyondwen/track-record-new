package com.wenhao.record.ui.dashboard

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.wenhao.record.R
import com.wenhao.record.data.tracking.AutoTrackSession
import com.wenhao.record.data.tracking.TrackPathSanitizer
import com.wenhao.record.data.tracking.AutoTrackUiState
import com.baidu.mapapi.map.MapView
import java.util.Locale

class DashboardUiController(
    private val activity: AppCompatActivity
) {
    val mapView: MapView = activity.findViewById(R.id.homeMapView)

    private val headerContainer: View = activity.findViewById(R.id.headerContainer)
    private val dashboardPanel: View = activity.findViewById(R.id.dashboardPanel)
    private val gpsStatusBadge: LinearLayout = activity.findViewById(R.id.gpsStatusBadge)
    private val gpsStatusDot: View = activity.findViewById(R.id.gpsStatusDot)
    private val tvGpsStatus: TextView = activity.findViewById(R.id.tvGpsStatus)
    private val layoutRecordDiagnosticsCompact: View =
        activity.findViewById(R.id.layoutRecordDiagnosticsCompact)
    private val tvRecordDiagnosticsCompactTitle: TextView =
        activity.findViewById(R.id.tvRecordDiagnosticsCompactTitle)
    private val tvRecordDiagnosticsCompactBody: TextView =
        activity.findViewById(R.id.tvRecordDiagnosticsCompactBody)
    private val tvDistanceValue: TextView = activity.findViewById(R.id.tvDistanceValue)
    private val tvTimeValue: TextView = activity.findViewById(R.id.tvTimeValue)
    private val tvSpeedValue: TextView = activity.findViewById(R.id.tvSpeedValue)
    private val tvRecordStatus: TextView = activity.findViewById(R.id.tvRecordStatus)
    private val tvRecordHint: TextView = activity.findViewById(R.id.tvRecordHint)
    private val layoutRecordDiagnostics: View = activity.findViewById(R.id.layoutRecordDiagnostics)
    private val tvRecordDiagnosticsTitle: TextView = activity.findViewById(R.id.tvRecordDiagnosticsTitle)
    private val tvRecordDiagnosticsBody: TextView = activity.findViewById(R.id.tvRecordDiagnosticsBody)
    private val tvAutoTrackTitle: TextView = activity.findViewById(R.id.tvAutoTrackTitle)
    private val tvAutoTrackMeta: TextView = activity.findViewById(R.id.tvAutoTrackMeta)
    private val tvPauseResumeAction: TextView = activity.findViewById(R.id.tvPauseResumeAction)
    private val btnStartRecord: View = activity.findViewById(R.id.btnStartRecord)
    private val recordButtonFace: View = activity.findViewById(R.id.recordButtonFace)
    private val recordButtonHalo: View = activity.findViewById(R.id.recordButtonHalo)
    private val ivRecordAction: ImageView = activity.findViewById(R.id.ivRecordAction)
    private val ivTabRecord: ImageView = activity.findViewById(R.id.ivTabRecord)
    private val ivTabHistory: ImageView = activity.findViewById(R.id.ivTabHistory)
    private val tvTabRecord: TextView = activity.findViewById(R.id.tvTabRecord)
    private val tvTabHistory: TextView = activity.findViewById(R.id.tvTabHistory)
    private val saveFeedbackCard: View = activity.findViewById(R.id.saveFeedbackCard)
    private val tabRecord: View = activity.findViewById(R.id.tabRecord)
    private val tabHistory: View = activity.findViewById(R.id.tabHistory)
    private val btnLocate: ImageView = activity.findViewById(R.id.btnLocate)

    private var recordPulseAnimator: AnimatorSet? = null

    init {
        saveFeedbackCard.visibility = View.GONE
        btnStartRecord.isClickable = false
        btnStartRecord.isFocusable = false
        tvPauseResumeAction.isVisible = false
        tvRecordDiagnosticsTitle.text = activity.getString(R.string.dashboard_diagnostics_title)
        tvRecordDiagnosticsBody.text = activity.getString(R.string.dashboard_diagnostics_loading)
        tvRecordDiagnosticsCompactTitle.text = activity.getString(R.string.dashboard_diagnostics_title)
        tvRecordDiagnosticsCompactBody.text = activity.getString(R.string.dashboard_diagnostics_loading)
    }

    fun bindNavigation(
        onRecordClick: () -> Unit,
        onHistoryClick: () -> Unit,
        onLocateClick: () -> Unit
    ) {
        tabRecord.setOnClickListener { onRecordClick() }
        tabHistory.setOnClickListener { onHistoryClick() }
        btnLocate.setOnClickListener { onLocateClick() }
    }

    fun setRecordContentVisible(isVisible: Boolean) {
        headerContainer.isVisible = isVisible
        dashboardPanel.isVisible = isVisible
    }

    fun setRecordTabSelected(isRecord: Boolean) {
        val selectedColor = ContextCompat.getColor(activity, R.color.md_theme_light_primary)
        val defaultColor = ContextCompat.getColor(activity, R.color.dashboard_nav_inactive)

        ivTabRecord.setColorFilter(if (isRecord) selectedColor else defaultColor)
        tvTabRecord.setTextColor(if (isRecord) selectedColor else defaultColor)
        ivTabHistory.setColorFilter(if (isRecord) defaultColor else selectedColor)
        tvTabHistory.setTextColor(if (isRecord) defaultColor else selectedColor)
    }

    fun render(session: AutoTrackSession?, state: AutoTrackUiState, durationSeconds: Int) {
        val sanitizedTrack = session?.let { activeSession ->
            TrackPathSanitizer.sanitize(activeSession.points, sortByTimestamp = false)
        }
        val displayDistanceKm = when {
            sanitizedTrack == null -> 0.0
            sanitizedTrack.points.size >= 2 -> sanitizedTrack.totalDistanceKm
            else -> session.totalDistanceKm
        }
        updateMetricViews(
            distanceKm = displayDistanceKm,
            durationSeconds = durationSeconds
        )
        renderState(session, state)
    }

    fun updateGpsStatusBadge(label: String, dotColorRes: Int) {
        tvGpsStatus.text = label
        gpsStatusDot.backgroundTintList = ContextCompat.getColorStateList(activity, dotColorRes)
        (gpsStatusBadge.background.mutate() as? GradientDrawable)?.setColor(Color.WHITE)
    }

    fun updateDiagnostics(
        title: String,
        body: String,
        compactBody: String = body,
        isVisible: Boolean = true
    ) {
        layoutRecordDiagnostics.isVisible = isVisible
        layoutRecordDiagnosticsCompact.isVisible = isVisible
        if (!isVisible) return
        tvRecordDiagnosticsTitle.text = title
        tvRecordDiagnosticsBody.text = body
        tvRecordDiagnosticsCompactTitle.text = title
        tvRecordDiagnosticsCompactBody.text = compactBody
    }

    fun onDestroy() {
        recordPulseAnimator?.cancel()
        recordPulseAnimator = null
    }

    private fun renderState(session: AutoTrackSession?, state: AutoTrackUiState) {
        val isTracking = state == AutoTrackUiState.TRACKING
        val isActivePulse = state == AutoTrackUiState.TRACKING || state == AutoTrackUiState.PREPARING

        ivRecordAction.setImageResource(
            if (isTracking) R.drawable.ic_stop_dashboard else R.drawable.ic_play_dashboard
        )
        recordButtonFace.setBackgroundResource(
            if (isActivePulse) R.drawable.dashboard_record_button_active_background
            else R.drawable.dashboard_record_button_background
        )
        recordButtonHalo.alpha = if (isActivePulse) 1f else 0.72f

        tvAutoTrackTitle.text = when (state) {
            AutoTrackUiState.TRACKING -> activity.getString(R.string.dashboard_auto_title_tracking)
            AutoTrackUiState.PREPARING -> activity.getString(R.string.dashboard_auto_title_preparing)
            AutoTrackUiState.PAUSED_STILL -> activity.getString(R.string.dashboard_auto_title_paused)
            AutoTrackUiState.SAVED_RECENTLY -> activity.getString(R.string.dashboard_auto_title_saved)
            AutoTrackUiState.WAITING_PERMISSION -> activity.getString(R.string.dashboard_auto_title_waiting_permission)
            AutoTrackUiState.DISABLED -> activity.getString(R.string.dashboard_auto_title_disabled)
            else -> activity.getString(R.string.dashboard_auto_title_idle)
        }
        tvAutoTrackMeta.text = when (state) {
            AutoTrackUiState.TRACKING -> activity.getString(R.string.dashboard_auto_meta_tracking)
            AutoTrackUiState.PREPARING -> activity.getString(R.string.dashboard_auto_meta_preparing)
            AutoTrackUiState.PAUSED_STILL -> activity.getString(R.string.dashboard_auto_meta_paused)
            AutoTrackUiState.SAVED_RECENTLY -> activity.getString(R.string.dashboard_auto_meta_saved)
            AutoTrackUiState.WAITING_PERMISSION -> activity.getString(R.string.dashboard_auto_meta_waiting_permission)
            AutoTrackUiState.DISABLED -> activity.getString(R.string.dashboard_auto_meta_disabled)
            else -> activity.getString(R.string.dashboard_auto_meta_idle)
        }

        tvRecordStatus.text = when (state) {
            AutoTrackUiState.TRACKING -> activity.getString(R.string.dashboard_status_tracking)
            AutoTrackUiState.PREPARING -> activity.getString(R.string.dashboard_status_preparing)
            AutoTrackUiState.PAUSED_STILL -> activity.getString(R.string.dashboard_status_paused)
            AutoTrackUiState.SAVED_RECENTLY -> activity.getString(R.string.dashboard_status_saved)
            AutoTrackUiState.WAITING_PERMISSION -> activity.getString(R.string.dashboard_status_waiting_permission)
            AutoTrackUiState.DISABLED -> activity.getString(R.string.dashboard_status_disabled)
            else -> activity.getString(R.string.dashboard_status_idle)
        }
        tvRecordStatus.setBackgroundResource(
            when (state) {
                AutoTrackUiState.TRACKING,
                AutoTrackUiState.PREPARING -> R.drawable.dashboard_status_active_background
                AutoTrackUiState.PAUSED_STILL,
                AutoTrackUiState.WAITING_PERMISSION,
                AutoTrackUiState.DISABLED -> R.drawable.dashboard_status_paused_background
                else -> R.drawable.dashboard_status_idle_background
            }
        )

        tvRecordHint.text = when (state) {
            AutoTrackUiState.TRACKING ->
                activity.getString(R.string.dashboard_hint_tracking, session?.points?.size ?: 0)
            AutoTrackUiState.PREPARING ->
                activity.getString(R.string.dashboard_hint_preparing)
            AutoTrackUiState.PAUSED_STILL ->
                activity.getString(R.string.dashboard_hint_paused)
            AutoTrackUiState.SAVED_RECENTLY ->
                activity.getString(R.string.dashboard_hint_saved)
            AutoTrackUiState.WAITING_PERMISSION ->
                activity.getString(R.string.dashboard_hint_waiting_permission)
            AutoTrackUiState.DISABLED ->
                activity.getString(R.string.dashboard_hint_disabled)
            else ->
                activity.getString(R.string.dashboard_hint_idle)
        }

        tvPauseResumeAction.isVisible = false
        btnStartRecord.contentDescription = activity.getString(R.string.dashboard_record_button_description)
        syncRecordButtonAnimation(isActivePulse)
    }

    private fun updateMetricViews(distanceKm: Double, durationSeconds: Int) {
        val averageSpeed = if (durationSeconds > 0) distanceKm / (durationSeconds / 3600.0) else 0.0
        tvDistanceValue.text = String.format(Locale.getDefault(), "%.2f", distanceKm)
        tvTimeValue.text = formatDuration(durationSeconds)
        tvSpeedValue.text = String.format(Locale.getDefault(), "%.1f \u516c\u91cc/\u5c0f\u65f6", averageSpeed)
    }

    private fun formatDuration(durationSeconds: Int): String {
        val hours = durationSeconds / 3600
        val minutes = (durationSeconds % 3600) / 60
        val seconds = durationSeconds % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    private fun syncRecordButtonAnimation(isActivePulse: Boolean) {
        if (isActivePulse) {
            if (recordPulseAnimator?.isRunning == true) return

            val haloScaleX = ObjectAnimator.ofFloat(recordButtonHalo, View.SCALE_X, 1f, 1.12f, 1f).apply {
                duration = 1400L
                repeatCount = ObjectAnimator.INFINITE
            }
            val haloScaleY = ObjectAnimator.ofFloat(recordButtonHalo, View.SCALE_Y, 1f, 1.12f, 1f).apply {
                duration = 1400L
                repeatCount = ObjectAnimator.INFINITE
            }
            val haloAlpha = ObjectAnimator.ofFloat(recordButtonHalo, View.ALPHA, 0.72f, 1f, 0.72f).apply {
                duration = 1400L
                repeatCount = ObjectAnimator.INFINITE
            }
            val faceScaleX = ObjectAnimator.ofFloat(recordButtonFace, View.SCALE_X, 1f, 0.96f, 1f).apply {
                duration = 1400L
                repeatCount = ObjectAnimator.INFINITE
            }
            val faceScaleY = ObjectAnimator.ofFloat(recordButtonFace, View.SCALE_Y, 1f, 0.96f, 1f).apply {
                duration = 1400L
                repeatCount = ObjectAnimator.INFINITE
            }

            recordPulseAnimator = AnimatorSet().apply {
                playTogether(haloScaleX, haloScaleY, haloAlpha, faceScaleX, faceScaleY)
                start()
            }
        } else {
            recordPulseAnimator?.cancel()
            recordPulseAnimator = null
            recordButtonHalo.scaleX = 1f
            recordButtonHalo.scaleY = 1f
            recordButtonHalo.alpha = 0.72f
            recordButtonFace.scaleX = 1f
            recordButtonFace.scaleY = 1f
        }
    }
}
