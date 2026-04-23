package com.wenhao.record.ui.main

import android.location.Location

internal object FreshLocationSelectionPolicy {
    const val GOOD_ENOUGH_ACCURACY_METERS = 25f
    const val MAX_WAIT_MILLIS = 2_500L

    fun shouldReplaceBest(currentBest: Location?, candidate: Location): Boolean {
        if (currentBest == null) return true

        val currentAccuracy = currentBest.accuracyOrMax()
        val candidateAccuracy = candidate.accuracyOrMax()
        if (candidateAccuracy + 8f < currentAccuracy) {
            return true
        }

        return candidate.time > currentBest.time + 1_500L &&
            candidateAccuracy <= currentAccuracy + 5f
    }

    fun shouldFinish(bestLocation: Location?, requestStartedAt: Long, now: Long): Boolean {
        if (bestLocation == null) return false
        return bestLocation.accuracyOrMax() <= GOOD_ENOUGH_ACCURACY_METERS ||
            now - requestStartedAt >= MAX_WAIT_MILLIS
    }

    private fun Location.accuracyOrMax(): Float {
        return if (hasAccuracy()) accuracy else Float.MAX_VALUE
    }
}
