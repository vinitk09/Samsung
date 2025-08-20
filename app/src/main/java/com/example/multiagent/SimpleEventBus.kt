package com.example.multiagent

// Simple event bus implementation
object SimpleEventBus {
    private val subscribers = mutableListOf<(Any) -> Unit>()

    fun register(subscriber: (Any) -> Unit) {
        subscribers.add(subscriber)
    }

    fun unregister(subscriber: (Any) -> Unit) {
        subscribers.remove(subscriber)
    }

    fun post(event: Any) {
        subscribers.forEach { it(event) }
    }
}