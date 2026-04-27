package com.wenhao.record.runtimeusage

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.ArrayDeque
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object RuntimeUsageRecorder {
    private const val DIRECTORY_NAME = "runtime-usage"
    private const val FILE_NAME = "runtime-usage.json"
    private const val MAX_RECENT_EVENTS = 300

    private val lock = Any()
    private val writerExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "runtime-usage-writer").apply { isDaemon = true }
    }

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var initialized = false

    private val moduleStats = linkedMapOf<String, ModuleUsageStat>()
    private val recentEvents = ArrayDeque<RuntimeUsageEvent>()

    fun init(context: Context) {
        if (initialized) return
        synchronized(lock) {
            if (initialized) return
            appContext = context.applicationContext
            restoreFromDiskLocked()
            initialized = true
        }
    }

    fun hit(module: RuntimeUsageModule, detail: String? = null) {
        val context = appContext ?: return
        val timestampMillis = System.currentTimeMillis()
        synchronized(lock) {
            val current = moduleStats[module.key]
            moduleStats[module.key] = if (current == null) {
                ModuleUsageStat(
                    count = 1,
                    firstHitAt = timestampMillis,
                    lastHitAt = timestampMillis,
                )
            } else {
                current.copy(
                    count = current.count + 1,
                    lastHitAt = timestampMillis,
                )
            }
            recentEvents.addLast(
                RuntimeUsageEvent(
                    module = module.key,
                    hitAt = timestampMillis,
                    detail = detail?.trim()?.takeIf { it.isNotEmpty() }?.take(120),
                ),
            )
            while (recentEvents.size > MAX_RECENT_EVENTS) {
                recentEvents.removeFirst()
            }
        }
        writerExecutor.execute { writeSnapshot(context) }
    }

    private fun restoreFromDiskLocked() {
        val file = snapshotFile(appContext ?: return)
        if (!file.exists()) return
        val json = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return
        moduleStats.clear()
        recentEvents.clear()

        val modules = json.optJSONArray("modules") ?: JSONArray()
        for (index in 0 until modules.length()) {
            val item = modules.optJSONObject(index) ?: continue
            val module = item.optString("module").trim()
            if (module.isEmpty()) continue
            moduleStats[module] = ModuleUsageStat(
                count = item.optInt("count", 0).coerceAtLeast(0),
                firstHitAt = item.optLong("firstHitAt", 0L),
                lastHitAt = item.optLong("lastHitAt", 0L),
            )
        }

        val events = json.optJSONArray("recentEvents") ?: JSONArray()
        for (index in 0 until events.length()) {
            val item = events.optJSONObject(index) ?: continue
            val module = item.optString("module").trim()
            if (module.isEmpty()) continue
            recentEvents.addLast(
                RuntimeUsageEvent(
                    module = module,
                    hitAt = item.optLong("hitAt", 0L),
                    detail = item.optString("detail").trim().takeIf { it.isNotEmpty() },
                ),
            )
        }
        while (recentEvents.size > MAX_RECENT_EVENTS) {
            recentEvents.removeFirst()
        }
    }

    private fun writeSnapshot(context: Context) {
        val payload = synchronized(lock) {
            JSONObject().apply {
                put("schemaVersion", 1)
                put("updatedAt", System.currentTimeMillis())
                put(
                    "modules",
                    JSONArray().apply {
                        moduleStats.entries.sortedBy { it.key }.forEach { (module, stat) ->
                            put(
                                JSONObject().apply {
                                    put("module", module)
                                    put("count", stat.count)
                                    put("firstHitAt", stat.firstHitAt)
                                    put("lastHitAt", stat.lastHitAt)
                                },
                            )
                        }
                    },
                )
                put(
                    "recentEvents",
                    JSONArray().apply {
                        recentEvents.forEach { event ->
                            put(
                                JSONObject().apply {
                                    put("module", event.module)
                                    put("hitAt", event.hitAt)
                                    event.detail?.let { put("detail", it) }
                                },
                            )
                        }
                    },
                )
            }.toString()
        }
        val file = snapshotFile(context)
        file.parentFile?.mkdirs()
        file.writeText(payload)
    }

    private fun snapshotFile(context: Context): File {
        return File(File(context.filesDir, DIRECTORY_NAME), FILE_NAME)
    }

    private data class ModuleUsageStat(
        val count: Int,
        val firstHitAt: Long,
        val lastHitAt: Long,
    )

    private data class RuntimeUsageEvent(
        val module: String,
        val hitAt: Long,
        val detail: String?,
    )
}
