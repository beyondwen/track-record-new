package com.wenhao.record.data.tracking

import android.content.Context
import androidx.core.content.edit

object TrainingSampleUploadConfigStorage {
    internal const val PREFS_NAME = "training_sample_upload_config"
    internal const val KEY_WORKER_BASE_URL = "worker_base_url"
    internal const val KEY_UPLOAD_TOKEN = "upload_token"

    fun load(context: Context): TrainingSampleUploadConfig {
        val prefs = prefs(context)
        return TrainingSampleUploadConfig(
            workerBaseUrl = sanitizeWorkerBaseUrl(
                prefs.getString(KEY_WORKER_BASE_URL, "").orEmpty()
            ),
            uploadToken = sanitizeUploadToken(
                prefs.getString(KEY_UPLOAD_TOKEN, "").orEmpty()
            )
        )
    }

    fun save(context: Context, config: TrainingSampleUploadConfig) {
        val sanitizedConfig = sanitize(config)
        prefs(context).edit {
            if (sanitizedConfig.workerBaseUrl.isEmpty()) {
                remove(KEY_WORKER_BASE_URL)
            } else {
                putString(KEY_WORKER_BASE_URL, sanitizedConfig.workerBaseUrl)
            }
            if (sanitizedConfig.uploadToken.isEmpty()) {
                remove(KEY_UPLOAD_TOKEN)
            } else {
                putString(KEY_UPLOAD_TOKEN, sanitizedConfig.uploadToken)
            }
        }
    }

    fun clear(context: Context) {
        prefs(context).edit {
            remove(KEY_WORKER_BASE_URL)
            remove(KEY_UPLOAD_TOKEN)
        }
    }

    private fun sanitize(config: TrainingSampleUploadConfig): TrainingSampleUploadConfig {
        return TrainingSampleUploadConfig(
            workerBaseUrl = sanitizeWorkerBaseUrl(config.workerBaseUrl),
            uploadToken = sanitizeUploadToken(config.uploadToken)
        )
    }

    private fun sanitizeWorkerBaseUrl(value: String): String {
        return value.trim().trimEnd('/')
    }

    private fun sanitizeUploadToken(value: String): String {
        return value.trim()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
