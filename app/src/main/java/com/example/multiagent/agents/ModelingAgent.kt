package com.example.multiagent.agents

import android.content.Context
import com.example.multiagent.TFLiteModelLoader
import org.tensorflow.lite.Interpreter

/**
 * ModelingAgent.kt
 *
 * This class handles all interactions with the on-device TensorFlow Lite model.
 * It loads the model and provides a method to perform inference.
 */
class ModelingAgent(context: Context) {

    private val interpreter: Interpreter

    init {
        // Use the TFLiteModelLoader to create the interpreter
        interpreter = TFLiteModelLoader.createInterpreter(context, "model.tflite")
    }

    /**
     * Takes a live feature vector, runs it through the autoencoder model,
     * and calculates the reconstruction error, which is our anomaly score.
     */
    fun getAnomalyScore(featureVector: FloatArray): Float {
        // The model expects a 2D array as input, so we wrap our vector
        val input = arrayOf(featureVector)

        // The model will output a 2D array, so we prepare a placeholder
        val output = Array(1) { FloatArray(FeatureExtractionAgent.FEATURE_VECTOR_SIZE) }

        // Run inference
        interpreter.run(input, output)

        // The anomaly score is the difference (Mean Squared Error) between
        // what we put in and what the model reconstructed.
        return calculateReconstructionError(input[0], output[0])
    }

    private fun calculateReconstructionError(original: FloatArray, reconstructed: FloatArray): Float {
        var error = 0.0f
        for (i in original.indices) {
            val diff = original[i] - reconstructed[i]
            error += diff * diff
        }
        return error / original.size
    }
}