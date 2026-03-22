package com.example.helloworld.permissions

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.helloworld.R
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
            Toast.makeText(activity, "\u5f00\u542f\u65e0\u611f\u8bb0\u5f55\u9700\u8981\u5b9a\u4f4d\u548c\u6d3b\u52a8\u8bc6\u522b\u6743\u9650\u3002", Toast.LENGTH_LONG).show()
            onRefreshDashboard()
            return@registerForActivityResult
        }

        if (needsBackgroundLocationPermission()) {
            showBackgroundLocationSettingsPrompt()
            return@registerForActivityResult
        }

        onStartBackgroundTracking()
    }

    private val appSettingsLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (needsBackgroundLocationPermission()) {
            Toast.makeText(activity, "\u5982\u9700\u540e\u53f0\u81ea\u52a8\u8bb0\u5f55\uff0c\u8bf7\u5728\u7cfb\u7edf\u8bbe\u7f6e\u91cc\u628a\u5b9a\u4f4d\u6743\u9650\u6539\u4e3a\u201c\u59cb\u7ec8\u5141\u8bb8\u201d\u3002", Toast.LENGTH_LONG).show()
        } else {
            onStartBackgroundTracking()
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
    }

    fun startBackgroundTrackingServiceIfReady() {
        if (!hasSmartTrackingBasePermissions()) return
        if (needsBackgroundLocationPermission()) return
        onStartBackgroundTracking()
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
        val fineGranted = ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fineGranted || coarseGranted
    }

    fun needsBackgroundLocationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) != PackageManager.PERMISSION_GRANTED
    }

    private fun hasActivityRecognitionPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
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
            .setTitle("\u540e\u53f0\u8bb0\u5f55\u9700\u8981\u201c\u59cb\u7ec8\u5141\u8bb8\u201d")
            .setMessage("\u4e3a\u4e86\u8ba9\u5e94\u7528\u5728\u540e\u53f0\u65e0\u611f\u5730\u81ea\u52a8\u8bb0\u5f55\u884c\u7a0b\uff0c\u8bf7\u5728\u63a5\u4e0b\u6765\u7684\u7cfb\u7edf\u8bbe\u7f6e\u9875\u91cc\u628a\u5b9a\u4f4d\u6743\u9650\u8bbe\u4e3a\u201c\u59cb\u7ec8\u5141\u8bb8\u201d\u3002")
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton("\u53bb\u8bbe\u7f6e") { _, _ ->
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", activity.packageName, null)
                )
                appSettingsLauncher.launch(intent)
            }
            .show()
    }
}
