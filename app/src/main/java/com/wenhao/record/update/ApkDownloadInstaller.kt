package com.wenhao.record.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class ApkDownloadInstaller(
    private val context: Context,
    private val authToken: String = "",
) {
    fun download(apkUrl: String, fileName: String = "app-debug.apk"): File {
        val targetFile = File(context.cacheDir, fileName)
        val connection = URL(apkUrl).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        if (authToken.isNotBlank()) {
            connection.setRequestProperty("Accept", "application/octet-stream")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("Authorization", "Bearer $authToken")
        }
        connection.inputStream.use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return targetFile
    }

    fun createInstallIntent(apkFile: File): Intent {
        val contentUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
