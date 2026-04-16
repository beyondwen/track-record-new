package com.wenhao.record.data.tracking

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

data class FrequentPlaceAnchor(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val visitCount: Int
)

object FrequentPlaceStorage {
    private const val PREFS_NAME = "track_record_places"
    private const val KEY_ANCHORS = "anchors"
    private const val KEY_INSIDE_ANCHOR_IDS = "inside_anchor_ids"

    fun loadAnchors(context: Context): List<FrequentPlaceAnchor> {
        val raw = prefs(context).getString(KEY_ANCHORS, null) ?: return emptyList()
        val array = JSONArray(raw)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    FrequentPlaceAnchor(
                        id = item.getString("id"),
                        latitude = item.getDouble("latitude"),
                        longitude = item.getDouble("longitude"),
                        radiusMeters = item.optDouble("radiusMeters", 180.0).toFloat(),
                        visitCount = item.optInt("visitCount", 0)
                    )
                )
            }
        }
    }

    fun saveAnchors(context: Context, anchors: List<FrequentPlaceAnchor>) {
        val array = JSONArray()
        anchors.forEach { anchor ->
            array.put(
                JSONObject().apply {
                    put("id", anchor.id)
                    put("latitude", anchor.latitude)
                    put("longitude", anchor.longitude)
                    put("radiusMeters", anchor.radiusMeters)
                    put("visitCount", anchor.visitCount)
                }
            )
        }
        prefs(context).edit {
            putString(KEY_ANCHORS, array.toString())
        }
    }

    fun loadInsideAnchorIds(context: Context): Set<String> {
        val raw = prefs(context).getString(KEY_INSIDE_ANCHOR_IDS, null) ?: return emptySet()
        val array = JSONArray(raw)
        return buildSet {
            for (index in 0 until array.length()) {
                add(array.getString(index))
            }
        }
    }

    fun saveInsideAnchorIds(context: Context, ids: Set<String>) {
        val array = JSONArray()
        ids.forEach(array::put)
        prefs(context).edit {
            putString(KEY_INSIDE_ANCHOR_IDS, array.toString())
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
