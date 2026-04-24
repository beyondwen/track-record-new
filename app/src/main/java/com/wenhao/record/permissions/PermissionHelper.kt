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
import com.wenhao.record.data.tracking.TrackingRuntimeSnapshotStorage
import com.wenhao.record.tracking.BackgroundTrackingService

class PermissionHelper(
    private val activity: AppCompatActivity,
    private val onRefreshGpsStatus: () -> Unit,
    private val onLocateGranted: () -> Unit,
    private val onRefreshDashboard: () -> Unit,
) {
    private enum class PendingPermissionAction {
        LOCATE,
    }

    private enum class PendingAppSettingsAction {
        SMART_TRACKING_ENABLE,
        REPAIR_ONLY,
    }

    private var pendingPermissionAction: PendingPermissionAction? = null
    private var pendingAppSettingsAction: PendingAppSettingsAction? = null
    private var hasShownBatteryOptimizationPrompt = false

    private val locationPermissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        val action = pendingPermissionAction
        pendingPermissionAction = null

        if (!granted) {
            onRefreshGpsStatus()
            onRefreshDashboard()
            Toast.makeText(activity, R.string.location_permission_denied, Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }

        onRefreshGpsStatus()
        onRefreshDashboard()
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
            showBackgroundLocationSettingsPrompt(PendingAppSettingsAction.SMART_TRACKING_ENABLE)
            return@registerForActivityResult
        }

        startBackgroundTrackingWithPrompts(promptBatteryOptimization = true)
    }

    private val activityRecognitionPermissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        onRefreshDashboard()
        if (!granted) {
            Toast.makeText(
                activity,
                R.string.activity_recognition_permission_required,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val notificationPermissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        onRefreshDashboard()
        if (!granted) {
            Toast.makeText(activity, R.string.notification_permission_limited, Toast.LENGTH_LONG).show()
        }
    }

    private val appSettingsLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val action = pendingAppSettingsAction
        pendingAppSettingsAction = null
        if (needsBackgroundLocationPermission()) {
            Toast.makeText(activity, R.string.background_location_permission_required, Toast.LENGTH_LONG).show()
        } else if (action == PendingAppSettingsAction.SMART_TRACKING_ENABLE) {
            startBackgroundTrackingWithPrompts(promptBatteryOptimization = true)
        }
        onRefreshDashboard()
    }

    private val batteryOptimizationLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        onRefreshDashboard()
        if (TrackingPermissionGate.shouldRequestIgnoreBatteryOptimizations(activity)) {
            Toast.makeText(
                activity,
                R.string.battery_optimization_settings_recommended,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun ensureSmartTrackingEnabled() {
        when {
            canRunBackgroundTracking() -> startBackgroundTrackingWithPrompts(promptBatteryOptimization = true)
            !hasSmartTrackingBasePermissions() -> smartTrackingPermissionLauncher.launch(buildSmartTrackingPermissionList())
            needsBackgroundLocationPermission() -> showBackgroundLocationSettingsPrompt(
                PendingAppSettingsAction.SMART_TRACKING_ENABLE
            )
            else -> onRefreshDashboard()
        }
    }

    fun startBackgroundTrackingServiceIfReady() {
        if (canRunBackgroundTracking()) {
            startBackgroundTrackingWithPrompts(promptBatteryOptimization = false)
            return
        }
        if (TrackingRuntimeSnapshotStorage.peek(activity).isEnabled) {
            BackgroundTrackingService.stop(activity)
        }
        onRefreshDashboard()
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

    fun requestLocationPermissionForRepair() {
        if (hasLocationPermission()) {
            onRefreshDashboard()
            return
        }
        pendingPermissionAction = null
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        )
    }

    fun requestActivityRecognitionPermissionForRepair() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || hasActivityRecognitionPermission()) {
            onRefreshDashboard()
            return
        }
        activityRecognitionPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
    }

    fun requestNotificationPermissionForRepair() {
        if (!needsNotificationPermission()) {
            onRefreshDashboard()
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            openAppSettings()
            return
        }
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun openAppSettings() {
        pendingAppSettingsAction = PendingAppSettingsAction.REPAIR_ONLY
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", activity.packageName, null)
        )
        appSettingsLauncher.launch(intent)
    }

    fun openBatteryOptimizationSettings() {
        val intent = TrackingPermissionGate.buildIgnoreBatteryOptimizationsIntent(activity)
        if (intent == null) {
            onRefreshDashboard()
            return
        }
        batteryOptimizationLauncher.launch(intent)
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

    fun canRunBackgroundTracking(): Boolean {
        return TrackingPermissionGate.canRunBackgroundTracking(activity)
    }

    fun shouldRequestIgnoreBatteryOptimizations(): Boolean {
        return TrackingPermissionGate.shouldRequestIgnoreBatteryOptimizations(activity)
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

    private fun showBackgroundLocationSettingsPrompt(action: PendingAppSettingsAction) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.background_location_settings_title)
            .setMessage(R.string.background_location_settings_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_go_to_settings) { _, _ ->
                pendingAppSettingsAction = action
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

    private fun startBackgroundTrackingWithPrompts(promptBatteryOptimization: Boolean) {
        BackgroundTrackingService.start(activity)
        maybeShowNotificationPermissionHint()
        if (promptBatteryOptimization) {
            maybeShowBatteryOptimizationPrompt()
        }
        onRefreshDashboard()
    }

    private fun maybeShowBatteryOptimizationPrompt() {
        if (hasShownBatteryOptimizationPrompt) return
        if (!TrackingPermissionGate.shouldRequestIgnoreBatteryOptimizations(activity)) return
        val intent = TrackingPermissionGate.buildIgnoreBatteryOptimizationsIntent(activity) ?: return
        hasShownBatteryOptimizationPrompt = true
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.battery_optimization_settings_title)
            .setMessage(R.string.battery_optimization_settings_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_go_to_settings) { _, _ ->
                batteryOptimizationLauncher.launch(intent)
            }
            .show()
    }
}
