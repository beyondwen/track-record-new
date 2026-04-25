package com.wenhao.record.ui.main

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.wenhao.record.BuildConfig
import com.wenhao.record.R
import com.wenhao.record.data.diagnostics.DiagnosticLogger
import com.wenhao.record.data.local.TrackDatabase
import com.wenhao.record.data.tracking.SyncDiagnosticsRepository
import com.wenhao.record.data.tracking.SyncDiagnosticsSnapshot
import com.wenhao.record.data.tracking.TrainingSampleUploadConfig
import com.wenhao.record.data.tracking.TrainingSampleUploadConfigStorage
import com.wenhao.record.data.tracking.WorkerAppConfigResult
import com.wenhao.record.data.tracking.WorkerAppConfigService
import com.wenhao.record.data.tracking.WorkerConnectivityResult
import com.wenhao.record.data.tracking.WorkerConnectivityService
import com.wenhao.record.ui.map.MapboxTokenStorage
import com.wenhao.record.ui.map.isMapboxAccessTokenConfigured
import com.wenhao.record.update.AppUpdateInfo
import com.wenhao.record.update.GithubReleaseUpdateService
import com.wenhao.record.update.UpdateCheckResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val KEY_CURRENT_TAB = "current_tab"
private const val TAG = "TrackRecordMain"

class MainViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

    private val updateService by lazy {
        GithubReleaseUpdateService(
            owner = "beyondwen",
            repo = "track-record-new",
        )
    }
    private val workerConnectivityService by lazy { WorkerConnectivityService() }
    private val workerAppConfigService by lazy { WorkerAppConfigService() }
    private val syncDiagnosticsRepository by lazy {
        SyncDiagnosticsRepository(TrackDatabase.getInstance(getApplication()))
    }

    private val _currentTab = MutableStateFlow(
        savedStateHandle.get<String>(KEY_CURRENT_TAB)?.let {
            try {
                MainTab.valueOf(it)
            } catch (_: IllegalArgumentException) {
                MainTab.RECORD
            }
        } ?: MainTab.RECORD
    )
    val currentTab: StateFlow<MainTab> = _currentTab.asStateFlow()

    private val _mapboxAccessToken = MutableStateFlow("")
    val mapboxAccessToken: StateFlow<String> = _mapboxAccessToken.asStateFlow()

    private val _aboutState = MutableStateFlow(
        AboutUiState(appVersionLabel = buildVersionLabel())
    )
    val aboutState: StateFlow<AboutUiState> = _aboutState.asStateFlow()

    fun setAboutStatusMessage(message: String?) {
        _aboutState.value = _aboutState.value.copy(statusMessage = message)
    }

    fun setAboutCheckingUpdate(isChecking: Boolean) {
        _aboutState.value = _aboutState.value.copy(isCheckingUpdate = isChecking)
    }

    init {
        restoreAboutState()
        refreshSyncDiagnostics(showStatusMessage = false)
        syncMapboxTokenFromWorkerIfNeeded()
    }

    fun setCurrentTab(tab: MainTab) {
        _currentTab.value = tab
        savedStateHandle[KEY_CURRENT_TAB] = tab.name
    }

    private fun buildVersionLabel(): String {
        return "当前版本：${BuildConfig.VERSION_NAME} (${BuildConfig.APP_VERSION_CODE})"
    }

    private fun restoreAboutState() {
        val savedToken = MapboxTokenStorage.load(getApplication())
        val uploadConfig = TrainingSampleUploadConfigStorage.load(getApplication())
        _mapboxAccessToken.value = savedToken
        _aboutState.value = AboutUiState(
            appVersionLabel = buildVersionLabel(),
            mapboxTokenInput = savedToken,
            hasConfiguredMapboxToken = isMapboxAccessTokenConfigured(savedToken),
            workerBaseUrlInput = uploadConfig.workerBaseUrl,
            uploadTokenInput = uploadConfig.uploadToken,
            hasConfiguredSampleUpload = hasConfiguredSampleUpload(uploadConfig),
        )
    }

    fun updateMapboxTokenInput(value: String) {
        _aboutState.value = _aboutState.value.copy(mapboxTokenInput = value)
    }

    fun saveMapboxToken() {
        val savedToken = MapboxTokenStorage.save(getApplication(), _aboutState.value.mapboxTokenInput)
        _mapboxAccessToken.value = savedToken
        _aboutState.value = _aboutState.value.copy(
            mapboxTokenInput = savedToken,
            hasConfiguredMapboxToken = isMapboxAccessTokenConfigured(savedToken),
        )
    }

    fun clearMapboxToken() {
        MapboxTokenStorage.clear(getApplication())
        _mapboxAccessToken.value = ""
        _aboutState.value = _aboutState.value.copy(
            mapboxTokenInput = "",
            hasConfiguredMapboxToken = false,
        )
    }

    fun updateWorkerBaseUrlInput(value: String) {
        _aboutState.value = _aboutState.value.copy(workerBaseUrlInput = value)
    }

    fun updateUploadTokenInput(value: String) {
        _aboutState.value = _aboutState.value.copy(uploadTokenInput = value)
    }

    fun saveSampleUploadConfig() {
        val newConfig = TrainingSampleUploadConfig(
            workerBaseUrl = _aboutState.value.workerBaseUrlInput,
            uploadToken = _aboutState.value.uploadTokenInput,
        )
        TrainingSampleUploadConfigStorage.save(getApplication(), newConfig)
        val savedConfig = TrainingSampleUploadConfigStorage.load(getApplication())
        _aboutState.value = _aboutState.value.copy(
            workerBaseUrlInput = savedConfig.workerBaseUrl,
            uploadTokenInput = savedConfig.uploadToken,
            hasConfiguredSampleUpload = hasConfiguredSampleUpload(savedConfig),
            statusMessage = "Worker 上传配置已保存，正在同步 Mapbox Token…",
        )
        syncMapboxTokenFromWorker(
            config = savedConfig,
            forceRefresh = true,
        )
    }

    fun clearSampleUploadConfig() {
        TrainingSampleUploadConfigStorage.clear(getApplication())
        _aboutState.value = _aboutState.value.copy(
            workerBaseUrlInput = "",
            uploadTokenInput = "",
            hasConfiguredSampleUpload = false,
        )
    }

    private fun hasConfiguredSampleUpload(config: TrainingSampleUploadConfig): Boolean {
        return config.workerBaseUrl.isNotBlank() && config.uploadToken.isNotBlank()
    }

    private fun syncMapboxTokenFromWorkerIfNeeded() {
        if (isMapboxAccessTokenConfigured(_mapboxAccessToken.value)) {
            return
        }
        val config = TrainingSampleUploadConfigStorage.load(getApplication())
        if (!hasConfiguredSampleUpload(config)) {
            return
        }
        syncMapboxTokenFromWorker(config, forceRefresh = false)
    }

    private fun syncMapboxTokenFromWorker(
        config: TrainingSampleUploadConfig,
        forceRefresh: Boolean,
    ) {
        if (!hasConfiguredSampleUpload(config)) {
            return
        }
        if (!forceRefresh && isMapboxAccessTokenConfigured(_mapboxAccessToken.value)) {
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.i(TAG, "Syncing Mapbox token from worker ${config.workerBaseUrl}")
                when (val result = workerAppConfigService.load(config)) {
                    is WorkerAppConfigResult.Success -> {
                        val savedToken = MapboxTokenStorage.save(getApplication(), result.mapboxPublicToken)
                        _mapboxAccessToken.value = savedToken
                        _aboutState.value = _aboutState.value.copy(
                            mapboxTokenInput = savedToken,
                            hasConfiguredMapboxToken = isMapboxAccessTokenConfigured(savedToken),
                            statusMessage = "已从 Worker 同步 Mapbox Token",
                        )
                    }

                    is WorkerAppConfigResult.Failure -> {
                        _aboutState.value = _aboutState.value.copy(statusMessage = result.message)
                    }
                }
            } catch (error: Exception) {
                Log.e(TAG, "Mapbox token sync crashed", error)
                DiagnosticLogger.error(
                    context = getApplication(),
                    source = "MainViewModel",
                    message = error.message?.takeIf { it.isNotBlank() } ?: "Mapbox token sync crashed",
                    fingerprint = "mapbox-token-sync-crashed",
                    payloadJson = JSONObject().apply { put("workerConfigured", true) }.toString(),
                )
                val message = error.message?.takeIf { it.isNotBlank() } ?: "同步 Mapbox Token 失败"
                _aboutState.value = _aboutState.value.copy(statusMessage = message)
            }
        }
    }

    fun refreshSyncDiagnostics(showStatusMessage: Boolean = true) {
        if (_aboutState.value.syncDiagnostics.isRefreshing) return
        _aboutState.value = _aboutState.value.copy(
            syncDiagnostics = _aboutState.value.syncDiagnostics.copy(isRefreshing = true),
            statusMessage = if (showStatusMessage) "正在刷新同步诊断…" else _aboutState.value.statusMessage,
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val snapshot = syncDiagnosticsRepository.load()
                _aboutState.value = _aboutState.value.copy(
                    syncDiagnostics = snapshot.toUiState(isRefreshing = false),
                    statusMessage = if (showStatusMessage) "同步诊断已刷新" else _aboutState.value.statusMessage,
                )
            } catch (error: Exception) {
                Log.e(TAG, "Sync diagnostics refresh failed", error)
                DiagnosticLogger.error(
                    context = getApplication(),
                    source = "MainViewModel",
                    message = error.message?.takeIf { it.isNotBlank() } ?: "sync diagnostics refresh failed",
                    fingerprint = "sync-diagnostics-refresh-failed",
                    payloadJson = JSONObject().apply { put("showStatusMessage", showStatusMessage) }.toString(),
                )
                val message = error.message?.takeIf { it.isNotBlank() } ?: "同步诊断刷新失败"
                _aboutState.value = _aboutState.value.copy(
                    syncDiagnostics = _aboutState.value.syncDiagnostics.copy(isRefreshing = false),
                    statusMessage = message,
                )
            }
        }
    }

    private fun SyncDiagnosticsSnapshot.toUiState(isRefreshing: Boolean): SyncDiagnosticsUiState {
        return SyncDiagnosticsUiState(
            rawPointCount = rawPointCount,
            todayDisplayPointCount = todayDisplayPointCount,
            analysisSegmentCount = analysisSegmentCount,
            outboxPendingCount = outboxPendingCount,
            outboxInProgressCount = outboxInProgressCount,
            outboxFailedCount = outboxFailedCount,
            lastError = lastError,
            isRefreshing = isRefreshing,
        )
    }

    fun testWorkerConnectivity() {
        if (_aboutState.value.isTestingWorkerConnectivity) return
        if (_aboutState.value.isCheckingUpdate) {
            _aboutState.value = _aboutState.value.copy(statusMessage = "检查更新进行中，请稍后再测试")
            return
        }

        val workerBaseUrl = _aboutState.value.workerBaseUrlInput.trim()
        if (workerBaseUrl.isBlank()) {
            _aboutState.value = _aboutState.value.copy(statusMessage = "请先填写 Worker 地址")
            return
        }

        _aboutState.value = _aboutState.value.copy(
            isTestingWorkerConnectivity = true,
            statusMessage = "正在检查 Worker…",
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.i(TAG, "Testing worker connectivity for $workerBaseUrl")
                val result = workerConnectivityService.check(workerBaseUrl)
                _aboutState.value = _aboutState.value.copy(
                    statusMessage = when (result) {
                        is WorkerConnectivityResult.Reachable -> result.message
                        is WorkerConnectivityResult.Unreachable -> result.message
                    }
                )
                when (result) {
                    is WorkerConnectivityResult.Reachable -> Log.i(TAG, "Worker connectivity check succeeded: ${result.message}")
                    is WorkerConnectivityResult.Unreachable -> Log.e(TAG, "Worker connectivity check failed: ${result.message}")
                }
            } catch (error: Exception) {
                Log.e(TAG, "Worker connectivity check crashed", error)
                DiagnosticLogger.error(
                    context = getApplication(),
                    source = "MainViewModel",
                    message = error.message?.takeIf { it.isNotBlank() } ?: "worker connectivity check crashed",
                    fingerprint = "worker-connectivity-check-crashed",
                    payloadJson = JSONObject().apply { put("workerBaseUrlConfigured", workerBaseUrl.isNotBlank()) }.toString(),
                )
                val message = error.message?.takeIf { it.isNotBlank() } ?: "Worker 检查失败"
                _aboutState.value = _aboutState.value.copy(statusMessage = message)
            } finally {
                _aboutState.value = _aboutState.value.copy(isTestingWorkerConnectivity = false)
            }
        }
    }

    fun checkForAppUpdate(onUpdateAvailable: (AppUpdateInfo) -> Unit) {
        if (_aboutState.value.isTestingWorkerConnectivity) {
            _aboutState.value = _aboutState.value.copy(statusMessage = "Worker 检查进行中，请稍后再检查更新")
            return
        }
        _aboutState.value = _aboutState.value.copy(isCheckingUpdate = true, statusMessage = null)

        viewModelScope.launch(Dispatchers.IO) {
            val result = updateService.checkForUpdate(currentVersionCode = BuildConfig.APP_VERSION_CODE)
            withContext(Dispatchers.Main) {
                _aboutState.value = _aboutState.value.copy(isCheckingUpdate = false)
                when (result) {
                    is UpdateCheckResult.UpToDate -> {
                        _aboutState.value = _aboutState.value.copy(statusMessage = "当前已是最新版本")
                    }
                    is UpdateCheckResult.Failure -> {
                        _aboutState.value = _aboutState.value.copy(statusMessage = result.message)
                    }
                    is UpdateCheckResult.UpdateAvailable -> {
                        _aboutState.value = _aboutState.value.copy(statusMessage = "发现新版本：${result.info.versionName}")
                        onUpdateAvailable(result.info)
                    }
                }
            }
        }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    val savedStateHandle = extras.createSavedStateHandle()
                    @Suppress("UNCHECKED_CAST")
                    return MainViewModel(application, savedStateHandle) as T
                }
            }
        }
    }
}
