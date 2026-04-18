package com.wenhao.record.data.history

import android.content.Context

object UploadedHistoryStore {
    internal const val PREFS_NAME = "uploaded_history_store"
    internal const val KEY_UPLOADED_HISTORY_IDS = "uploaded_history_ids"
    private val lock = Any()

    fun load(context: Context): Set<Long> {
        synchronized(lock) {
            val storedValues = prefs(context)
                .getStringSet(KEY_UPLOADED_HISTORY_IDS, emptySet())
                ?.let(::LinkedHashSet)
                .orEmpty()
            val cleanedHistoryIds = storedValues.mapNotNull { it.toLongOrNull() }.toSet()
            val cleanedStoredValues = cleanedHistoryIds.map { it.toString() }.toSet()

            if (storedValues.isNotEmpty() && cleanedStoredValues != storedValues) {
                prefs(context).edit()
                    .putStringSet(KEY_UPLOADED_HISTORY_IDS, cleanedStoredValues)
                    .apply()
            }

            return cleanedHistoryIds
        }
    }

    fun markUploaded(context: Context, historyIds: List<Long>) {
        synchronized(lock) {
            if (historyIds.isEmpty()) {
                return
            }

            val updatedIds = load(context).toMutableSet().apply {
                addAll(historyIds)
            }

            prefs(context).edit()
                .putStringSet(KEY_UPLOADED_HISTORY_IDS, updatedIds.map { it.toString() }.toSet())
                .apply()
        }
    }

    fun clear(context: Context) {
        synchronized(lock) {
            prefs(context).edit()
                .remove(KEY_UPLOADED_HISTORY_IDS)
                .apply()
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
