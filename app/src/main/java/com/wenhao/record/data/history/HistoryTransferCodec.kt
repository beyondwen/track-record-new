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
            val itemsIndex = trimmed.indexOf(""""$KEY_ITEMS":""")
            if (itemsIndex == -1) return@runCatching emptyList()
            
            val arrayStart = trimmed.indexOf('[', itemsIndex)
            if (arrayStart == -1) return@runCatching emptyList()
            
            val arrayEnd = trimmed.lastIndexOf(']')
            if (arrayEnd == -1 || arrayEnd < arrayStart) return@runCatching emptyList()
            
            val itemsJson = trimmed.substring(arrayStart, arrayEnd + 1)
            HistorySnapshotCodec.decode(itemsJson)
        }.getOrDefault(emptyList())
    }

    fun merge(existingItems: List<HistoryItem>, importedItems: List<HistoryItem>): List<HistoryItem> {
        if (importedItems.isEmpty()) return existingItems
        val importedIds = importedItems.map { it.id }.toSet()
        return (existingItems.filterNot { it.id in importedIds } + importedItems)
            .sortedWith(compareByDescending<HistoryItem> { it.timestamp }.thenByDescending { it.id })
    }
}
