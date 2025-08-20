package com.example.multiagent

data class TouchEvent(
    val timestamp: Long,
    val x: Float,
    val y: Float,
    val pressure: Float,
    val action: Int,
    val eventTime: Long
)