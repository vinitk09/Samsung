package com.example.multiagent

import android.view.accessibility.AccessibilityEvent
import org.greenrobot.eventbus.EventBus

class TypingEvent(val interKeyLatency: Long)
//class TypingEvent(val interKeyLatency: Long)

class TypingAgent {
    private var lastKeyEventTimestamp: Long = 0

    fun processAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            val source = event.source
            if (source == null || !source.isEditable) {
                source?.recycle()
                return
            }
            source.recycle()
            processKeyEvent(event.eventTime)
        }
    }

    fun processKeyEvent(eventTime: Long) {
        if (lastKeyEventTimestamp != 0L) {
            val latency = eventTime - lastKeyEventTimestamp
            if (latency > 10 && latency < 2000) {
                EventBus.getDefault().post(TypingEvent(latency))
            }
        }
        lastKeyEventTimestamp = eventTime
    }

    fun reset() {
        this.lastKeyEventTimestamp = 0
    }
}