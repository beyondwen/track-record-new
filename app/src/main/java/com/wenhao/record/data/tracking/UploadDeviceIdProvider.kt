package com.wenhao.record.data.tracking

import android.content.Context
import androidx.core.content.edit

internal const val UPLOAD_DEVICE_ID_PREFS_NAME = "upload_device_id"
internal const val KEY_INSTALLATION_ID = "installation_id"
internal const val DEFAULT_UPLOAD_DEVICE_ID = "wenhao151"
private val uploadDeviceIdLock = Any()

fun uploadDeviceId(context: Context): String {
    val appContext = context.applicationContext
    val prefs = prefs(appContext)
    prefs.getString(KEY_INSTALLATION_ID, null)
        ?.takeIf { it == DEFAULT_UPLOAD_DEVICE_ID }
        ?.let { return it }

    synchronized(uploadDeviceIdLock) {
        prefs.getString(KEY_INSTALLATION_ID, null)
            ?.takeIf { it == DEFAULT_UPLOAD_DEVICE_ID }
            ?.let { return it }
        prefs.edit { putString(KEY_INSTALLATION_ID, DEFAULT_UPLOAD_DEVICE_ID) }
        return DEFAULT_UPLOAD_DEVICE_ID
    }
}

private fun prefs(context: Context) =
    context.getSharedPreferences(UPLOAD_DEVICE_ID_PREFS_NAME, Context.MODE_PRIVATE)
