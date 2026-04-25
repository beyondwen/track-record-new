package com.wenhao.record.data.diagnostics

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

class DiagnosticLogStore(context: Context) {
    private val appContext = context.applicationContext

    fun enqueue(entry: DiagnosticLogEntry) {
        synchronized(LOCK) {
            val entries = loadLocked().toMutableList()
            val existingIndex = entries.indexOfFirst { it.fingerprint == entry.fingerprint && it.type == entry.type }
            if (existingIndex >= 0) {
                entries[existingIndex] = entry
            } else {
                entries += entry
            }
            saveLocked(entries.takeLast(MAX_QUEUE_SIZE))
        }
    }

    fun loadBatch(limit: Int): List<DiagnosticLogEntry> {
        synchronized(LOCK) {
            return loadLocked().take(limit.coerceAtLeast(1))
        }
    }

    fun removeByLogIds(logIds: Set<String>) {
        if (logIds.isEmpty()) return
        synchronized(LOCK) {
            saveLocked(loadLocked().filterNot { it.logId in logIds })
        }
    }

    private fun loadLocked(): List<DiagnosticLogEntry> {
        val raw = prefs().getString(KEY_LOGS, "[]").orEmpty()
        val array = try {
            JSONArray(raw)
        } catch (_: Exception) {
            JSONArray()
        }
        return buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index) ?: continue
                val entry = json.toEntry() ?: continue
                add(entry)
            }
        }
    }

    private fun saveLocked(entries: List<DiagnosticLogEntry>) {
        val array = JSONArray().apply {
            entries.forEach { entry -> put(entry.toJson()) }
        }
        prefs().edit { putString(KEY_LOGS, array.toString()) }
    }

    private fun DiagnosticLogEntry.toJson(): JSONObject {
        return JSONObject().apply {
            put("logId", logId)
            put("occurredAt", occurredAt)
            put("type", type.name)
            put("severity", severity.name)
            put("source", source)
            put("message", message)
            put("fingerprint", fingerprint)
            put("payloadJson", payloadJson ?: JSONObject.NULL)
        }
    }

    private fun JSONObject.toEntry(): DiagnosticLogEntry? {
        return try {
            DiagnosticLogEntry(
                logId = getString("logId"),
                occurredAt = getLong("occurredAt"),
                type = DiagnosticLogType.valueOf(getString("type")),
                severity = DiagnosticLogSeverity.valueOf(getString("severity")),
                source = getString("source"),
                message = getString("message"),
                fingerprint = getString("fingerprint"),
                payloadJson = optString("payloadJson").takeIf { it.isNotBlank() },
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun prefs() = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "diagnostic_logs"
        private const val KEY_LOGS = "logs"
        private const val MAX_QUEUE_SIZE = 200
        private val LOCK = Any()
    }
}
