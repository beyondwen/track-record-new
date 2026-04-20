package com.wenhao.record.data.tracking

import android.content.Context
import androidx.core.content.edit
import java.util.UUID

internal const val UPLOAD_DEVICE_ID_PREFS_NAME = "upload_device_id"
internal const val KEY_INSTALLATION_ID = "installation_id"
private val uploadDeviceIdLock = Any()

fun uploadDeviceId(context: Context): String {
    val appContext = context.applicationContext
    val prefs = prefs(appContext)
    prefs.getString(KEY_INSTALLATION_ID, null)?.takeIf { it.isNotBlank() }?.let { return it }

    synchronized(uploadDeviceIdLock) {
        prefs.getString(KEY_INSTALLATION_ID, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val installationId = UUID.randomUUID().toString()
        prefs.edit { putString(KEY_INSTALLATION_ID, installationId) }
        return installationId
    }
}

private fun prefs(context: Context) =
    context.getSharedPreferences(UPLOAD_DEVICE_ID_PREFS_NAME, Context.MODE_PRIVATE)
