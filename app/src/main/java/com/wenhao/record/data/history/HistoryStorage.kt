package com.wenhao.record.data.history

import android.content.Context
import com.wenhao.record.data.local.TrackDatabase
import com.wenhao.record.data.local.history.HistoryPointEntity
import com.wenhao.record.data.local.history.HistoryRecordEntity
import com.wenhao.record.data.local.history.HistoryRecordWithPoints
import com.wenhao.record.data.tracking.TrackDataChangeNotifier
import com.wenhao.record.data.tracking.TrackPoint
import org.json.JSONArray
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object HistoryStorage {
    private const val PREFS_NAME = "track_record_history"
    private const val KEY_ITEMS = "items"

    private val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val cacheLock = Any()

    @Volatile
    private var historyCache: List<HistoryItem> = emptyList()

    @Volatile
    private var historyCacheInitialized = false

    @Volatile
    private var dailyCache: List<HistoryDayItem> = emptyList()

    @Volatile
    private var dailyCacheInitialized = false

    fun load(context: Context): MutableList<HistoryItem> {
        ensureHistoryCache(context)
        return copyHistoryList(historyCache)
    }

    fun loadDaily(context: Context): List<HistoryDayItem> {
        ensureHistoryCache(context)
        ensureDailyCache()
        return copyDailyList(dailyCache)
    }

    fun loadById(context: Context, historyId: Long): HistoryItem? {
        ensureHistoryCache(context)
        return historyCache.firstOrNull { item -> item.id == historyId }
            ?.let { item -> item.copy(points = item.points.toList()) }
    }

    fun loadDailyByStart(context: Context, dayStartMillis: Long): HistoryDayItem? {
        ensureHistoryCache(context)
        ensureDailyCache()
        return dailyCache.firstOrNull { item -> item.dayStartMillis == dayStartMillis }
            ?.let(::copyDailyItem)
    }

    fun save(context: Context, items: List<HistoryItem>) {
        ensureHistoryCache(context)
        val normalizedItems = items.map { it.copy(points = it.points.toList()) }
            .sortedWith(compareByDescending<HistoryItem> { it.timestamp }.thenByDescending { it.id })
        synchronized(cacheLock) {
            historyCache = normalizedItems
            refreshDailyCacheLocked()
        }

        ioExecutor.execute {
            val dao = TrackDatabase.getInstance(context).historyDao()
            dao.replaceAll(
                records = normalizedItems.map { item ->
                    HistoryRecordEntity(
                        historyId = item.id,
                        timestamp = item.timestamp,
                        distanceKm = item.distanceKm,
                        durationSeconds = item.durationSeconds,
                        averageSpeedKmh = item.averageSpeedKmh,
                        title = item.title
                    )
                },
                points = normalizedItems.flatMap { item ->
                    item.points.mapIndexed { index, point ->
                        HistoryPointEntity(
                            historyId = item.id,
                            pointOrder = index,
                            latitude = point.latitude,
                            longitude = point.longitude,
                            timestampMillis = point.timestampMillis,
                            accuracyMeters = point.accuracyMeters
                        )
                    }
                }
            )
        }

        TrackDataChangeNotifier.notifyHistoryChanged()
    }

    fun nextHistoryId(context: Context): Long {
        ensureHistoryCache(context)
        synchronized(cacheLock) {
            return (historyCache.maxOfOrNull { it.id } ?: 0L) + 1L
        }
    }

    fun add(context: Context, item: HistoryItem) {
        ensureHistoryCache(context)
        val normalizedItem = item.copy(points = item.points.toList())
        synchronized(cacheLock) {
            historyCache = (historyCache.filterNot { it.id == normalizedItem.id } + normalizedItem)
                .sortedWith(compareByDescending<HistoryItem> { it.timestamp }.thenByDescending { it.id })
            refreshDailyCacheLocked()
        }

        ioExecutor.execute {
            TrackDatabase.getInstance(context).historyDao().upsertHistory(
                record = normalizedItem.toRecordEntity(),
                points = normalizedItem.toPointEntities()
            )
        }

        TrackDataChangeNotifier.notifyHistoryChanged()
    }

    fun rename(context: Context, historyId: Long, newTitle: String?) {
        ensureHistoryCache(context)
        var updated = false
        synchronized(cacheLock) {
            historyCache = historyCache.map { item ->
                if (item.id != historyId) {
                    item
                } else {
                    updated = true
                    item.copy(title = newTitle)
                }
            }
            if (updated) {
                refreshDailyCacheLocked()
            }
        }
        if (!updated) return

        ioExecutor.execute {
            TrackDatabase.getInstance(context).historyDao().updateTitle(historyId, newTitle)
        }

        TrackDataChangeNotifier.notifyHistoryChanged()
    }

    fun delete(context: Context, historyId: Long) {
        ensureHistoryCache(context)
        var removed = false
        synchronized(cacheLock) {
            val updatedItems = historyCache.filterNot { item -> item.id == historyId }
            removed = updatedItems.size != historyCache.size
            if (removed) {
                historyCache = updatedItems
                refreshDailyCacheLocked()
            }
        }
        if (!removed) return

        ioExecutor.execute {
            TrackDatabase.getInstance(context).historyDao().deleteHistory(historyId)
        }

        TrackDataChangeNotifier.notifyHistoryChanged()
    }

    fun deleteMany(context: Context, historyIds: List<Long>) {
        if (historyIds.isEmpty()) return
        ensureHistoryCache(context)
        val targetIds = historyIds.toSet()
        var removed = false
        synchronized(cacheLock) {
            val updatedItems = historyCache.filterNot { item -> item.id in targetIds }
            removed = updatedItems.size != historyCache.size
            if (removed) {
                historyCache = updatedItems
                refreshDailyCacheLocked()
            }
        }
        if (!removed) return

        ioExecutor.execute {
            TrackDatabase.getInstance(context).historyDao().deleteHistoryList(historyIds)
        }

        TrackDataChangeNotifier.notifyHistoryChanged()
    }

    private fun ensureHistoryCache(context: Context) {
        if (historyCacheInitialized) return

        synchronized(cacheLock) {
            if (historyCacheInitialized) return
            migrateLegacyHistoryIfNeeded(context)
            val loadedHistory = ioExecutor.submit<List<HistoryItem>> {
                TrackDatabase.getInstance(context)
                    .historyDao()
                    .getHistoryWithPoints()
                    .map { it.toModel() }
            }.get()
            historyCache = loadedHistory
            refreshDailyCacheLocked()
            historyCacheInitialized = true
        }
    }

    private fun ensureDailyCache() {
        if (dailyCacheInitialized) return

        synchronized(cacheLock) {
            if (dailyCacheInitialized) return
            refreshDailyCacheLocked()
        }
    }

    private fun migrateLegacyHistoryIfNeeded(context: Context) {
        val raw = prefs(context).getString(KEY_ITEMS, null) ?: return
        val dao = TrackDatabase.getInstance(context).historyDao()
        val hasRoomData = ioExecutor.submit<Int> { dao.getHistoryCount() }.get() > 0
        if (hasRoomData) {
            prefs(context).edit().remove(KEY_ITEMS).apply()
            return
        }

        val legacyItems = parseLegacyHistory(raw)
        if (legacyItems.isEmpty()) {
            prefs(context).edit().remove(KEY_ITEMS).apply()
            return
        }

        ioExecutor.submit {
            dao.replaceAll(
                records = legacyItems.map { item ->
                    HistoryRecordEntity(
                        historyId = item.id,
                        timestamp = item.timestamp,
                        distanceKm = item.distanceKm,
                        durationSeconds = item.durationSeconds,
                        averageSpeedKmh = item.averageSpeedKmh,
                        title = item.title
                    )
                },
                points = legacyItems.flatMap { item ->
                    item.points.mapIndexed { index, point ->
                        HistoryPointEntity(
                            historyId = item.id,
                            pointOrder = index,
                            latitude = point.latitude,
                            longitude = point.longitude,
                            timestampMillis = point.timestampMillis,
                            accuracyMeters = point.accuracyMeters
                        )
                    }
                }
            )
        }.get()
        prefs(context).edit().remove(KEY_ITEMS).apply()
    }

    private fun parseLegacyHistory(raw: String): List<HistoryItem> {
        return runCatching {
            val jsonArray = JSONArray(raw)
            buildList {
                for (index in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(index)
                    val pointsArray = obj.optJSONArray("points") ?: JSONArray()
                    val points = buildList {
                        for (pointIndex in 0 until pointsArray.length()) {
                            val point = pointsArray.getJSONObject(pointIndex)
                            add(
                                TrackPoint(
                                    latitude = point.getDouble("latitude"),
                                    longitude = point.getDouble("longitude"),
                                    timestampMillis = point.optLong("timestampMillis"),
                                    accuracyMeters = point.optDouble("accuracyMeters")
                                        .takeUnless { point.isNull("accuracyMeters") }
                                        ?.toFloat()
                                )
                            )
                        }
                    }
                    add(
                        HistoryItem(
                            id = obj.getLong("id"),
                            timestamp = obj.getLong("timestamp"),
                            distanceKm = obj.getDouble("distanceKm"),
                            durationSeconds = obj.getInt("durationSeconds"),
                            averageSpeedKmh = obj.getDouble("averageSpeedKmh"),
                            title = obj.optString("title").takeIf { it.isNotBlank() },
                            points = points
                        )
                    )
                }
            }
        }.getOrDefault(emptyList()).sortedWith(
            compareByDescending<HistoryItem> { it.timestamp }.thenByDescending { it.id }
        )
    }

    private fun HistoryRecordWithPoints.toModel(): HistoryItem {
        return HistoryItem(
            id = record.historyId,
            timestamp = record.timestamp,
            distanceKm = record.distanceKm,
            durationSeconds = record.durationSeconds,
            averageSpeedKmh = record.averageSpeedKmh,
            title = record.title,
            points = points.sortedBy { it.pointOrder }.map { point ->
                TrackPoint(
                    latitude = point.latitude,
                    longitude = point.longitude,
                    timestampMillis = point.timestampMillis,
                    accuracyMeters = point.accuracyMeters
                )
            }
        )
    }

    private fun HistoryItem.toRecordEntity(): HistoryRecordEntity {
        return HistoryRecordEntity(
            historyId = id,
            timestamp = timestamp,
            distanceKm = distanceKm,
            durationSeconds = durationSeconds,
            averageSpeedKmh = averageSpeedKmh,
            title = title
        )
    }

    private fun HistoryItem.toPointEntities(): List<HistoryPointEntity> {
        return points.mapIndexed { index, point ->
            HistoryPointEntity(
                historyId = id,
                pointOrder = index,
                latitude = point.latitude,
                longitude = point.longitude,
                timestampMillis = point.timestampMillis,
                accuracyMeters = point.accuracyMeters
            )
        }
    }

    private fun refreshDailyCacheLocked() {
        dailyCache = HistoryDayAggregator.aggregate(historyCache)
        dailyCacheInitialized = true
    }

    private fun copyHistoryList(items: List<HistoryItem>): MutableList<HistoryItem> {
        return items.map { it.copy(points = it.points.toList()) }.toMutableList()
    }

    private fun copyDailyList(items: List<HistoryDayItem>): List<HistoryDayItem> {
        return items.map(::copyDailyItem)
    }

    private fun copyDailyItem(item: HistoryDayItem): HistoryDayItem {
        return item.copy(
            sourceIds = item.sourceIds.toList(),
            segments = item.segments.map { segment -> segment.toList() },
            points = item.points.toList()
        )
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
