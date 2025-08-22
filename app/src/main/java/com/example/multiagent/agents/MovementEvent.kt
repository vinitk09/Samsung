package com.example.multiagent.agents

/**
 * MovementEvent.kt
 *
 * Represents a single snapshot of the device's motion data, combining
 * readings from the accelerometer and gyroscope.
 */
data class MovementEvent(
    val timestamp: Long,
    val accX: Float,
    val accY: Float,
    val accZ: Float,
    val gyroX: Float,
    val gyroY: Float,
    val gyroZ: Float,
//    val accelerationX: Float,
//    val accelerationY: Float,
//    val accelerationZ: Float
//    val timestamp: Long

)