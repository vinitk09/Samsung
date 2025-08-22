package com.example.multiagent.agents

import com.example.multiagent.UserProfile
import kotlin.math.log10

/**
 * FeatureExtractionAgent.kt
 *
 * This object is responsible for converting the high-level UserProfile object
 * into a flat numerical array (a "feature vector") that can be fed into the
 * TensorFlow Lite model.
 */
object FeatureExtractionAgent {

    // IMPORTANT: This must match the NUM_FEATURES in your Python training script.
    const val FEATURE_VECTOR_SIZE = 10

    fun createFeatureVector(profile: UserProfile): FloatArray {
        val vector = FloatArray(FEATURE_VECTOR_SIZE)

        // Normalize and scale each feature to be roughly between 0 and 1.
        // Using logarithmic scaling for some values helps handle outliers.
        vector[0] = normalize(profile.averageLatency, 500.0)
        vector[1] = normalize(profile.latencyStdDev, 250.0)
        vector[2] = normalize(profile.typingSpeedWPM.toDouble(), 100.0).toFloat()
        vector[3] = profile.averagePressure
        vector[4] = profile.pressureStdDev
        vector[5] = normalize(profile.averageSwipeSpeed, 25.0)
        vector[6] = normalize(profile.swipeSpeedStdDev, 15.0)
        vector[7] = normalizeLog(profile.averageMovement, 15.0)
        vector[8] = normalizeLog(profile.movementStdDev, 10.0)

        // The last feature can be a combination or a placeholder for now
        vector[9] = (vector[0] + vector[3] + vector[7]) / 3.0f

        return vector
    }

    // Simple normalization function to scale a value to a 0-1 range.
    private fun normalize(value: Double, max: Double): Float {
        return (value / max).coerceIn(0.0, 1.0).toFloat()
    }

    // Normalization using a log scale, good for values with a wide range.
    private fun normalizeLog(value: Double, max: Double): Float {
        if (value <= 0) return 0.0f
        val logValue = log10(value.coerceAtMost(max))
        val logMax = log10(max)
        return (logValue / logMax).coerceIn(0.0, 1.0).toFloat()
    }
}