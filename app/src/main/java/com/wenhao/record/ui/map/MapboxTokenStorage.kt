package com.wenhao.record.ui.map

import android.content.Context
import androidx.core.content.edit

object MapboxTokenStorage {
    private const val PREFS_NAME = "mapbox_token_storage"
    private const val KEY_ACCESS_TOKEN = "access_token"

    fun load(context: Context): String {
        return sanitizeMapboxAccessToken(
            prefs(context).getString(KEY_ACCESS_TOKEN, "").orEmpty()
        )
    }

    fun save(context: Context, token: String): String {
        val sanitizedToken = sanitizeMapboxAccessToken(token)
        prefs(context).edit {
            if (sanitizedToken.isEmpty()) {
                remove(KEY_ACCESS_TOKEN)
            } else {
                putString(KEY_ACCESS_TOKEN, sanitizedToken)
            }
        }
        return sanitizedToken
    }

    fun clear(context: Context) {
        prefs(context).edit { remove(KEY_ACCESS_TOKEN) }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

internal fun sanitizeMapboxAccessToken(token: String): String = token.trim()

internal fun resolveMapboxAccessToken(runtimeToken: String, bundledToken: String): String {
    val sanitizedRuntimeToken = sanitizeMapboxAccessToken(runtimeToken)
    if (sanitizedRuntimeToken.isNotEmpty()) {
        return sanitizedRuntimeToken
    }
    return sanitizeMapboxAccessToken(bundledToken)
}
