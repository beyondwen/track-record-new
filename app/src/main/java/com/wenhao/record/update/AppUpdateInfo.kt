package com.wenhao.record.update

data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkName: String,
    val apkUrl: String,
)

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val info: AppUpdateInfo) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data class Failure(val message: String) : UpdateCheckResult
}
