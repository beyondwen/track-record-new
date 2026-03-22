package com.wenhao.record.permissions

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.wenhao.record.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class PermissionHelper(
    private val activity: AppCompatActivity,
    private val onRefreshGpsStatus: () -> Unit,
    private val onLocateGranted: () -> Unit,
    private val onRefreshDashboard: () -> Unit,
    private val onStartBackgroundTracking: () -> Unit
) {
    private enum class PendingPermissionAction {
        LOCATE
    }

    private var pendingPermissionAction: PendingPermissionAction? = null

    private val locationPermissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        val action = pendingPermissionAction
        pendingPermissionAction = null

        if (!granted) {
            onRefreshGpsStatus()
            Toast.makeText(activity, R.string.location_permission_denied, Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }

        onRefreshGpsStatus()
        when (action) {
            PendingPermissionAction.LOCATE -> onLocateGranted()
            null -> Unit
        }
    }

    private val smartTrackingPermissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            hasLocationPermission()
        val recognitionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            permissions[Manifest.permission.ACTIVITY_RECOGNITION] == true ||
            hasActivityRecognitionPermission()
        if (!locationGranted || !recognitionGranted) {
            Toast.makeText(activity, R.string.smart_tracking_permission_required, Toast.LENGTH_LONG).show()
            onRefreshDashboard()
            return@registerForActivityResult
        }

        if (needsBackgroundLocationPermission()) {
            showBackgroundLocationSettingsPrompt()
            return@registerForActivityResult
        }

        onStartBackgroundTracking()
        maybeShowNotificationPermissionHint()
    }

    private val appSettingsLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (needsBackgroundLocationPermission()) {
            Toast.makeText(activity, R.string.background_location_permission_required, Toast.LENGTH_LONG).show()
        } else {
            onStartBackgroundTracking()
            maybeShowNotificationPermissionHint()
        }
        onRefreshDashboard()
    }

    fun ensureSmartTrackingEnabled() {
        if (!hasSmartTrackingBasePermissions()) {
            smartTrackingPermissionLauncher.launch(buildSmartTrackingPermissionList())
            return
        }

        if (needsBackgroundLocationPermission()) {
            showBackgroundLocationSettingsPrompt()
            return
        }

        onStartBackgroundTracking()
        maybeShowNotificationPermissionHint()
    }

    fun startBackgroundTrackingServiceIfReady() {
        if (!hasSmartTrackingBasePermissions()) return
        if (needsBackgroundLocationPermission()) return
        onStartBackgroundTracking()
        maybeShowNotificationPermissionHint()
    }

    fun requestLocatePermissionOrRun() {
        if (hasLocationPermission()) {
            onLocateGranted()
            return
        }

        pendingPermissionAction = PendingPermissionAction.LOCATE
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    fun hasSmartTrackingBasePermissions(): Boolean {
        return hasLocationPermission() && hasActivityRecognitionPermission()
    }

    fun hasLocationPermission(): Boolean {
        return TrackingPermissionGate.hasLocationPermission(activity)
    }

    fun needsBackgroundLocationPermission(): Boolean {
        return TrackingPermissionGate.needsBackgroundLocationPermission(activity)
    }

    fun hasActivityRecognitionPermission(): Boolean {
        return TrackingPermissionGate.hasActivityRecognitionPermission(activity)
    }

    fun needsNotificationPermission(): Boolean {
        return TrackingPermissionGate.needsNotificationPermission(activity)
    }

    private fun buildSmartTrackingPermissionList(): Array<String> {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions += Manifest.permission.ACTIVITY_RECOGNITION
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        return permissions.toTypedArray()
    }

    private fun showBackgroundLocationSettingsPrompt() {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.background_location_settings_title)
            .setMessage(R.string.background_location_settings_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_go_to_settings) { _, _ ->
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", activity.packageName, null)
                )
                appSettingsLauncher.launch(intent)
            }
            .show()
    }

    private fun maybeShowNotificationPermissionHint() {
        if (!needsNotificationPermission()) return
        Toast.makeText(activity, R.string.notification_permission_limited, Toast.LENGTH_LONG).show()
    }
}
