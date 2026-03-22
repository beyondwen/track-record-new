package com.example.helloworld.ui.dashboard

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
import com.example.helloworld.R
import com.example.helloworld.data.tracking.AutoTrackSession
import com.example.helloworld.data.tracking.AutoTrackUiState
import com.amap.api.maps.MapView
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
    private val tvDistanceValue: TextView = activity.findViewById(R.id.tvDistanceValue)
    private val tvTimeValue: TextView = activity.findViewById(R.id.tvTimeValue)
    private val tvSpeedValue: TextView = activity.findViewById(R.id.tvSpeedValue)
    private val tvRecordStatus: TextView = activity.findViewById(R.id.tvRecordStatus)
    private val tvRecordHint: TextView = activity.findViewById(R.id.tvRecordHint)
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
        updateMetricViews(session?.totalDistanceKm ?: 0.0, durationSeconds)
        renderState(session, state)
    }

    fun updateGpsStatusBadge(label: String, dotColorRes: Int) {
        tvGpsStatus.text = label
        gpsStatusDot.backgroundTintList = ContextCompat.getColorStateList(activity, dotColorRes)
        (gpsStatusBadge.background.mutate() as? GradientDrawable)?.setColor(Color.WHITE)
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
            AutoTrackUiState.TRACKING -> "\u81ea\u52a8\u8bb0\u5f55\u4e2d"
            AutoTrackUiState.PREPARING -> "\u68c0\u6d4b\u5230\u79fb\u52a8"
            AutoTrackUiState.PAUSED_STILL -> "\u9759\u6b62\u5f85\u4fdd\u5b58"
            AutoTrackUiState.SAVED_RECENTLY -> "\u5df2\u81ea\u52a8\u4fdd\u5b58"
            AutoTrackUiState.WAITING_PERMISSION -> "\u7b49\u5f85\u6388\u6743"
            AutoTrackUiState.DISABLED -> "\u667a\u80fd\u8bb0\u5f55\u5df2\u5173\u95ed"
            else -> "\u540e\u53f0\u5b88\u5019\u4e2d"
        }
        tvAutoTrackMeta.text = when (state) {
            AutoTrackUiState.TRACKING -> "\u6b63\u5728\u6301\u7eed\u91c7\u96c6\u8fd9\u6bb5\u51fa\u884c\u7684\u8f68\u8ff9"
            AutoTrackUiState.PREPARING -> "\u6b63\u5728\u5524\u8d77\u5b9a\u4f4d\uff0c\u9a6c\u4e0a\u5f00\u59cb\u8bb0\u5f55\u8fd9\u6bb5\u884c\u7a0b"
            AutoTrackUiState.PAUSED_STILL -> "\u68c0\u6d4b\u5230\u4f60\u5df2\u9759\u6b62\uff0c\u5982\u679c 2 \u5206\u949f\u5185\u4ecd\u672a\u79fb\u52a8\u5c31\u4f1a\u81ea\u52a8\u4fdd\u5b58"
            AutoTrackUiState.SAVED_RECENTLY -> "\u6700\u65b0\u4e00\u6bb5\u51fa\u884c\u5df2\u4fdd\u5b58\u5230\u5386\u53f2\uff0c\u5f53\u524d\u7ee7\u7eed\u5904\u4e8e\u5b88\u5019\u72b6\u6001"
            AutoTrackUiState.WAITING_PERMISSION -> "\u5f00\u542f\u6743\u9650\u540e\u5373\u53ef\u5728\u540e\u53f0\u65e0\u611f\u8bb0\u5f55"
            AutoTrackUiState.DISABLED -> "\u9700\u8981\u91cd\u65b0\u542f\u7528\u540e\u53f0\u667a\u80fd\u8bb0\u5f55\u624d\u4f1a\u81ea\u52a8\u5b88\u5019"
            else -> "\u68c0\u6d4b\u5230\u6b65\u884c\u3001\u9a91\u884c\u6216\u9a7e\u8f66\u540e\u4f1a\u81ea\u52a8\u5f00\u59cb"
        }

        tvRecordStatus.text = when (state) {
            AutoTrackUiState.TRACKING -> "\u81ea\u52a8\u8bb0\u5f55\u4e2d"
            AutoTrackUiState.PREPARING -> "\u51c6\u5907\u8bb0\u5f55"
            AutoTrackUiState.PAUSED_STILL -> "\u9759\u6b62\u4e2d"
            AutoTrackUiState.SAVED_RECENTLY -> "\u5df2\u81ea\u52a8\u4fdd\u5b58"
            AutoTrackUiState.WAITING_PERMISSION -> "\u7b49\u5f85\u6388\u6743"
            AutoTrackUiState.DISABLED -> "\u5df2\u5173\u95ed"
            else -> "\u540e\u53f0\u5b88\u5019\u4e2d"
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
                "\u5df2\u81ea\u52a8\u91c7\u96c6 ${session?.points?.size ?: 0} \u4e2a\u5b9a\u4f4d\u70b9\uff0c\u8fde\u7eed\u9759\u6b62\u7ea6 2 \u5206\u949f\u540e\u4f1a\u81ea\u52a8\u4fdd\u5b58\u5230\u5386\u53f2\u3002"
            AutoTrackUiState.PREPARING ->
                "\u5df2\u68c0\u6d4b\u5230\u660e\u663e\u79fb\u52a8\uff0c\u6b63\u5728\u5524\u8d77\u5b9a\u4f4d\u4e0e\u8f68\u8ff9\u8bb0\u5f55\u3002"
            AutoTrackUiState.PAUSED_STILL ->
                "\u5f53\u524d\u8fd9\u6bb5\u884c\u7a0b\u6682\u65f6\u9759\u6b62\uff0c\u5982\u679c\u4f60\u518d\u6b21\u79fb\u52a8\uff0c\u4f1a\u76f4\u63a5\u63a5\u7eed\u8bb0\u5f55\u3002"
            AutoTrackUiState.SAVED_RECENTLY ->
                "\u884c\u7a0b\u5df2\u81ea\u52a8\u4fdd\u5b58\uff0c\u4f60\u53ef\u4ee5\u5230\u5386\u53f2\u9875\u67e5\u770b\u8fd9\u6bb5\u8f68\u8ff9\u3002"
            AutoTrackUiState.WAITING_PERMISSION ->
                "\u5b8c\u6210\u5b9a\u4f4d\u3001\u6d3b\u52a8\u8bc6\u522b\u548c\u540e\u53f0\u5b9a\u4f4d\u6388\u6743\u540e\uff0c\u5e94\u7528\u5c31\u80fd\u5728\u540e\u53f0\u81ea\u52a8\u8bb0\u5f55\u4f60\u7684\u884c\u7a0b\u3002"
            AutoTrackUiState.DISABLED ->
                "\u667a\u80fd\u8bb0\u5f55\u5df2\u505c\u6b62\uff0c\u91cd\u65b0\u6253\u5f00\u5e94\u7528\u5e76\u6388\u6743\u540e\u53ef\u7ee7\u7eed\u5de5\u4f5c\u3002"
            else ->
                "\u5df2\u5f00\u542f\u65e0\u611f\u8bb0\u5f55\uff0c\u68c0\u6d4b\u5230\u6b65\u884c\u3001\u9a91\u884c\u6216\u9a7e\u8f66\u540e\u4f1a\u81ea\u52a8\u5f00\u59cb\uff0c\u65e0\u9700\u624b\u52a8\u64cd\u4f5c\u3002"
        }

        tvPauseResumeAction.isVisible = false
        btnStartRecord.contentDescription = "\u667a\u80fd\u8bb0\u5f55\u72b6\u6001"
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
