// TouchEvent.kt
package com.example.multiagent

data class TouchEvent(
    val timestamp: Long,
    val x: Float,
    val y: Float,
    val pressure: Float,
    val action: Int,
    val eventTime: Long,
    val source: Int // NEW: To differentiate between touchscreen, mouse, etc.
)