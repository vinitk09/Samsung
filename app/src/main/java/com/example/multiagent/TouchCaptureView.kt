package com.example.multiagent

import android.content.Context
import android.util.Log
import android.view.MotionEvent
import android.view.View
import org.greenrobot.eventbus.EventBus

class TouchCaptureView(context: Context) : View(context) {
    private val tag = "TouchCaptureView"

    init {
        Log.d(tag, "TouchCaptureView initialized - Ready to detect all touches")
    }

    // Override dispatchTouchEvent to capture ALL touch events
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // Capture ALL touch events before they go anywhere else
        val x = event.x
        val y = event.y
        val pressure = event.pressure
        val action = event.action
        val actionName = when (action) {
            MotionEvent.ACTION_DOWN -> "DOWN"
            MotionEvent.ACTION_UP -> "UP"
            MotionEvent.ACTION_MOVE -> "MOVE"
            MotionEvent.ACTION_SCROLL -> "SCROLL"
            MotionEvent.ACTION_HOVER_MOVE -> "HOVER_MOVE"
            else -> "OTHER($action)"
        }
        val eventTime = event.eventTime

        // Create and post a TouchEvent to the EventBus
        val touchEvent = TouchEvent(
            timestamp = System.currentTimeMillis(),
            x = x,
            y = y,
            pressure = pressure,
            action = action,
            eventTime = eventTime
        )

        // Detailed logging
        Log.d(tag, "TOUCH DETECTED: Action=$actionName, X=${x.toInt()}, Y=${y.toInt()}, Pressure=${"%.2f".format(pressure)}")

        EventBus.getDefault().post(touchEvent)

        // CRITICAL: Return false to allow the event to continue to the underlying app
        return false
    }

    override fun onFilterTouchEventForSecurity(event: MotionEvent): Boolean {
        // Return false to allow normal touch processing
        return false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Log.d(tag, "onAttachedToWindow - Ready to capture all screen touches")
    }
}