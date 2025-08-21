package com.example.multiagent

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class MasAccessibilityService : AccessibilityService() {
    private lateinit var typingAgent: TypingAgent

    override fun onCreate() {
        super.onCreate()
        typingAgent = TypingAgent()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("MasAccessibility", "Service connected.")
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        event?.let {
            if (it.action == KeyEvent.ACTION_DOWN) {
                typingAgent.processKeyEvent(it.eventTime)
            }
        }
        return super.onKeyEvent(event)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let {
            if (it.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
                val source: AccessibilityNodeInfo? = it.source
                if (source != null && source.isEditable) {
                    typingAgent.reset()
                }
                source?.recycle()
            }
            typingAgent.processAccessibilityEvent(it)
        }
    }

    override fun onInterrupt() {
        Log.d("MasAccessibility", "Service interrupted.")
    }
}