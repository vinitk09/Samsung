// AnomalyEvent.kt
package com.example.multiagent

/**
 * A simple data class representing a detected anomaly.
 * It is published by the DataAnalysisAgent and received by the UI.
 *
 * @param reason A human-readable string explaining why the anomaly was triggered.
 * @param severity The level of risk associated with the anomaly.
 */
data class AnomalyEvent(val reason: String, val severity: AnomalySeverity)

enum class AnomalySeverity {
    LOW, MEDIUM, HIGH
}