package com.wenhao.record.update

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

internal const val APK_MIME_TYPE = "application/vnd.android.package-archive"

class ApkDownloadInstaller(
    private val context: Context,
) {
    fun download(apkUrl: String, fileName: String = "app-debug.apk"): File {
        val targetFile = File(context.cacheDir, fileName)
        val connection = URL(apkUrl).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.instanceFollowRedirects = true
        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IllegalStateException("Unexpected HTTP $responseCode while downloading APK")
            }
            connection.inputStream.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            return targetFile
        } finally {
            connection.disconnect()
        }
    }

    fun createInstallIntent(apkFile: File): Intent {
        val contentUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )
        return buildInstallIntent(contentUri)
    }

    fun createFallbackInstallIntent(apkFile: File): Intent {
        val contentUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )
        return buildInstallFallbackIntent(contentUri)
    }
}

internal fun buildInstallIntent(contentUri: Uri): Intent {
    return Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
        setDataAndType(contentUri, APK_MIME_TYPE)
        clipData = ClipData.newRawUri("apk", contentUri)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

internal fun buildInstallFallbackIntent(contentUri: Uri): Intent {
    return Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(contentUri, APK_MIME_TYPE)
        clipData = ClipData.newRawUri("apk", contentUri)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
