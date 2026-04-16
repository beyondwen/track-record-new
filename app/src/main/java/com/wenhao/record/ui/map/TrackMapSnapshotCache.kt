package com.wenhao.record.ui.map

import android.graphics.Bitmap
import android.util.LruCache

internal object TrackMapSnapshotCache {
    private val cache = object : LruCache<String, Bitmap>(maxSizeInKb()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun get(key: String): Bitmap? = cache.get(key)

    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }

    private fun maxSizeInKb(): Int {
        val memoryClassKb = (Runtime.getRuntime().maxMemory() / 1024L).toInt()
        return (memoryClassKb / 16).coerceAtLeast(8 * 1024)
    }
}
