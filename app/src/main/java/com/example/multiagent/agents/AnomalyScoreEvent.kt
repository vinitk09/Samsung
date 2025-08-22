package com.example.multiagent.agents

/**
 * AnomalyScoreEvent.kt
 *
 * Published by the AnomalyDetectionAgent, this event carries the
 * raw anomaly score calculated by the machine learning model.
 */
data class AnomalyScoreEvent(val score: Float)