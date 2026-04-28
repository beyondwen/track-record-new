package com.wenhao.record.data.history

import android.content.Context
import com.wenhao.record.data.local.TrackDatabase
import com.wenhao.record.data.local.history.HistoryPointEntity
import com.wenhao.record.data.local.history.HistoryRecordEntity
import com.wenhao.record.data.local.history.HistoryRecordWithPoints
import com.wenhao.record.data.tracking.TrackDataChangeNotifier
import com.wenhao.record.data.tracking.TrackPoint
import com.wenhao.record.util.AppTaskExecutor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object HistoryStorage {
    private val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private val cacheLock = java.lang.Object()

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

    suspend fun load(context: Context): MutableList<HistoryItem> {
        val appContext = context.applicationContext
        return withContext(Dispatchers.IO) {
            ensureHistoryCache(appContext)
            copyHistoryList(historyCache)
        }
    }

    fun peek(context: Context): MutableList<HistoryItem> {
        ensureHistoryCacheAsync(context)
        return copyHistoryList(historyCache)
    }

    suspend fun loadDaily(context: Context): List<HistoryDayItem> {
        val appContext = context.applicationContext
        return withContext(Dispatchers.IO) {
            ensureHistoryCache(appContext)
            LocalHistoryRepository(
                TrackDatabase.getInstance(appContext).historyDao(),
            ).loadDailyItems()
        }
    }

    fun peekDaily(context: Context): List<HistoryDayItem> {
        ensureHistoryCacheAsync(context)
        ensureDailyCache()
        return copyDailyList(dailyCache)
    }

    suspend fun loadById(context: Context, historyId: Long): HistoryItem? {
        val appContext = context.applicationContext
        return withContext(Dispatchers.IO) {
            ensureHistoryCache(appContext)
            historyCache.firstOrNull { item -> item.id == historyId }
        }
    }

    suspend fun loadDailyByStart(context: Context, dayStartMillis: Long): HistoryDayItem? {
        val appContext = context.applicationContext
        return withContext(Dispatchers.IO) {
            ensureHistoryCache(appContext)
            LocalHistoryRepository(
                TrackDatabase.getInstance(appContext).historyDao(),
            ).loadDayDetail(dayStartMillis)
        }
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

    suspend fun save(context: Context, items: List<HistoryItem>) {
        ensureHistoryCache(context)
        val normalizedItems = items.map { it.copy(points = it.points.toList()) }
            .sortedWith(compareByDescending<HistoryItem> { it.timestamp }.thenByDescending { it.id })
        synchronized(cacheLock) {
            historyCache = normalizedItems
            refreshDailyCacheLocked()
        }

        withContext(Dispatchers.IO) {
            val dao = TrackDatabase.getInstance(context).historyDao()
            dao.replaceAll(
                records = normalizedItems.map { item ->
                    HistoryRecordEntity(
                        historyId = item.id,
                        dateKey = HistoryDayAggregator.startOfDay(item.timestamp),
                        timestamp = item.timestamp,
                        distanceKm = item.distanceKm,
                        durationSeconds = item.durationSeconds,
                        averageSpeedKmh = item.averageSpeedKmh,
                        title = item.title,
                        startSource = item.startSource.name,
                        stopSource = item.stopSource.name,
                        manualStartAt = item.manualStartAt,
                        manualStopAt = item.manualStopAt,
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
        }

        TrackDataChangeNotifier.notifyHistoryChanged()
    }

    fun nextHistoryId(context: Context): Long {
        ensureHistoryCache(context)
        synchronized(cacheLock) {
            return (historyCache.maxOfOrNull { it.id } ?: 0L) + 1L
        }
    }

    suspend fun add(context: Context, item: HistoryItem) {
        ensureHistoryCache(context)
        val normalizedItem = item.copy(points = item.points.toList())
        synchronized(cacheLock) {
            historyCache = (historyCache.filterNot { it.id == normalizedItem.id } + normalizedItem)
                .sortedWith(compareByDescending<HistoryItem> { it.timestamp }.thenByDescending { it.id })
            refreshDailyCacheLocked()
        }

        withContext(Dispatchers.IO) {
            TrackDatabase.getInstance(context).historyDao().upsertHistory(
                record = normalizedItem.toRecordEntity(),
                points = normalizedItem.toPointEntities()
            )
        }

        TrackDataChangeNotifier.notifyHistoryChanged()
    }

    suspend fun upsertProjectedItems(context: Context, projectedItems: List<HistoryItem>) {
        if (projectedItems.isEmpty()) return
        ensureHistoryCache(context)
        val normalizedItems = projectedItems.map { item ->
            item.copy(points = item.points.toList())
        }

        synchronized(cacheLock) {
            val mergedById = historyCache.associateBy { it.id }.toMutableMap()
            normalizedItems.forEach { item ->
                mergedById[item.id] = item
            }
            historyCache = mergedById.values
                .sortedWith(compareByDescending<HistoryItem> { it.timestamp }.thenByDescending { it.id })
            refreshDailyCacheLocked()
        }

        withContext(Dispatchers.IO) {
            val dao = TrackDatabase.getInstance(context).historyDao()
            normalizedItems.forEach { item ->
                dao.upsertHistory(
                    record = item.toRecordEntity(),
                    points = item.toPointEntities(),
                )
            }
        }

        TrackDataChangeNotifier.notifyHistoryChanged()
    }

    suspend fun restoreFromRemote(context: Context, remoteItems: List<HistoryItem>) {
        if (remoteItems.isEmpty()) return
        upsertProjectedItems(context, remoteItems)
    }

    suspend fun rename(context: Context, historyId: Long, newTitle: String?) {
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

        withContext(Dispatchers.IO) {
            TrackDatabase.getInstance(context).historyDao().updateTitle(historyId, newTitle)
        }

        TrackDataChangeNotifier.notifyHistoryChanged()
    }

    suspend fun delete(context: Context, historyId: Long) {
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

        withContext(Dispatchers.IO) {
            TrackDatabase.getInstance(context).historyDao().deleteHistory(historyId)
        }

        TrackDataChangeNotifier.notifyHistoryChanged()
    }

    suspend fun deleteMany(context: Context, historyIds: List<Long>) {
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

        withContext(Dispatchers.IO) {
            TrackDatabase.getInstance(context).historyDao().deleteHistoryList(historyIds)
        }

        TrackDataChangeNotifier.notifyHistoryChanged()
    }

    private fun ensureHistoryCache(context: Context) {
        if (historyCacheInitialized) return

        synchronized(cacheLock) {
            if (historyCacheInitialized) return
            while (historyCacheLoading && !historyCacheInitialized) {
                try {
                    cacheLock.wait()
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return
                }
            }
            if (historyCacheInitialized) return
            historyCacheLoading = true
        }

        val loadedHistory = try {
            ioExecutor.submit<List<HistoryItem>> {
                kotlinx.coroutines.runBlocking {
                    loadHistoryFromDisk(context)
                }
            }.get()
        } catch (throwable: Throwable) {
            synchronized(cacheLock) {
                historyCacheLoading = false
                cacheLock.notifyAll()
            }
            throw throwable
        }

        val callbacksToDispatch: List<(List<HistoryItem>) -> Unit> = synchronized(cacheLock) {
            historyCache = loadedHistory
            refreshDailyCacheLocked()
            historyCacheInitialized = true
            historyCacheLoading = false
            cacheLock.notifyAll()
            readyCallbacks.toList().also { readyCallbacks.clear() }
        }

        if (callbacksToDispatch.isNotEmpty()) {
            callbacksToDispatch.forEach { callback ->
                AppTaskExecutor.runOnMain {
                    callback(copyHistoryList(loadedHistory))
                }
            }
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
            val loadedHistory = try {
                kotlinx.coroutines.runBlocking {
                    loadHistoryFromDisk(context)
                }
            } catch (_: Throwable) {
                synchronized(cacheLock) {
                    historyCacheLoading = false
                    cacheLock.notifyAll()
                }
                return@execute
            }
            val callbacksToDispatch: List<(List<HistoryItem>) -> Unit> = synchronized(cacheLock) {
                historyCache = loadedHistory
                refreshDailyCacheLocked()
                historyCacheInitialized = true
                historyCacheLoading = false
                cacheLock.notifyAll()
                readyCallbacks.toList().also { readyCallbacks.clear() }
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

    private suspend fun loadHistoryFromDisk(context: Context): List<HistoryItem> {
        return TrackDatabase.getInstance(context)
            .historyDao()
            .getHistoryWithPoints()
            .map { it.toModel() }
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
            },
            startSource = TrackRecordSource.fromStorage(record.startSource),
            stopSource = TrackRecordSource.fromStorage(record.stopSource),
            manualStartAt = record.manualStartAt,
            manualStopAt = record.manualStopAt,
        )
    }

    private fun HistoryItem.toRecordEntity(): HistoryRecordEntity {
        return HistoryRecordEntity(
            historyId = id,
            dateKey = HistoryDayAggregator.startOfDay(timestamp),
            timestamp = timestamp,
            distanceKm = distanceKm,
            durationSeconds = durationSeconds,
            averageSpeedKmh = averageSpeedKmh,
            title = title,
            startSource = startSource.name,
            stopSource = stopSource.name,
            manualStartAt = manualStartAt,
            manualStopAt = manualStopAt,
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

    private fun copyHistoryList(items: List<HistoryItem>): MutableList<HistoryItem> {
        return items.toMutableList()
    }

    private fun copyDailyList(items: List<HistoryDayItem>): List<HistoryDayItem> {
        return items.toList()
    }

    private fun copyDailyItem(item: HistoryDayItem): HistoryDayItem {
        return item
    }
}
