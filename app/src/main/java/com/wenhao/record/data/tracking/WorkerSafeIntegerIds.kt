package com.wenhao.record.data.tracking

object WorkerSafeIntegerIds {
    const val MAX_SAFE_INTEGER: Long = 9_007_199_254_740_991L

    fun toPositiveSafeInteger(value: Long): Long {
        val normalized = Math.floorMod(value, MAX_SAFE_INTEGER)
        return if (normalized == 0L) 1L else normalized
    }

    fun stableStayId(
        segmentId: Long,
        arrivalTime: Long,
        departureTime: Long,
    ): Long {
        val raw = (segmentId * 1_000_003L) xor arrivalTime xor departureTime
        return toPositiveSafeInteger(raw)
    }
}
