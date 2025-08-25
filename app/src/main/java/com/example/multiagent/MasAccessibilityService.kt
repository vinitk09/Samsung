package com.example.multiagent

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import org.greenrobot.eventbus.EventBus

class MasAccessibilityService : AccessibilityService() {

    private val tag = "MasAccessibility"
    private var lastKeyPressTime: Long = 0

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We only care about events where text content has changed.
        if (event?.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            val currentTime = System.currentTimeMillis()

            // A latency can only be calculated if there was a previous key press.
            if (lastKeyPressTime != 0L) {
                val latency = currentTime - lastKeyPressTime
                // Post every single latency event without filtering.
                // The DataAnalysisAgent is responsible for judging the data.
                EventBus.getDefault().post(TypingEvent(latency))
                Log.d(tag, "Typing latency captured: $latency ms")
            }

            // Update the timestamp for the next event.
            lastKeyPressTime = currentTime
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("MasAccessibility", "Service connected.")
    }

    override fun onInterrupt() {
        Log.d("MasAccessibility", "Service interrupted.")
        lastKeyPressTime = 0L // Reset on interrupt
    }
}