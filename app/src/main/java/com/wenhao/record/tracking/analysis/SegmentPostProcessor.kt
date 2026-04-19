package com.wenhao.record.tracking.analysis

class SegmentPostProcessor(
    private val minDynamicDurationMillis: Long = 90_000L,
    private val minDynamicPointCount: Int = 3,
    private val maxStaticGapInsideDynamicMillis: Long = 60_000L,
    private val maxStaticGapInsideDynamicPoints: Int = 2,
) {
    fun refine(candidates: List<SegmentCandidate>): List<SegmentCandidate> {
        if (candidates.isEmpty()) return emptyList()

        val normalized = candidates.mapIndexed { index, candidate ->
            when {
                shouldRecoverDynamicNoise(candidate) -> candidate.copy(kind = SegmentKind.STATIC)
                shouldBridgeDynamicGap(index = index, candidates = candidates) ->
                    candidate.copy(kind = SegmentKind.DYNAMIC)
                candidate.kind == SegmentKind.UNCERTAIN -> recoverUncertain(index, candidates)
                else -> candidate
            }
        }

        return mergeAdjacentSameKind(normalized)
    }

    private fun shouldRecoverDynamicNoise(candidate: SegmentCandidate): Boolean {
        return candidate.kind == SegmentKind.DYNAMIC &&
            (candidate.durationMillis < minDynamicDurationMillis || candidate.pointCount < minDynamicPointCount)
    }

    private fun shouldBridgeDynamicGap(
        index: Int,
        candidates: List<SegmentCandidate>,
    ): Boolean {
        val candidate = candidates[index]
        if (candidate.kind != SegmentKind.STATIC) return false
        if (candidate.durationMillis > maxStaticGapInsideDynamicMillis) return false
        if (candidate.pointCount > maxStaticGapInsideDynamicPoints) return false
        if (index == 0 || index == candidates.lastIndex) return false

        val previous = candidates[index - 1]
        val next = candidates[index + 1]
        return previous.kind == SegmentKind.DYNAMIC && next.kind == SegmentKind.DYNAMIC
    }

    private fun recoverUncertain(
        index: Int,
        candidates: List<SegmentCandidate>,
    ): SegmentCandidate {
        val previousKind = candidates.getOrNull(index - 1)?.kind
        val nextKind = candidates.getOrNull(index + 1)?.kind
        val recoveredKind = when {
            previousKind == nextKind && previousKind != null -> previousKind
            previousKind == SegmentKind.STATIC || nextKind == SegmentKind.STATIC -> SegmentKind.STATIC
            previousKind == SegmentKind.DYNAMIC || nextKind == SegmentKind.DYNAMIC -> SegmentKind.DYNAMIC
            else -> SegmentKind.STATIC
        }
        return candidates[index].copy(kind = recoveredKind)
    }

    private fun mergeAdjacentSameKind(candidates: List<SegmentCandidate>): List<SegmentCandidate> {
        if (candidates.isEmpty()) return emptyList()

        val merged = mutableListOf(candidates.first())
        for (candidate in candidates.drop(1)) {
            val last = merged.last()
            if (last.kind == candidate.kind) {
                merged[merged.lastIndex] = last.copy(
                    endTimestamp = maxOf(last.endTimestamp, candidate.endTimestamp),
                    pointCount = last.pointCount + candidate.pointCount,
                )
            } else {
                merged += candidate
            }
        }
        return merged
    }
}
