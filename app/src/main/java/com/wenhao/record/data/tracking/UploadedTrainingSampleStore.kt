package com.wenhao.record.data.tracking

import android.content.Context

object UploadedTrainingSampleStore {
    internal const val PREFS_NAME = "uploaded_training_sample_store"
    internal const val KEY_UPLOADED_EVENT_IDS = "uploaded_event_ids"
    private val lock = Any()

    fun load(context: Context): Set<Long> {
        synchronized(lock) {
            val storedValues = prefs(context)
                .getStringSet(KEY_UPLOADED_EVENT_IDS, emptySet())
                ?.let(::LinkedHashSet)
                .orEmpty()
            val cleanedEventIds = storedValues.mapNotNull { it.toLongOrNull() }.toSet()
            val cleanedStoredValues = cleanedEventIds.map { it.toString() }.toSet()

            if (storedValues.isNotEmpty() && cleanedStoredValues != storedValues) {
                prefs(context).edit()
                    .putStringSet(KEY_UPLOADED_EVENT_IDS, cleanedStoredValues)
                    .apply()
            }

            return cleanedEventIds
        }
    }

    fun markUploaded(context: Context, eventIds: List<Long>) {
        synchronized(lock) {
            if (eventIds.isEmpty()) {
                return
            }

            val updatedIds = load(context).toMutableSet().apply {
                addAll(eventIds)
            }

            prefs(context).edit()
                .putStringSet(KEY_UPLOADED_EVENT_IDS, updatedIds.map { it.toString() }.toSet())
                .apply()
        }
    }

    fun clear(context: Context) {
        synchronized(lock) {
            prefs(context).edit()
                .remove(KEY_UPLOADED_EVENT_IDS)
                .apply()
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
