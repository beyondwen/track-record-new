package com.wenhao.record.data.history

import org.json.JSONArray
import org.json.JSONObject

object HistoryTransferCodec {
    private const val CURRENT_VERSION = 1
    private const val KEY_VERSION = "version"
    private const val KEY_EXPORTED_AT = "exportedAt"
    private const val KEY_ITEM_COUNT = "itemCount"
    private const val KEY_ITEMS = "items"

    fun encode(
        items: List<HistoryItem>,
        exportedAtMillis: Long = System.currentTimeMillis()
    ): String {
        val normalizedItems = items
            .map { item -> item.copy(points = item.points.toList()) }
            .sortedWith(compareByDescending<HistoryItem> { it.timestamp }.thenByDescending { it.id })
        return JSONObject().apply {
            put(KEY_VERSION, CURRENT_VERSION)
            put(KEY_EXPORTED_AT, exportedAtMillis)
            put(KEY_ITEM_COUNT, normalizedItems.size)
            put(KEY_ITEMS, JSONArray(HistorySnapshotCodec.encode(normalizedItems)))
        }.toString()
    }

    fun decode(raw: String): List<HistoryItem> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (trimmed.startsWith("[")) {
            return HistorySnapshotCodec.decode(trimmed)
        }

        return runCatching {
            val root = JSONObject(trimmed)
            val items = root.optJSONArray(KEY_ITEMS) ?: return@runCatching emptyList()
            HistorySnapshotCodec.decode(items.toString())
        }.getOrDefault(emptyList())
    }

    fun merge(existingItems: List<HistoryItem>, importedItems: List<HistoryItem>): List<HistoryItem> {
        if (importedItems.isEmpty()) return existingItems
        val importedIds = importedItems.map { it.id }.toSet()
        return (existingItems.filterNot { it.id in importedIds } + importedItems)
            .sortedWith(compareByDescending<HistoryItem> { it.timestamp }.thenByDescending { it.id })
    }
}
