package com.wenhao.record.tracking.analysis

class TrackAnalysisRunner(
    private val classifier: PointSignalClassifier = PointSignalClassifier(),
    private val candidateBuilder: SegmentCandidateBuilder = SegmentCandidateBuilder(),
    private val postProcessor: SegmentPostProcessor = SegmentPostProcessor(),
    private val stayClusterDetector: StayClusterDetector = StayClusterDetector(),
) {
    fun analyze(
        points: List<AnalyzedPoint>,
        previousContext: AnalysisContext? = null,
    ): TrackAnalysisResult {
        val workingPoints = (previousContext?.trailingPoints.orEmpty() + points)
            .sortedBy { it.timestampMillis }
            .distinctBy { Triple(it.timestampMillis, it.latitude, it.longitude) }
        if (workingPoints.isEmpty()) {
            return TrackAnalysisResult(
                scoredPoints = emptyList(),
                segments = emptyList(),
                stayClusters = emptyList(),
            )
        }

        val scoredPoints = workingPoints.mapIndexed { index, point ->
            val score = classifier.classify(workingPoints.windowAround(index = index, radius = 1))
            ScoredPoint(
                timestampMillis = point.timestampMillis,
                latitude = point.latitude,
                longitude = point.longitude,
                kind = resolveKind(score),
                staticScore = score.staticScore,
                dynamicScore = score.dynamicScore,
            )
        }
        val segments = postProcessor.refine(candidateBuilder.build(scoredPoints))
        val stayClusters = stayClusterDetector.detect(
            points = workingPoints,
            segments = segments,
        )
        return TrackAnalysisResult(
            scoredPoints = scoredPoints,
            segments = segments,
            stayClusters = stayClusters,
        )
    }

    private fun resolveKind(score: PointSignalScore): SegmentKind {
        return when {
            score.dynamicScore >= 0.7f && score.dynamicScore > score.staticScore -> SegmentKind.DYNAMIC
            score.staticScore >= 0.6f && score.staticScore >= score.dynamicScore -> SegmentKind.STATIC
            else -> SegmentKind.UNCERTAIN
        }
    }

    private fun List<AnalyzedPoint>.windowAround(index: Int, radius: Int): List<AnalyzedPoint> {
        val fromIndex = (index - radius).coerceAtLeast(0)
        val toIndex = (index + radius + 1).coerceAtMost(size)
        return subList(fromIndex, toIndex)
    }
}
