package com.wenhao.record.tracking

import android.location.Location
import com.wenhao.record.data.history.HistoryItem
import com.wenhao.record.data.tracking.FrequentPlaceAnchor
import com.wenhao.record.data.tracking.TrackPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FrequentPlaceDetector {
    private const val CLUSTER_RADIUS_METERS = 180f
    private const val MIN_CLUSTER_VISITS = 3
    private const val MIN_CLUSTER_DAYS = 2
    private const val MIN_ANCHOR_SEPARATION_METERS = 320f
    private const val MAX_ANCHORS = 4
    private const val MAX_HISTORY_ITEMS = 80

    private data class CandidatePoint(
        val latitude: Double,
        val longitude: Double,
        val dayKey: String
    )

    private data class Cluster(
        var latitude: Double,
        var longitude: Double,
        val points: MutableList<CandidatePoint> = mutableListOf()
    )

    fun buildAnchors(historyItems: List<HistoryItem>): List<FrequentPlaceAnchor> {
        val candidates = historyItems
            .asSequence()
            .take(MAX_HISTORY_ITEMS)
            .flatMap { historyItem ->
                candidatePointsFor(historyItem).asSequence()
            }
            .toList()

        if (candidates.isEmpty()) return emptyList()

        val clusters = mutableListOf<Cluster>()
        candidates.forEach { candidate ->
            val cluster = clusters.firstOrNull {
                distanceMeters(
                    it.latitude,
                    it.longitude,
                    candidate.latitude,
                    candidate.longitude
                ) <= CLUSTER_RADIUS_METERS
            }

            if (cluster == null) {
                clusters += Cluster(
                    latitude = candidate.latitude,
                    longitude = candidate.longitude,
                    points = mutableListOf(candidate)
                )
            } else {
                cluster.points += candidate
                val sampleCount = cluster.points.size
                cluster.latitude = ((cluster.latitude * (sampleCount - 1)) + candidate.latitude) / sampleCount
                cluster.longitude = ((cluster.longitude * (sampleCount - 1)) + candidate.longitude) / sampleCount
            }
        }

        val strongAnchors = clusters
            .mapNotNull { cluster ->
                val uniqueDays = cluster.points.map { it.dayKey }.distinct().size
                val visitCount = cluster.points.size
                if (visitCount < MIN_CLUSTER_VISITS || uniqueDays < MIN_CLUSTER_DAYS) {
                    null
                } else {
                    FrequentPlaceAnchor(
                        id = "stay_${cluster.latitude}_${cluster.longitude}".hashCode().toString(),
                        latitude = cluster.latitude,
                        longitude = cluster.longitude,
                        radiusMeters = CLUSTER_RADIUS_METERS,
                        visitCount = uniqueDays
                    )
                }
            }
            .sortedByDescending { it.visitCount }

        val filtered = mutableListOf<FrequentPlaceAnchor>()
        strongAnchors.forEach { anchor ->
            val isFarEnough = filtered.none {
                distanceMeters(
                    it.latitude,
                    it.longitude,
                    anchor.latitude,
                    anchor.longitude
                ) < MIN_ANCHOR_SEPARATION_METERS
            }
            if (isFarEnough && filtered.size < MAX_ANCHORS) {
                filtered += anchor
            }
        }
        return filtered
    }

    private fun candidatePointsFor(historyItem: HistoryItem): List<CandidatePoint> {
        if (historyItem.points.isEmpty()) return emptyList()
        val dayKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(historyItem.timestamp))
        return buildList {
            historyItem.points.firstOrNull()?.let { add(it.toCandidate(dayKey)) }
            historyItem.points.lastOrNull()
                ?.takeIf { historyItem.points.size > 1 }
                ?.let { add(it.toCandidate(dayKey)) }
        }
    }

    private fun TrackPoint.toCandidate(dayKey: String): CandidatePoint {
        return CandidatePoint(
            latitude = latitude,
            longitude = longitude,
            dayKey = dayKey
        )
    }

    private fun distanceMeters(
        firstLatitude: Double,
        firstLongitude: Double,
        secondLatitude: Double,
        secondLongitude: Double
    ): Float {
        val result = FloatArray(1)
        Location.distanceBetween(
            firstLatitude,
            firstLongitude,
            secondLatitude,
            secondLongitude,
            result
        )
        return result[0]
    }
}
