package com.wenhao.record.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wenhao.record.BuildConfig
import com.wenhao.record.R
import com.wenhao.record.data.history.HistoryDayItem
import com.wenhao.record.data.history.HistoryStorage
import com.wenhao.record.data.history.HistoryTransferCodec
import com.wenhao.record.data.history.HistoryBatchUploadResult
import com.wenhao.record.data.history.HistoryBatchUploader
import com.wenhao.record.data.history.HistoryUploadRow
import com.wenhao.record.data.history.HistoryUploadService
import com.wenhao.record.data.history.UploadedHistoryStore
import com.wenhao.record.data.tracking.AutoTrackDiagnostics
import com.wenhao.record.data.tracking.AutoTrackDiagnosticsStorage
import com.wenhao.record.data.tracking.AutoTrackSession
import com.wenhao.record.data.tracking.AutoTrackStorage
import com.wenhao.record.data.tracking.AutoTrackUiState
import com.wenhao.record.data.tracking.DecisionFeedbackType
import com.wenhao.record.data.tracking.DecisionEventStorage
import com.wenhao.record.data.tracking.TrackDataChangeNotifier
import com.wenhao.record.data.tracking.TrainingSampleExportCodec
import com.wenhao.record.data.tracking.TrainingSampleBatchUploadResult
import com.wenhao.record.data.tracking.TrainingSampleBatchUploader
import com.wenhao.record.data.tracking.TrainingSampleExporter
import com.wenhao.record.data.tracking.TrainingSampleUploadConfig
import com.wenhao.record.data.tracking.TrainingSampleUploadConfigStorage
import com.wenhao.record.data.tracking.TrainingSampleUploadService
import com.wenhao.record.data.tracking.UploadedTrainingSampleStore
import com.wenhao.record.data.tracking.WorkerConnectivityResult
import com.wenhao.record.data.tracking.WorkerConnectivityService
import com.wenhao.record.map.GeoCoordinate
import com.wenhao.record.permissions.PermissionHelper
import com.wenhao.record.stability.CrashLogStore
import com.wenhao.record.tracking.BackgroundTrackingService
import com.wenhao.record.tracking.LocationSelectionUtils
import com.wenhao.record.tracking.model.DecisionModelRepository
import com.wenhao.record.update.ApkDownloadInstaller
import com.wenhao.record.update.AppUpdateInfo
import com.wenhao.record.update.GithubReleaseUpdateService
import com.wenhao.record.update.UpdateCheckResult
import com.wenhao.record.ui.dashboard.DashboardUiController
import com.wenhao.record.ui.designsystem.TrackRecordTheme
import com.wenhao.record.ui.barometer.BarometerController
import com.wenhao.record.ui.history.HistoryController
import com.wenhao.record.ui.map.MapActivity
import com.wenhao.record.ui.map.MapboxTokenStorage
import com.wenhao.record.ui.map.isMapboxAccessTokenConfigured
import com.wenhao.record.util.AppTaskExecutor
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "TrackRecordBarometer"
        private val PressureSensorKeywords = listOf(
            "pressure",
            "barometer",
            "air pressure",
            "air_pressure",
            "bmp",
            "lps",
            "qmp",
            "hpa",
            "press",
        )
    }

    private val updateService by lazy {
        GithubReleaseUpdateService(
            owner = "beyondwen",
            repo = "track-record-new",
        )
    }
    private val trainingSampleUploadService by lazy { TrainingSampleUploadService() }
    private val historyUploadService by lazy { HistoryUploadService() }
    private val workerConnectivityService by lazy { WorkerConnectivityService() }
    private val apkDownloadInstaller by lazy { ApkDownloadInstaller(this) }
    private lateinit var dashboardUiController: DashboardUiController
    private lateinit var barometerController: BarometerController
    private lateinit var homeMapController: HomeMapPort
    private lateinit var historyController: HistoryController

    private val permissionHelper by lazy {
        PermissionHelper(
            activity = this,
            onRefreshGpsStatus = ::refreshGpsStatus,
            onLocateGranted = ::centerOnCurrentLocation,
            onRefreshDashboard = ::refreshDashboardContent,
            onManualRecordReady = ::startManualRecording,
        )
    }

    private val exportHistoryLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) {
            setHistoryTransferBusy(false)
        } else {
            exportHistoryToUri(uri)
        }
    }

    private val importHistoryLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            setHistoryTransferBusy(false)
        } else {
            importHistoryFromUri(uri)
        }
    }

    private val exportTrainingSamplesLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) {
            setHistoryTransferBusy(false)
        } else {
            exportTrainingSamplesToUri(uri)
        }
    }

    private val importDecisionModelLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            setHistoryTransferBusy(false)
        } else {
            importDecisionModelFromUri(uri)
        }
    }

    private var locationManager: LocationManager? = null
    private var sensorManager: SensorManager? = null
    private var pressureSensor: Sensor? = null
    private var gnssStatusCallback: GnssStatus.Callback? = null
    private var centerOnNextFix = false
    private var lastDashboardSessionStart: Long? = null
    private var previewLocationCache: GeoCoordinate? = null
    private var previewLocationCachedAt = 0L
    private var previewAltitudeMeters: Double? = null
    private var historyTransferBusy = false
    private var currentTab by mutableStateOf(MainTab.RECORD)
    private var mapboxAccessToken by mutableStateOf("")
    private var aboutState by mutableStateOf(
        AboutUiState(appVersionLabel = "")
    )

    private val defaultLatLng = GeoCoordinate(39.9042, 116.4074)
    private val dataChangeListener = object : TrackDataChangeNotifier.Listener {
        override fun onDashboardDataChanged() {
            refreshDashboardContent()
        }

        override fun onHistoryDataChanged() {
            historyController.reload()
            historyController.updateContent()
            homeMapController.shouldRefit = true
            refreshDashboardContent()
        }
    }
    private val freshLocationListener = android.location.LocationListener(::handleLocationUpdate)
    private val pressureSensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) = Unit

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        restoreAboutState()

        dashboardUiController = DashboardUiController(this)
        barometerController = BarometerController()
        homeMapController = HomeMapController()
        historyController = HistoryController(this)
        locationManager = getSystemService(LocationManager::class.java)
        sensorManager = getSystemService(SensorManager::class.java)
        refreshPressureSensorCapability()

        setContent {
            TrackRecordTheme {
                MainComposeScreen(
                    currentTab = currentTab,
                    dashboardState = dashboardUiController.panelState,
                    dashboardOverlayState = dashboardUiController.overlayState,
                    historyState = historyController.uiState,
                    barometerState = barometerController.uiState,
                    aboutState = aboutState,
                    mapboxAccessToken = mapboxAccessToken,
                    dashboardMapState = homeMapController.renderState,
                    onRecordTabClick = { showTab(MainTab.RECORD) },
                    onHistoryTabClick = { showTab(MainTab.HISTORY) },
                    onBarometerTabClick = { showTab(MainTab.BAROMETER) },
                    onAboutTabClick = { showTab(MainTab.ABOUT) },
                    onAboutBackClick = { showTab(MainTab.RECORD) },
                    onCheckUpdateClick = ::checkForAppUpdate,
                    onMapboxTokenChange = ::updateMapboxTokenInput,
                    onMapboxTokenSaveClick = ::saveMapboxToken,
                    onMapboxTokenClearClick = ::clearMapboxToken,
                    onWorkerBaseUrlChange = ::updateWorkerBaseUrlInput,
                    onUploadTokenChange = ::updateUploadTokenInput,
                    onSampleUploadConfigSaveClick = ::saveSampleUploadConfig,
                    onSampleUploadConfigClearClick = ::clearSampleUploadConfig,
                    onWorkerConnectivityTestClick = ::testWorkerConnectivity,
                    onSampleUploadClick = ::uploadPendingTrainingSamples,
                    onHistoryUploadClick = ::uploadPendingHistories,
                    onManualRecordClick = ::handleManualRecordToggle,
                    onLocateClick = ::handleLocateAction,
                    onHistoryOpen = { dayStartMillis ->
                        startActivity(MapActivity.createHistoryIntent(this, dayStartMillis))
                    },
                    onHistoryDelete = ::confirmDeleteHistoryDay,
                    onHistoryExport = ::requestHistoryExport,
                    onHistoryImport = ::requestHistoryImport,
                    onTrainingSampleExport = ::requestTrainingSampleExport,
                    onDecisionModelImport = ::requestDecisionModelImport,
                    onHistoryDecisionFeedback = { eventId ->
                        historyController.setDecisionFeedbackSheet(eventId = eventId, visible = true)
                    },
                    onHistoryFeedbackSubmit = { type ->
                        historyController.submitFeedback(type)
                        Toast.makeText(this, feedbackSavedText(type), Toast.LENGTH_SHORT).show()
                    },
                    onHistoryFeedbackDismiss = {
                        historyController.setDecisionFeedbackSheet(eventId = 0L, visible = false)
                    },
                )
            }
        }

        configureHomeMap()
        initGnssStatusCallback()
        historyController.reload()
        historyController.updateContent()
        refreshDashboardContent()
        showTab(MainTab.RECORD)
        refreshGpsStatus()
    }

    private fun showTab(tab: MainTab) {
        currentTab = tab
        val isRecord = tab == MainTab.RECORD
        dashboardUiController.setRecordTabSelected(isRecord)

        if (tab == MainTab.BAROMETER) {
            refreshPressureSensorCapability()
            barometerController.updateLocationAltitude(previewAltitudeMeters)
            registerPressureSensorIfNeeded()
        } else {
            unregisterPressureSensor()
        }

        if (tab == MainTab.HISTORY) {
            historyController.reload()
            historyController.updateContent()
            return
        }

        if (tab == MainTab.RECORD) {
            syncRecordTabToCurrentLocation()
            refreshDashboardContent()
        }
    }

    private fun buildVersionLabel(): String {
        return "当前版本：${BuildConfig.VERSION_NAME} (${BuildConfig.APP_VERSION_CODE})"
    }

    private fun restoreAboutState() {
        val savedToken = MapboxTokenStorage.load(this)
        val uploadConfig = TrainingSampleUploadConfigStorage.load(this)
        mapboxAccessToken = savedToken
        aboutState = AboutUiState(
            appVersionLabel = buildVersionLabel(),
            mapboxTokenInput = savedToken,
            hasConfiguredMapboxToken = isMapboxAccessTokenConfigured(savedToken),
            workerBaseUrlInput = uploadConfig.workerBaseUrl,
            uploadTokenInput = uploadConfig.uploadToken,
            hasConfiguredSampleUpload = hasConfiguredSampleUpload(uploadConfig),
        )
    }

    private fun updateMapboxTokenInput(value: String) {
        aboutState = aboutState.copy(mapboxTokenInput = value)
    }

    private fun saveMapboxToken() {
        val savedToken = MapboxTokenStorage.save(this, aboutState.mapboxTokenInput)
        mapboxAccessToken = savedToken
        aboutState = aboutState.copy(
            mapboxTokenInput = savedToken,
            hasConfiguredMapboxToken = isMapboxAccessTokenConfigured(savedToken),
        )
        Toast.makeText(this, "Mapbox Token 已保存到当前设备", Toast.LENGTH_SHORT).show()
    }

    private fun clearMapboxToken() {
        MapboxTokenStorage.clear(this)
        mapboxAccessToken = ""
        aboutState = aboutState.copy(
            mapboxTokenInput = "",
            hasConfiguredMapboxToken = false,
        )
        Toast.makeText(this, "已清空当前设备上的 Mapbox Token", Toast.LENGTH_SHORT).show()
    }

    private fun updateWorkerBaseUrlInput(value: String) {
        aboutState = aboutState.copy(workerBaseUrlInput = value)
    }

    private fun updateUploadTokenInput(value: String) {
        aboutState = aboutState.copy(uploadTokenInput = value)
    }

    private fun saveSampleUploadConfig() {
        val newConfig = TrainingSampleUploadConfig(
            workerBaseUrl = aboutState.workerBaseUrlInput,
            uploadToken = aboutState.uploadTokenInput,
        )
        TrainingSampleUploadConfigStorage.save(this, newConfig)
        val savedConfig = TrainingSampleUploadConfigStorage.load(this)
        aboutState = aboutState.copy(
            workerBaseUrlInput = savedConfig.workerBaseUrl,
            uploadTokenInput = savedConfig.uploadToken,
            hasConfiguredSampleUpload = hasConfiguredSampleUpload(savedConfig),
        )
        Toast.makeText(this, "训练样本上传配置已保存", Toast.LENGTH_SHORT).show()
    }

    private fun clearSampleUploadConfig() {
        TrainingSampleUploadConfigStorage.clear(this)
        aboutState = aboutState.copy(
            workerBaseUrlInput = "",
            uploadTokenInput = "",
            hasConfiguredSampleUpload = false,
        )
        Toast.makeText(this, "训练样本上传配置已清空", Toast.LENGTH_SHORT).show()
    }

    private fun uploadPendingTrainingSamples() {
        if (aboutState.isUploadingSamples) return
        if (aboutState.isUploadingHistories) {
            aboutState = aboutState.copy(statusMessage = "历史轨迹上传进行中，请稍后再试")
            return
        }
        if (aboutState.isCheckingUpdate) {
            aboutState = aboutState.copy(statusMessage = "检查更新进行中，请稍后再试")
            return
        }
        if (aboutState.isTestingWorkerConnectivity) {
            aboutState = aboutState.copy(statusMessage = "Worker 连通性测试进行中，请稍后再试")
            return
        }

        val config = TrainingSampleUploadConfigStorage.load(this)
        if (!hasConfiguredSampleUpload(config)) {
            aboutState = aboutState.copy(statusMessage = "未配置上传信息")
            return
        }
        aboutState = aboutState.copy(
            isUploadingSamples = true,
            statusMessage = "准备上传样本…",
        )

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val uploadedEventIds = UploadedTrainingSampleStore.load(this@MainActivity)
                val pendingRows = TrainingSampleExporter.exportRows(this@MainActivity)
                    .filterNot { row -> uploadedEventIds.contains(row.eventId) }
                if (pendingRows.isEmpty()) {
                    launch(Dispatchers.Main) {
                        aboutState = aboutState.copy(statusMessage = "当前没有可上传的新样本")
                    }
                    return@launch
                }

                launch(Dispatchers.Main) {
                    aboutState = aboutState.copy(
                        statusMessage = "正在上传 ${pendingRows.size} 条样本…",
                    )
                }

                Log.i(TAG, "Start uploading ${pendingRows.size} training samples")
                val batchUploader = TrainingSampleBatchUploader { batchRows ->
                    trainingSampleUploadService.upload(
                        config = config,
                        appVersion = BuildConfig.VERSION_NAME,
                        deviceId = uploadDeviceId(),
                        rows = batchRows,
                    )
                }
                val uploadResult = batchUploader.upload(
                    rows = pendingRows,
                    batchSize = TrainingSampleBatchUploader.DEFAULT_BATCH_SIZE,
                    onBatchStart = { progress ->
                        launch(Dispatchers.Main) {
                            aboutState = aboutState.copy(
                                statusMessage = buildString {
                                    append("正在上传第 ")
                                    append(progress.batchIndex + 1)
                                    append("/")
                                    append(progress.totalBatches)
                                    append(" 批（")
                                    append(progress.batchSize)
                                    append(" 条）…")
                                },
                            )
                        }
                        Log.i(
                            TAG,
                            "Uploading training sample batch ${progress.batchIndex + 1}/${progress.totalBatches} size=${progress.batchSize}"
                        )
                    },
                )

                when (uploadResult) {
                    is TrainingSampleBatchUploadResult.Success -> {
                        DecisionEventStorage.deleteUploadedEvents(
                            context = this@MainActivity,
                            eventIds = uploadResult.acceptedEventIds,
                        )
                        UploadedTrainingSampleStore.markUploaded(
                            context = this@MainActivity,
                            eventIds = uploadResult.acceptedEventIds,
                        )
                        launch(Dispatchers.Main) {
                            aboutState = aboutState.copy(
                                statusMessage = "上传成功：${uploadResult.acceptedEventIds.size} 条",
                            )
                        }
                        Log.i(
                            TAG,
                            "Training sample upload succeeded accepted=${uploadResult.acceptedEventIds.size} inserted=${uploadResult.insertedCount} deduped=${uploadResult.dedupedCount}"
                        )
                    }

                    is TrainingSampleBatchUploadResult.Failure -> {
                        DecisionEventStorage.deleteUploadedEvents(
                            context = this@MainActivity,
                            eventIds = uploadResult.acceptedEventIds,
                        )
                        UploadedTrainingSampleStore.markUploaded(
                            context = this@MainActivity,
                            eventIds = uploadResult.acceptedEventIds,
                        )
                        launch(Dispatchers.Main) {
                            aboutState = aboutState.copy(
                                statusMessage = buildString {
                                    append("上传失败：")
                                    append(uploadResult.message)
                                    if (uploadResult.totalBatches > 1) {
                                        append("（第 ")
                                        append(uploadResult.failedBatchIndex + 1)
                                        append("/")
                                        append(uploadResult.totalBatches)
                                        append(" 批）")
                                    }
                                },
                            )
                        }
                        Log.e(
                            TAG,
                            "Training sample upload failed at batch ${uploadResult.failedBatchIndex + 1}/${uploadResult.totalBatches} message=${uploadResult.message} acceptedBeforeFailure=${uploadResult.acceptedEventIds.size}"
                        )
                    }
                }
            } catch (error: Exception) {
                Log.e(TAG, "Training sample upload crashed", error)
                launch(Dispatchers.Main) {
                    val message = error.message?.takeIf { it.isNotBlank() } ?: "上传失败，请稍后重试"
                    aboutState = aboutState.copy(statusMessage = "上传失败：$message")
                }
            } finally {
                launch(Dispatchers.Main) {
                    aboutState = aboutState.copy(isUploadingSamples = false)
                }
            }
        }
    }

    private fun uploadPendingHistories() {
        if (aboutState.isUploadingHistories) return
        if (aboutState.isUploadingSamples) {
            aboutState = aboutState.copy(statusMessage = "样本上传进行中，请稍后再试")
            return
        }
        if (aboutState.isCheckingUpdate) {
            aboutState = aboutState.copy(statusMessage = "检查更新进行中，请稍后再试")
            return
        }
        if (aboutState.isTestingWorkerConnectivity) {
            aboutState = aboutState.copy(statusMessage = "Worker 连通性测试进行中，请稍后再试")
            return
        }

        val config = TrainingSampleUploadConfigStorage.load(this)
        if (!hasConfiguredSampleUpload(config)) {
            aboutState = aboutState.copy(statusMessage = "未配置上传信息")
            return
        }
        aboutState = aboutState.copy(
            isUploadingHistories = true,
            statusMessage = "准备上传历史轨迹…",
        )

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val uploadedHistoryIds = UploadedHistoryStore.load(this@MainActivity)
                val pendingRows = HistoryStorage.load(this@MainActivity)
                    .filterNot { item -> uploadedHistoryIds.contains(item.id) }
                    .map(HistoryUploadRow::from)
                if (pendingRows.isEmpty()) {
                    launch(Dispatchers.Main) {
                        aboutState = aboutState.copy(statusMessage = "当前没有可上传的新历史轨迹")
                    }
                    return@launch
                }

                launch(Dispatchers.Main) {
                    aboutState = aboutState.copy(
                        statusMessage = "正在上传 ${pendingRows.size} 条历史轨迹…",
                    )
                }

                Log.i(TAG, "Start uploading ${pendingRows.size} histories")
                val batchUploader = HistoryBatchUploader { batchRows ->
                    historyUploadService.upload(
                        config = config,
                        appVersion = BuildConfig.VERSION_NAME,
                        deviceId = uploadDeviceId(),
                        rows = batchRows,
                    )
                }
                val uploadResult = batchUploader.upload(
                    rows = pendingRows,
                    batchSize = HistoryBatchUploader.DEFAULT_BATCH_SIZE,
                    onBatchStart = { progress ->
                        launch(Dispatchers.Main) {
                            aboutState = aboutState.copy(
                                statusMessage = buildString {
                                    append("正在上传第 ")
                                    append(progress.batchIndex + 1)
                                    append("/")
                                    append(progress.totalBatches)
                                    append(" 批历史轨迹（")
                                    append(progress.batchSize)
                                    append(" 条）…")
                                },
                            )
                        }
                        Log.i(
                            TAG,
                            "Uploading history batch ${progress.batchIndex + 1}/${progress.totalBatches} size=${progress.batchSize}"
                        )
                    },
                )

                when (uploadResult) {
                    is HistoryBatchUploadResult.Success -> {
                        UploadedHistoryStore.markUploaded(
                            context = this@MainActivity,
                            historyIds = uploadResult.acceptedHistoryIds,
                        )
                        launch(Dispatchers.Main) {
                            aboutState = aboutState.copy(
                                statusMessage = "历史轨迹上传成功：${uploadResult.acceptedHistoryIds.size} 条",
                            )
                        }
                        Log.i(
                            TAG,
                            "History upload succeeded accepted=${uploadResult.acceptedHistoryIds.size} inserted=${uploadResult.insertedCount} deduped=${uploadResult.dedupedCount}"
                        )
                    }

                    is HistoryBatchUploadResult.Failure -> {
                        UploadedHistoryStore.markUploaded(
                            context = this@MainActivity,
                            historyIds = uploadResult.acceptedHistoryIds,
                        )
                        launch(Dispatchers.Main) {
                            aboutState = aboutState.copy(
                                statusMessage = buildString {
                                    append("历史轨迹上传失败：")
                                    append(uploadResult.message)
                                    if (uploadResult.totalBatches > 1) {
                                        append("（第 ")
                                        append(uploadResult.failedBatchIndex + 1)
                                        append("/")
                                        append(uploadResult.totalBatches)
                                        append(" 批）")
                                    }
                                },
                            )
                        }
                        Log.e(
                            TAG,
                            "History upload failed at batch ${uploadResult.failedBatchIndex + 1}/${uploadResult.totalBatches} message=${uploadResult.message} acceptedBeforeFailure=${uploadResult.acceptedHistoryIds.size}"
                        )
                    }
                }
            } catch (error: Exception) {
                Log.e(TAG, "History upload crashed", error)
                launch(Dispatchers.Main) {
                    val message = error.message?.takeIf { it.isNotBlank() } ?: "上传失败，请稍后重试"
                    aboutState = aboutState.copy(statusMessage = "历史轨迹上传失败：$message")
                }
            } finally {
                launch(Dispatchers.Main) {
                    aboutState = aboutState.copy(isUploadingHistories = false)
                }
            }
        }
    }

    private fun hasConfiguredSampleUpload(config: TrainingSampleUploadConfig): Boolean {
        return config.workerBaseUrl.isNotBlank() && config.uploadToken.isNotBlank()
    }

    private fun testWorkerConnectivity() {
        if (aboutState.isTestingWorkerConnectivity) return
        if (aboutState.isUploadingSamples) {
            aboutState = aboutState.copy(statusMessage = "样本上传进行中，请稍后再测试")
            return
        }
        if (aboutState.isUploadingHistories) {
            aboutState = aboutState.copy(statusMessage = "历史轨迹上传进行中，请稍后再测试")
            return
        }
        if (aboutState.isCheckingUpdate) {
            aboutState = aboutState.copy(statusMessage = "检查更新进行中，请稍后再测试")
            return
        }

        val workerBaseUrl = aboutState.workerBaseUrlInput.trim()
        if (workerBaseUrl.isBlank()) {
            aboutState = aboutState.copy(statusMessage = "请先填写 Worker 地址")
            return
        }

        aboutState = aboutState.copy(
            isTestingWorkerConnectivity = true,
            statusMessage = "正在测试 Worker 连通性…",
        )

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.i(TAG, "Testing worker connectivity for $workerBaseUrl")
                val result = workerConnectivityService.check(workerBaseUrl)
                launch(Dispatchers.Main) {
                    aboutState = aboutState.copy(
                        statusMessage = when (result) {
                            is WorkerConnectivityResult.Reachable -> result.message
                            is WorkerConnectivityResult.Unreachable -> result.message
                        }
                    )
                }
                when (result) {
                    is WorkerConnectivityResult.Reachable -> Log.i(TAG, "Worker connectivity check succeeded: ${result.message}")
                    is WorkerConnectivityResult.Unreachable -> Log.e(TAG, "Worker connectivity check failed: ${result.message}")
                }
            } catch (error: Exception) {
                Log.e(TAG, "Worker connectivity check crashed", error)
                launch(Dispatchers.Main) {
                    val message = error.message?.takeIf { it.isNotBlank() } ?: "Worker 连通性测试失败"
                    aboutState = aboutState.copy(statusMessage = message)
                }
            } finally {
                launch(Dispatchers.Main) {
                    aboutState = aboutState.copy(isTestingWorkerConnectivity = false)
                }
            }
        }
    }

    private fun uploadDeviceId(): String {
        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        return if (androidId.isNullOrBlank()) {
            packageName
        } else {
            androidId
        }
    }

    private fun checkForAppUpdate() {
        if (aboutState.isUploadingSamples) {
            aboutState = aboutState.copy(statusMessage = "样本上传进行中，请稍后再检查更新")
            return
        }
        if (aboutState.isUploadingHistories) {
            aboutState = aboutState.copy(statusMessage = "历史轨迹上传进行中，请稍后再检查更新")
            return
        }
        if (aboutState.isTestingWorkerConnectivity) {
            aboutState = aboutState.copy(statusMessage = "Worker 连通性测试进行中，请稍后再检查更新")
            return
        }
        aboutState = aboutState.copy(isCheckingUpdate = true, statusMessage = null)
        lifecycleScope.launch(Dispatchers.IO) {
            val result = updateService.checkForUpdate(currentVersionCode = BuildConfig.APP_VERSION_CODE)
            launch(Dispatchers.Main) {
                aboutState = aboutState.copy(isCheckingUpdate = false)
                when (result) {
                    is UpdateCheckResult.UpToDate -> {
                        aboutState = aboutState.copy(statusMessage = "当前已是最新版本")
                    }
                    is UpdateCheckResult.Failure -> {
                        aboutState = aboutState.copy(statusMessage = result.message)
                    }
                    is UpdateCheckResult.UpdateAvailable -> {
                        aboutState = aboutState.copy(statusMessage = "发现新版本：${result.info.versionName}")
                        showUpdateConfirmDialog(result.info)
                    }
                }
            }
        }
    }

    private fun showUpdateConfirmDialog(info: AppUpdateInfo) {
        MaterialAlertDialogBuilder(this)
            .setTitle("发现新版本")
            .setMessage("检测到新版本 ${info.versionName}，是否下载并安装？")
            .setNegativeButton("取消", null)
            .setPositiveButton("更新") { _, _ ->
                downloadAndInstallUpdate(info)
            }
            .show()
    }

    private fun downloadAndInstallUpdate(info: AppUpdateInfo) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls()
        ) {
            aboutState = aboutState.copy(statusMessage = "请先允许安装未知应用")
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:$packageName")
                )
            )
            return
        }

        aboutState = aboutState.copy(statusMessage = "正在下载更新…")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.i(TAG, "Starting APK download. version=${info.versionName}, url=${info.apkUrl}")
                val apkFile = apkDownloadInstaller.download(info.apkUrl, info.apkName)
                Log.i(
                    TAG,
                    "APK download finished. file=${apkFile.absolutePath}, bytes=${apkFile.length()}"
                )
                launch(Dispatchers.Main) {
                    openDownloadedApk(apkFile)
                }
            } catch (error: Exception) {
                Log.e(TAG, "Failed to download APK from ${info.apkUrl}", error)
                launch(Dispatchers.Main) {
                    aboutState = aboutState.copy(statusMessage = "下载失败")
                }
            }
        }
    }

    private fun openDownloadedApk(apkFile: File) {
        aboutState = aboutState.copy(statusMessage = "下载完成，正在打开安装器")
        val primaryIntent = apkDownloadInstaller.createInstallIntent(apkFile)
        if (startInstallerIntent(primaryIntent, "package-installer")) {
            return
        }

        val fallbackIntent = apkDownloadInstaller.createFallbackInstallIntent(apkFile)
        if (startInstallerIntent(fallbackIntent, "view-fallback")) {
            return
        }

        aboutState = aboutState.copy(statusMessage = "打开安装器失败")
        Toast.makeText(this, "打开安装器失败", Toast.LENGTH_SHORT).show()
    }

    private fun startInstallerIntent(intent: Intent, source: String): Boolean {
        val resolved = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        if (resolved == null) {
            Log.w(TAG, "No installer activity resolved for $source")
            return false
        }

        return runCatching {
            Log.i(
                TAG,
                "Opening installer via $source: ${resolved.activityInfo.packageName}/${resolved.activityInfo.name}"
            )
            startActivity(intent)
        }.onFailure { error ->
            Log.e(TAG, "Failed to open package installer via $source", error)
        }.isSuccess
    }

    private fun syncRecordTabToCurrentLocation() {
        val previewLocation = loadPreviewLocation(forceRefresh = true)
        when {
            previewLocation != null -> {
                homeMapController.updateCurrentLocation(
                    latLng = previewLocation,
                    shouldCenter = true,
                )
            }

            homeMapController.hasActiveTrack() -> {
                homeMapController.focusActiveTrackOnLatestPoint(forceZoom = true)
            }
        }

        if (permissionHelper.hasLocationPermission() && isLocationEnabled()) {
            centerOnNextFix = true
            requestFreshLocationUpdates()
        }
    }

    private fun handleLocateAction() {
        centerOnCurrentLocation()
    }

    private fun handleManualRecordToggle() {
        if (AutoTrackStorage.peekSession(this) != null) {
            BackgroundTrackingService.stop(this)
            Toast.makeText(this, "已结束手动记录", Toast.LENGTH_SHORT).show()
            return
        }
        startManualRecording()
    }

    private fun startManualRecording() {
        if (!permissionHelper.hasLocationPermission()) {
            permissionHelper.requestManualRecordPermissionOrRun()
            return
        }
        if (!isLocationEnabled()) {
            refreshGpsStatus()
            Toast.makeText(this, R.string.location_service_disabled, Toast.LENGTH_SHORT).show()
            return
        }
        BackgroundTrackingService.start(this)
        Toast.makeText(this, "已开始手动记录", Toast.LENGTH_SHORT).show()
    }

    private fun configureHomeMap() {
        homeMapController.configure(
            previewLocation = loadPreviewLocation(forceRefresh = true),
            hasLocationPermission = permissionHelper.hasLocationPermission(),
            defaultCoordinate = defaultLatLng,
        )
    }

    private fun initGnssStatusCallback() {
        gnssStatusCallback = object : GnssStatus.Callback() {
            override fun onStarted() {
                dashboardUiController.updateGpsStatusBadge(
                    label = "正在搜索 GPS",
                    dotColorRes = R.color.dashboard_badge_yellow,
                )
            }

            override fun onStopped() {
                refreshGpsStatus()
            }

            override fun onSatelliteStatusChanged(status: GnssStatus) {
                var usedInFixCount = 0
                var totalCn0DbHz = 0f
                for (index in 0 until status.satelliteCount) {
                    if (status.usedInFix(index)) {
                        usedInFixCount++
                        totalCn0DbHz += status.getCn0DbHz(index)
                    }
                }
                val averageCn0 = if (usedInFixCount > 0) totalCn0DbHz / usedInFixCount else 0f
                when {
                    usedInFixCount == 0 -> dashboardUiController.updateGpsStatusBadge(
                        "正在搜索 GPS",
                        R.color.dashboard_badge_yellow,
                    )

                    averageCn0 >= 20f -> dashboardUiController.updateGpsStatusBadge(
                        "GPS 已就绪",
                        R.color.dashboard_badge_green,
                    )

                    else -> dashboardUiController.updateGpsStatusBadge(
                        "信号较弱",
                        R.color.dashboard_badge_red,
                    )
                }
            }
        }
    }

    private fun refreshDashboardContent() {
        val session = AutoTrackStorage.peekSession(this)
        val previewLocation = loadPreviewLocation()
        val todayHistoryItems = HistoryStorage.peek(this)
        val previousSessionStart = lastDashboardSessionStart
        lastDashboardSessionStart = session?.startTimestamp
        val dashboardState = currentDashboardState(session)
        val durationSeconds = session?.let {
            ((System.currentTimeMillis() - it.startTimestamp) / 1000L).toInt()
        } ?: 0

        dashboardUiController.render(session, dashboardState, durationSeconds)
        homeMapController.render(
            session = session,
            previewLocation = previewLocation,
            todayHistoryItems = todayHistoryItems,
        )
        renderDashboardDiagnosticsRefined()

        if (previousSessionStart != null && session == null) {
            homeMapController.shouldRefit = true
            historyController.reload()
            historyController.updateContent()
        }
    }

    private fun currentDashboardState(session: AutoTrackSession?): AutoTrackUiState {
        if (!permissionHelper.hasLocationPermission()) {
            return AutoTrackUiState.WAITING_PERMISSION
        }

        val savedState = AutoTrackStorage.loadUiState(this)
        return if (session != null) {
            AutoTrackUiState.TRACKING
        } else {
            when (savedState) {
                AutoTrackUiState.TRACKING,
                AutoTrackUiState.PREPARING,
                AutoTrackUiState.PAUSED_STILL,
                AutoTrackUiState.DISABLED,
                -> AutoTrackUiState.IDLE
                else -> savedState
            }
        }
    }

    private fun renderDashboardDiagnosticsRefined() {
        val diagnostics = AutoTrackDiagnosticsStorage.load(this)
        val permissionSummary = buildPermissionSummarySafe()
        val eventSummary = buildTimedSummary(diagnostics.lastEventAt, diagnostics.lastEvent)
        val locationSummary = buildLocationSummary(diagnostics)
        val decisionSummary = buildDecisionSummary(diagnostics)
        val saveSummary = diagnostics.lastSavedSummary?.let { summary ->
            buildTimedSummary(diagnostics.lastSavedAt, summary)
        } ?: "还没有手动结束并保存过有效记录"
        val crashSummary = CrashLogStore.latestSummary(this)

        val body = buildString {
            append("权限：").append(permissionSummary).append('\n')
            append("服务：").append(diagnostics.serviceStatus).append('\n')
            append("最近事件：").append(eventSummary).append('\n')
            append("最近定位：").append(locationSummary).append('\n')
            append("模型决策：").append(decisionSummary).append('\n')
            append("最近保存：").append(saveSummary)
            if (!crashSummary.isNullOrBlank()) {
                append('\n').append("最近异常：").append(crashSummary)
            }
        }
        val compactBody = buildString {
            append(diagnostics.serviceStatus).append('\n')
            append(eventSummary).append('\n')
            append(
                if (diagnostics.lastLocationAt > 0L) {
                    buildTimedSummary(diagnostics.lastLocationAt, diagnostics.lastLocationDecision)
                } else {
                    "等待定位信号"
                }
            )
            if (decisionSummary.isNotBlank()) {
                append('\n').append(decisionSummary)
            }
            if (!crashSummary.isNullOrBlank()) {
                append('\n').append(crashSummary)
            }
        }
        dashboardUiController.updateDiagnostics(
            title = "记录诊断",
            body = body,
            compactBody = compactBody,
        )
    }

    private fun buildPermissionSummarySafe(): String = when {
        !permissionHelper.hasLocationPermission() -> "缺少定位权限"
        permissionHelper.needsNotificationPermission() -> "通知权限未开启"
        else -> "权限完整"
    }

    private fun buildLocationSummary(diagnostics: AutoTrackDiagnostics): String {
        if (diagnostics.lastLocationAt <= 0L) {
            return "暂未收到定位点"
        }

        return buildString {
            append(buildTimedSummary(diagnostics.lastLocationAt, diagnostics.lastLocationDecision))
            append("，已采集 ").append(diagnostics.acceptedPointCount).append(" 个点")
            diagnostics.lastLocationAccuracyMeters?.let { accuracy ->
                append("，精度约 ").append(accuracy.toInt()).append(" 米")
            }
        }
    }

    private fun buildDecisionSummary(diagnostics: AutoTrackDiagnostics): String {
        val decision = diagnostics.lastDecision ?: return "尚未生成模型决策"
        val parts = mutableListOf<String>()
        parts += "最终判定 $decision"
        diagnostics.lastStartScore?.let { score ->
            parts += "开始 ${"%.2f".format(Locale.US, score)}"
        }
        diagnostics.lastStopScore?.let { score ->
            parts += "结束 ${"%.2f".format(Locale.US, score)}"
        }
        return parts.joinToString("，")
    }

    private fun feedbackSavedText(type: DecisionFeedbackType): String {
        return when (type) {
            DecisionFeedbackType.START_TOO_EARLY -> "已标记为开始太早"
            DecisionFeedbackType.START_TOO_LATE -> "已标记为开始太晚"
            DecisionFeedbackType.STOP_TOO_EARLY -> "已标记为结束太早"
            DecisionFeedbackType.STOP_TOO_LATE -> "已标记为结束太晚"
            DecisionFeedbackType.CORRECT -> "已标记为判定正确"
        }
    }

    private fun buildTimedSummary(timestamp: Long, summary: String): String {
        if (timestamp <= 0L) return summary
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        val hour = calendar.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
        val minute = calendar.get(Calendar.MINUTE).toString().padStart(2, '0')
        return "${hour}:${minute} $summary"
    }

    private fun refreshGpsStatus() {
        when {
            !permissionHelper.hasLocationPermission() -> {
                dashboardUiController.updateGpsStatusBadge("未授予定位权限", R.color.dashboard_badge_gray)
            }

            !isLocationEnabled() -> {
                dashboardUiController.updateGpsStatusBadge("定位服务未开启", R.color.dashboard_badge_red)
            }

            loadPreviewLocation() != null -> {
                dashboardUiController.updateGpsStatusBadge("GPS 已就绪", R.color.dashboard_badge_green)
            }

            else -> {
                dashboardUiController.updateGpsStatusBadge("正在搜索 GPS", R.color.dashboard_badge_yellow)
            }
        }
    }

    private fun requestHistoryExport() {
        if (historyTransferBusy) return
        val items = HistoryStorage.peek(this)
        if (items.isEmpty()) {
            Toast.makeText(this, "当前没有可导出的历史记录", Toast.LENGTH_SHORT).show()
            return
        }

        setHistoryTransferBusy(true)
        val filename = "track-record-history-${
            SimpleDateFormat("yyyyMMdd-HHmmss", Locale.CHINA).format(Date())
        }.json"
        exportHistoryLauncher.launch(filename)
    }

    private fun exportHistoryToUri(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            val items = HistoryStorage.load(this@MainActivity)
            if (items.isEmpty()) {
                AppTaskExecutor.runOnMain {
                    with(this@MainActivity) {
                        setHistoryTransferBusy(false)
                    Toast.makeText(this, "当前没有可导出的历史记录", Toast.LENGTH_SHORT).show()
                    }
                }
                return@launch
            }

            val result = runCatching {
                val payload = HistoryTransferCodec.encode(items)
                contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    writer.write(payload)
                } ?: error("Unable to open export stream")
            }
            AppTaskExecutor.runOnMain {
                with(this@MainActivity) {
                    setHistoryTransferBusy(false)
                Toast.makeText(
                    this,
                    if (result.isSuccess) "历史记录已导出" else "导出失败，请重试",
                    Toast.LENGTH_SHORT,
                ).show()
                }
            }
        }
    }

    private fun requestHistoryImport() {
        if (historyTransferBusy) return
        setHistoryTransferBusy(true)
        importHistoryLauncher.launch(arrayOf("application/json", "text/plain", "text/*"))
    }

    private fun requestTrainingSampleExport() {
        if (historyTransferBusy) return
        val rows = TrainingSampleExporter.exportRows(this)
        if (rows.isEmpty()) {
            Toast.makeText(this, "当前没有可导出的训练样本", Toast.LENGTH_SHORT).show()
            return
        }

        setHistoryTransferBusy(true)
        val filename = "track-record-training-samples-${
            SimpleDateFormat("yyyyMMdd-HHmmss", Locale.CHINA).format(Date())
        }.jsonl"
        exportTrainingSamplesLauncher.launch(filename)
    }

    private fun exportTrainingSamplesToUri(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            val rows = TrainingSampleExporter.exportRows(this@MainActivity)
            if (rows.isEmpty()) {
                AppTaskExecutor.runOnMain {
                    with(this@MainActivity) {
                        setHistoryTransferBusy(false)
                        Toast.makeText(this, "当前没有可导出的训练样本", Toast.LENGTH_SHORT).show()
                    }
                }
                return@launch
            }

            val result = runCatching {
                val payload = TrainingSampleExportCodec.encodeJsonLines(rows)
                contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    writer.write(payload)
                } ?: error("Unable to open export stream")
            }
            AppTaskExecutor.runOnMain {
                with(this@MainActivity) {
                    setHistoryTransferBusy(false)
                    Toast.makeText(
                        this,
                        if (result.isSuccess) "训练样本已导出" else "训练样本导出失败，请重试",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    private fun requestDecisionModelImport() {
        if (historyTransferBusy) return
        setHistoryTransferBusy(true)
        importDecisionModelLauncher.launch(arrayOf("application/json", "text/plain", "text/*"))
    }

    private fun importDecisionModelFromUri(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val payload = contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                    reader.readText()
                } ?: error("Unable to read model bundle")
                DecisionModelRepository.importBundle(this@MainActivity, payload)
                if (AutoTrackStorage.peekSession(this@MainActivity) != null) {
                    BackgroundTrackingService.reloadDecisionModel(this@MainActivity)
                }
            }

            AppTaskExecutor.runOnMain {
                with(this@MainActivity) {
                    setHistoryTransferBusy(false)
                    Toast.makeText(
                        this,
                        if (result.isSuccess) "决策模型已导入" else "决策模型导入失败，请确认文件格式",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    private fun importHistoryFromUri(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            val importedItems = runCatching {
                contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                    HistoryTransferCodec.decode(reader.readText())
                } ?: emptyList()
            }.getOrDefault(emptyList())

            if (importedItems.isEmpty()) {
                AppTaskExecutor.runOnMain {
                    with(this@MainActivity) {
                        setHistoryTransferBusy(false)
                    Toast.makeText(this, "导入失败，请确认备份文件格式", Toast.LENGTH_SHORT).show()
                    }
                }
                return@launch
            }

            val mergedItems = HistoryTransferCodec.merge(
                existingItems = HistoryStorage.load(this@MainActivity),
                importedItems = importedItems,
            )
            HistoryStorage.save(this@MainActivity, mergedItems)
            AppTaskExecutor.runOnMain {
                with(this@MainActivity) {
                    setHistoryTransferBusy(false)
                    homeMapController.shouldRefit = true
                    historyController.reload()
                    historyController.updateContent()
                    refreshDashboardContent()
                Toast.makeText(
                    this,
                    "已导入 ${importedItems.size} 条记录，当前共 ${mergedItems.size} 条",
                    Toast.LENGTH_SHORT,
                ).show()
                }
            }
        }
    }

    private fun setHistoryTransferBusy(isBusy: Boolean) {
        historyTransferBusy = isBusy
        historyController.setTransferBusy(isBusy)
    }

    private fun confirmDeleteHistoryDay(dayStartMillis: Long) {
        val item = historyController.uiState.items.firstOrNull { it.dayStartMillis == dayStartMillis } ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.compose_history_delete_title)
            .setMessage(getString(R.string.compose_history_delete_message, item.displayTitle))
            .setNegativeButton(R.string.compose_history_delete_cancel, null)
            .setPositiveButton(R.string.compose_history_delete_confirm) { _, _ ->
                historyController.deleteHistory(item)
                refreshDashboardContent()
                Toast.makeText(this, R.string.compose_history_delete_success, Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun centerOnCurrentLocation() {
        if (!permissionHelper.hasLocationPermission()) {
            permissionHelper.requestLocatePermissionOrRun()
            return
        }

        if (!isLocationEnabled()) {
            refreshGpsStatus()
            Toast.makeText(this, R.string.location_service_disabled, Toast.LENGTH_SHORT).show()
            return
        }

        centerOnNextFix = true
        requestFreshLocationUpdates()

        loadPreviewLocation(forceRefresh = true)?.let {
            homeMapController.centerOnPreviewLocation(it)
        } ?: Toast.makeText(this, R.string.location_unavailable, Toast.LENGTH_SHORT).show()
    }

    private fun isLocationEnabled(): Boolean {
        val manager = locationManager ?: return false
        return manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    @SuppressLint("MissingPermission")
    private fun requestFreshLocationUpdates() {
        val manager = locationManager ?: return
        val providers = collectEnabledProviders(manager)
        if (providers.isEmpty()) {
            refreshGpsStatus()
            Toast.makeText(this, R.string.location_service_disabled, Toast.LENGTH_SHORT).show()
            return
        }

        stopLocationUpdates()
        providers.forEach { provider ->
            manager.requestLocationUpdates(provider, 1000L, 1f, freshLocationListener, Looper.getMainLooper())
        }
    }

    private fun collectEnabledProviders(manager: LocationManager): List<String> {
        val providers = mutableListOf<String>()
        val hasFineLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (hasFineLocation && manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            providers += LocationManager.GPS_PROVIDER
        }
        if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            providers += LocationManager.NETWORK_PROVIDER
        }
        if (providers.isEmpty() && manager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
            providers += LocationManager.PASSIVE_PROVIDER
        }
        return providers
    }

    private fun stopLocationUpdates() {
        locationManager?.removeUpdates(freshLocationListener)
    }

    private fun handleLocationUpdate(location: Location) {
        val previewLocation = toMapCoordinate(location)
        previewLocationCache = previewLocation
        previewLocationCachedAt = System.currentTimeMillis()
        previewAltitudeMeters = location.altitude.takeIf { location.hasAltitude() }
        barometerController.updateLocationAltitude(
            altitudeMeters = previewAltitudeMeters,
            accuracyMeters = location.accuracy,
            timestampMillis = location.time.takeIf { it > 0L } ?: System.currentTimeMillis(),
        )
        homeMapController.updateCurrentLocation(
            latLng = previewLocation,
            shouldCenter = centerOnNextFix,
        )
        centerOnNextFix = false
        stopLocationUpdates()
    }

    @SuppressLint("MissingPermission")
    private fun loadLastKnownLocation(): Location? {
        if (!permissionHelper.hasLocationPermission()) return null
        val manager = locationManager ?: return null
        return LocationSelectionUtils.loadBestLastKnownLocation(
            locationManager = manager,
            hasFineLocation = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED,
            maxAgeMs = 15 * 60 * 1000L,
        )
    }

    private fun loadPreviewLocation(forceRefresh: Boolean = false): GeoCoordinate? {
        val now = System.currentTimeMillis()
        if (!forceRefresh && now - previewLocationCachedAt < 5_000L) {
            return previewLocationCache
        }
        val lastKnownLocation = loadLastKnownLocation()
        previewAltitudeMeters = lastKnownLocation?.altitude?.takeIf { lastKnownLocation.hasAltitude() }
        barometerController.updateLocationAltitude(
            altitudeMeters = previewAltitudeMeters,
            accuracyMeters = lastKnownLocation?.accuracy,
            timestampMillis = lastKnownLocation?.time?.takeIf { it > 0L } ?: now,
        )
        val previewLocation = lastKnownLocation?.let(::toMapCoordinate)
        previewLocationCache = previewLocation
        previewLocationCachedAt = now
        return previewLocation
    }

    private fun toMapCoordinate(location: Location): GeoCoordinate {
        return GeoCoordinate(
            latitude = location.latitude,
            longitude = location.longitude,
        )
    }

    @SuppressLint("MissingPermission")
    private fun registerGnssCallback() {
        if (!permissionHelper.hasLocationPermission()) return
        val manager = locationManager ?: return
        val callback = gnssStatusCallback ?: return
        manager.registerGnssStatusCallback(callback, Handler(Looper.getMainLooper()))
    }

    private fun unregisterGnssCallback() {
        val manager = locationManager ?: return
        val callback = gnssStatusCallback ?: return
        manager.unregisterGnssStatusCallback(callback)
    }

    private fun registerPressureSensorIfNeeded() {
        refreshPressureSensorCapability()
        val manager = sensorManager ?: return
        val sensor = pressureSensor ?: return
        manager.unregisterListener(pressureSensorListener, sensor)
        manager.registerListener(
            pressureSensorListener,
            sensor,
            SensorManager.SENSOR_DELAY_NORMAL,
        )
    }

    private fun unregisterPressureSensor() {
        val manager = sensorManager ?: return
        val sensor = pressureSensor ?: return
        manager.unregisterListener(pressureSensorListener, sensor)
    }

    private fun refreshPressureSensorCapability() {
        val manager = sensorManager
        val resolvedSensor = resolvePressureSensor(manager)
        pressureSensor = resolvedSensor
        val hasFeature = packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_BAROMETER)
        barometerController.setBarometerFeatureAvailable(hasFeature)
        barometerController.setSensorAvailability(resolvedSensor != null)
        barometerController.updateSensorDebugLabel(
            resolvedSensor?.let(::buildPressureSensorLabel).orEmpty(),
        )
        barometerController.updateSensorDiagnostics(
            buildPressureSensorDiagnostics(
                manager = manager,
                resolvedSensor = resolvedSensor,
                hasFeature = hasFeature,
            ),
        )
        barometerController.updateSensorInventory(
            buildPressureSensorInventory(manager),
        )
    }

    private fun resolvePressureSensor(manager: SensorManager?): Sensor? {
        if (manager == null) return null
        val typedPressureSensors = manager.getSensorList(Sensor.TYPE_PRESSURE)
        if (typedPressureSensors.isNotEmpty()) {
            val picked = typedPressureSensors.first()
            Log.d(TAG, "Using typed pressure sensor: ${buildPressureSensorLabel(picked)}")
            return picked
        }

        val defaultPressure = manager.getDefaultSensor(Sensor.TYPE_PRESSURE)
        if (defaultPressure != null) {
            Log.d(TAG, "Using default pressure sensor: ${buildPressureSensorLabel(defaultPressure)}")
            return defaultPressure
        }

        val nonWakePressure = manager.getDefaultSensor(Sensor.TYPE_PRESSURE, false)
        if (nonWakePressure != null) {
            Log.d(TAG, "Using non-wake pressure sensor: ${buildPressureSensorLabel(nonWakePressure)}")
            return nonWakePressure
        }

        val wakePressure = manager.getDefaultSensor(Sensor.TYPE_PRESSURE, true)
        if (wakePressure != null) {
            Log.d(TAG, "Using wake-up pressure sensor: ${buildPressureSensorLabel(wakePressure)}")
            return wakePressure
        }

        val allSensors = manager.getSensorList(Sensor.TYPE_ALL)
        val pressureCandidates = allSensors.filter { sensor ->
            sensor.type == Sensor.TYPE_PRESSURE ||
                matchesPressureSensor(sensor.name) ||
                matchesPressureSensor(sensor.vendor) ||
                matchesPressureSensor(sensor.stringType)
        }
        if (pressureCandidates.isNotEmpty()) {
            val picked = pressureCandidates.first()
            Log.d(
                TAG,
                "Fallback pressure sensor picked: ${buildPressureSensorLabel(picked)} from ${
                    pressureCandidates.joinToString { buildPressureSensorLabel(it) }
                }",
            )
            return picked
        }

        Log.w(
            TAG,
            "No pressure sensor matched. Sensors=${allSensors.joinToString { "${it.name}|${it.vendor}|${it.type}|${it.stringType}" }}",
        )
        return null
    }

    private fun buildPressureSensorLabel(sensor: Sensor): String {
        return "${sensor.name} · ${sensor.vendor}"
    }

    private fun buildPressureSensorDiagnostics(
        manager: SensorManager?,
        resolvedSensor: Sensor?,
        hasFeature: Boolean,
    ): String {
        if (manager == null) return "SensorManager 不可用"
        val typedPressureSensors = manager.getSensorList(Sensor.TYPE_PRESSURE)
        val allSensors = manager.getSensorList(Sensor.TYPE_ALL)
        val pressureLikeSensors = allSensors.filter { sensor ->
            sensor.type == Sensor.TYPE_PRESSURE ||
                matchesPressureSensor(sensor.name) ||
                matchesPressureSensor(sensor.vendor) ||
                matchesPressureSensor(sensor.stringType)
        }
        return buildString {
            append("系统气压特性: ")
            append(if (hasFeature) "有" else "无")
            append(" · TYPE_PRESSURE: ")
            append(typedPressureSensors.size)
            append(" · 压力候选: ")
            append(pressureLikeSensors.size)
            append(" · 结果: ")
            append(resolvedSensor?.let(::buildPressureSensorLabel) ?: "未匹配")
            if (pressureLikeSensors.isNotEmpty()) {
                append(" · 候选列表: ")
                append(
                    pressureLikeSensors.take(4).joinToString(" / ") { sensor ->
                        buildPressureSensorLabel(sensor)
                    },
                )
            }
        }
    }

    private fun buildPressureSensorInventory(manager: SensorManager?): String {
        if (manager == null) return ""
        val allSensors = manager.getSensorList(Sensor.TYPE_ALL)
        if (allSensors.isEmpty()) return ""
        return allSensors
            .take(18)
            .joinToString(separator = "\n") { sensor ->
                val marker = if (
                    sensor.type == Sensor.TYPE_PRESSURE ||
                    matchesPressureSensor(sensor.name) ||
                    matchesPressureSensor(sensor.vendor) ||
                    matchesPressureSensor(sensor.stringType)
                ) {
                    "可能相关"
                } else {
                    "其他"
                }
                "$marker · ${sensor.name} · ${sensor.vendor} · type=${sensor.type} · ${sensor.stringType}"
            }
    }

    private fun matchesPressureSensor(text: String): Boolean {
        return PressureSensorKeywords.any { keyword ->
            text.contains(keyword, ignoreCase = true)
        }
    }

    override fun onResume() {
        super.onResume()
        TrackDataChangeNotifier.addListener(dataChangeListener)
        registerGnssCallback()
        if (currentTab == MainTab.BAROMETER) {
            registerPressureSensorIfNeeded()
        }
        refreshGpsStatus()
        historyController.reload()
        historyController.updateContent()
        homeMapController.shouldRefit = true
        refreshDashboardContent()
    }

    override fun onPause() {
        TrackDataChangeNotifier.removeListener(dataChangeListener)
        unregisterGnssCallback()
        unregisterPressureSensor()
        stopLocationUpdates()
        super.onPause()
    }

    override fun onDestroy() {
        unregisterGnssCallback()
        unregisterPressureSensor()
        stopLocationUpdates()
        TrackDataChangeNotifier.removeListener(dataChangeListener)
        homeMapController.onCleared()
        super.onDestroy()
    }
}
