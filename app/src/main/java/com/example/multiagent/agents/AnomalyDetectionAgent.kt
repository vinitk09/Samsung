package com.example.multiagent.agents

import android.content.Context
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import com.example.multiagent.AnomalyEvent
import com.example.multiagent.AnomalySeverity
/**
 * AnomalyDetectionAgent.kt
 *
 * This agent acts as the bridge between feature extraction and the ML model.
 * It subscribes to FeatureVector events, passes them to the ModelingAgent
 * for inference, and then publishes the resulting anomaly score.
 */
class AnomalyDetectionAgent(context: Context) {
    // This agent needs an instance of the ModelingAgent to perform inference.
    private val modelingAgent: ModelingAgent = ModelingAgent(context)

    init {
        // Register with the event bus to listen for feature vectors.
        EventBus.getDefault().register(this)
    }

    /**
     * This method listens for FeatureVector events that are published by
     * the FeatureExtractionAgent (or the main DataAnalysisAgent).
     */
    @Subscribe
    fun onFeatureVectorCreated(event: FeatureVectorEvent) {
        // 1. Get an anomaly score from the TFLite model using the received vector.
        val score = modelingAgent.getAnomalyScore(event.vector)

        // 2. Publish the raw score for other agents to use.
        EventBus.getDefault().post(AnomalyScoreEvent(score))
    }

    fun unregister() {
        EventBus.getDefault().unregister(this)
    }
}

/**
 * A simple data class to wrap the feature vector for event bus communication.
 */
data class FeatureVectorEvent(val vector: FloatArray)