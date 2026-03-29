package com.wenhao.record.data.history

import android.content.Context
import androidx.core.content.edit
import com.wenhao.record.data.local.TrackDatabase
import com.wenhao.record.data.local.history.HistoryPointEntity
import com.wenhao.record.data.local.history.HistoryRecordEntity
import com.wenhao.record.data.local.history.HistoryRecordWithPoints
import com.wenhao.record.data.tracking.TrackDataChangeNotifier
import com.wenhao.record.data.tracking.TrackPoint
import com.wenhao.record.util.AppTaskExecutor
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object HistoryStorage {
    private const val PREFS_NAME = "track_record_history"
    private const val KEY_ITEMS = "items"
    private const val SNAPSHOT_FILE_NAME = "history_snapshot.json"

    private val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val cacheLock = Any()

    @Volatile
    private var historyCache: List<HistoryItem> = emptyList()

    @Volatile
    private var historyCacheInitialized = false

    @Volatile
    private var historyCacheLoading = false

    @Volatile
    private var dailyCache: List<HistoryDayItem> = emptyList()

    @Volatile
    private var dailyCacheInitialized = false

    private val readyCallbacks = mutableListOf<(List<HistoryItem>) -> Unit>()

    fun load(context: Context): MutableList<HistoryItem> {
        ensureHistoryCache(context)
        return copyHistoryList(historyCache)
    }

    fun peek(context: Context): MutableList<HistoryItem> {
        ensureHistoryCacheAsync(context)
        return copyHistoryList(historyCache)
    }

    fun loadDaily(context: Context): List<HistoryDayItem> {
        ensureHistoryCache(context)
        ensureDailyCache()
        return copyDailyList(dailyCache)
    }

    fun peekDaily(context: Context): List<HistoryDayItem> {
        ensureHistoryCacheAsync(context)
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

    fun peekDailyByStart(context: Context, dayStartMillis: Long): HistoryDayItem? {
        ensureHistoryCacheAsync(context)
        ensureDailyCache()
        return dailyCache.firstOrNull { item -> item.dayStartMillis == dayStartMillis }
            ?.let(::copyDailyItem)
    }

    fun isReady(): Boolean = historyCacheInitialized

    fun whenReady(context: Context, callback: (List<HistoryItem>) -> Unit) {
        val readySnapshot = synchronized(cacheLock) {
            if (historyCacheInitialized) {
                copyHistoryList(historyCache)
            } else {
                readyCallbacks += callback
                null
            }
        }
        if (readySnapshot != null) {
            AppTaskExecutor.runOnMain {
                callback(readySnapshot)
            }
            return
        }
        ensureHistoryCacheAsync(context)
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
                            accuracyMeters = point.accuracyMeters,
                            altitudeMeters = point.altitudeMeters
                        )
                    }
                }
            )
            persistSnapshot(context, normalizedItems)
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
            persistSnapshot(context, synchronized(cacheLock) { historyCache })
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
            persistSnapshot(context, synchronized(cacheLock) { historyCache })
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
            persistSnapshot(context, synchronized(cacheLock) { historyCache })
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
            persistSnapshot(context, synchronized(cacheLock) { historyCache })
        }

        TrackDataChangeNotifier.notifyHistoryChanged()
    }

    private fun ensureHistoryCache(context: Context) {
        if (historyCacheInitialized) return

        synchronized(cacheLock) {
            if (historyCacheInitialized) return
            val loadedHistory = ioExecutor.submit<List<HistoryItem>> {
                loadHistoryFromDisk(context)
            }.get()
            historyCache = loadedHistory
            refreshDailyCacheLocked()
            if (loadedHistory.isNotEmpty()) {
                ioExecutor.execute {
                    persistSnapshot(context, loadedHistory)
                }
            }
            historyCacheInitialized = true
        }
    }

    fun warmUp(context: Context) {
        ensureHistoryCacheAsync(context)
    }

    private fun ensureHistoryCacheAsync(context: Context) {
        if (historyCacheInitialized || historyCacheLoading) return

        synchronized(cacheLock) {
            if (historyCacheInitialized || historyCacheLoading) return
            historyCacheLoading = true
        }

        ioExecutor.execute {
            val loadedHistory = loadHistoryFromDisk(context)
            val callbacksToDispatch = synchronized(cacheLock) {
                historyCache = loadedHistory
                refreshDailyCacheLocked()
                historyCacheInitialized = true
                historyCacheLoading = false
                readyCallbacks.toList().also { readyCallbacks.clear() }
            }
            if (loadedHistory.isNotEmpty()) {
                persistSnapshot(context, loadedHistory)
            }
            TrackDataChangeNotifier.notifyHistoryChanged()
            if (callbacksToDispatch.isNotEmpty()) {
                callbacksToDispatch.forEach { callback ->
                    AppTaskExecutor.runOnMain {
                        callback(copyHistoryList(loadedHistory))
                    }
                }
            }
        }
    }

    private fun ensureDailyCache() {
        if (dailyCacheInitialized) return

        synchronized(cacheLock) {
            if (dailyCacheInitialized) return
            refreshDailyCacheLocked()
        }
    }

    private fun loadHistoryFromDisk(context: Context): List<HistoryItem> {
        migrateLegacyHistoryIfNeeded(context)
        restoreSnapshotIfNeeded(context)
        return TrackDatabase.getInstance(context)
            .historyDao()
            .getHistoryWithPoints()
            .map { it.toModel() }
    }

    private fun migrateLegacyHistoryIfNeeded(context: Context) {
        val raw = prefs(context).getString(KEY_ITEMS, null) ?: return
        val dao = TrackDatabase.getInstance(context).historyDao()
        val hasRoomData = dao.getHistoryCount() > 0
        if (hasRoomData) {
            prefs(context).edit {
                remove(KEY_ITEMS)
            }
            return
        }

        val legacyItems = parseLegacyHistory(raw)
        if (legacyItems.isEmpty()) {
            prefs(context).edit {
                remove(KEY_ITEMS)
            }
            return
        }

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
                        accuracyMeters = point.accuracyMeters,
                        altitudeMeters = point.altitudeMeters
                    )
                }
            }
        )
        persistSnapshot(context, legacyItems)
        prefs(context).edit {
            remove(KEY_ITEMS)
        }
    }

    private fun restoreSnapshotIfNeeded(context: Context) {
        val dao = TrackDatabase.getInstance(context).historyDao()
        val hasRoomData = dao.getHistoryCount() > 0
        if (hasRoomData) return

        val snapshotItems = loadSnapshot(context)
        if (snapshotItems.isEmpty()) return

        dao.replaceAll(
            records = snapshotItems.map { item -> item.toRecordEntity() },
            points = snapshotItems.flatMap { item -> item.toPointEntities() }
        )
    }

    private fun parseLegacyHistory(raw: String): List<HistoryItem> {
        return HistorySnapshotCodec.decode(raw)
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
                    accuracyMeters = point.accuracyMeters,
                    altitudeMeters = point.altitudeMeters,
                    wgs84Latitude = point.wgs84Latitude,
                    wgs84Longitude = point.wgs84Longitude
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
                accuracyMeters = point.accuracyMeters,
                altitudeMeters = point.altitudeMeters,
                wgs84Latitude = point.wgs84Latitude,
                wgs84Longitude = point.wgs84Longitude
            )
        }
    }

    private fun refreshDailyCacheLocked() {
        dailyCache = HistoryDayAggregator.aggregate(historyCache)
        dailyCacheInitialized = true
    }

    private fun persistSnapshot(context: Context, items: List<HistoryItem>) {
        val normalizedItems = items.map { item -> item.copy(points = item.points.toList()) }
        val snapshotFile = context.getFileStreamPath(SNAPSHOT_FILE_NAME)
        val tempFile = File(snapshotFile.parentFile, "$SNAPSHOT_FILE_NAME.tmp")
        tempFile.bufferedWriter().use { writer ->
            writer.write(HistorySnapshotCodec.encode(normalizedItems))
        }
        if (snapshotFile.exists() && !snapshotFile.delete()) {
            tempFile.delete()
            return
        }
        if (!tempFile.renameTo(snapshotFile)) {
            tempFile.delete()
        }
    }

    private fun loadSnapshot(context: Context): List<HistoryItem> {
        return runCatching {
            context.openFileInput(SNAPSHOT_FILE_NAME).bufferedReader().use { reader ->
                parseLegacyHistory(reader.readText())
            }
        }.getOrDefault(emptyList())
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
