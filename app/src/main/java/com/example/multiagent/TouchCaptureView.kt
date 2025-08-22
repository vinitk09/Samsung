package com.example.multiagent

import android.content.Context
import android.util.Log
import android.view.MotionEvent
import android.view.View
import org.greenrobot.eventbus.EventBus

class TouchCaptureView(context: Context) : View(context) {
    private val tag = "TouchCaptureView"

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        try {
            // RESOLVED: Capture the event source and include it in the TouchEvent
            val touchEvent = TouchEvent(
                timestamp = System.currentTimeMillis(),
                x = event.rawX,
                y = event.rawY,
                pressure = event.pressure,
                action = event.action,
                eventTime = event.eventTime,
                source = event.source
            )
            EventBus.getDefault().post(touchEvent)
        } catch (e: Exception) {
            Log.e(tag, "Error processing touch event: ${e.message}")
        }
        return false
    }
}