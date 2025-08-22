package com.example.multiagent.agents

/**
 * AppUsageEvent.kt
 *
 * Represents an app usage session event. It is published when an app
 * either enters the foreground or leaves it, and includes the duration
 * for sessions that have ended.
 */
data class AppUsageEvent(
    val packageName: String,
    val eventType: EventType,
    val durationInMillis: Long = 0 // Duration will be included when the event type is END
)

enum class EventType {
    START, END
}