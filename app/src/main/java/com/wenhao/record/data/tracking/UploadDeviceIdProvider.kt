package com.wenhao.record.data.tracking

import android.content.Context
import android.provider.Settings

fun uploadDeviceId(context: Context): String {
    val androidId = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ANDROID_ID,
    )
    return if (androidId.isNullOrBlank()) {
        context.packageName
    } else {
        androidId
    }
}
