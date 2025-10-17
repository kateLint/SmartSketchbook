package com.example.smartsketchbook.domain.ml

data class SketchDataBatch(
    val label: String,
    val pathPoints: List<Pair<Float, Float>>
)

/**
 * Conceptual data client for Federated Learning.
 * In a real FL integration, this would prepare anonymized local examples
 * meeting quality thresholds before handing them to the FL SDK.
 */
class SketchbookDataClient(
    private val maxExamplesPerRound: Int = 64
) {
    // In-memory queue of locally collected, user-consented sketches.
    private val pendingExamples: MutableList<SketchDataBatch> = mutableListOf()

    // Add a labeled sketch example (points in raw canvas coordinates)
    fun enqueue(label: String, points: List<Pair<Float, Float>>) {
        if (points.isEmpty()) return
        val normalized = normalizePoints(points)
        pendingExamples += SketchDataBatch(label = label, pathPoints = normalized)
    }

    // Return up to N examples prepared for an FL round.
    // In real FL, apply anonymization, quality filters, and min-count thresholds.
    fun getTrainingBatches(): List<SketchDataBatch> {
        return pendingExamples.take(maxExamplesPerRound).toList()
    }

    // Remove the first 'count' examples after successful submission
    fun clearConsumed(count: Int) {
        if (count <= 0) return
        val n = kotlin.math.min(count, pendingExamples.size)
        repeat(n) { pendingExamples.removeAt(0) }
    }

    fun size(): Int = pendingExamples.size

    private fun normalizePoints(points: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
        if (points.isEmpty()) return points
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for ((x, y) in points) {
            if (x < minX) minX = x
            if (y < minY) minY = y
            if (x > maxX) maxX = x
            if (y > maxY) maxY = y
        }
        val w = (maxX - minX).coerceAtLeast(1f)
        val h = (maxY - minY).coerceAtLeast(1f)
        // Normalize to [0,1] box to avoid leaking absolute canvas size
        return points.map { (x, y) ->
            val nx = (x - minX) / w
            val ny = (y - minY) / h
            nx to ny
        }
    }
}


