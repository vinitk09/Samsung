package com.example.multiagent

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.WindowManager

class TouchAgent(private val context: Context) {
    private val tag = "TouchAgent"
    private val windowManager: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var touchCaptureView: TouchCaptureView? = null
    private var isRunning = false

    fun start() {
        if (isRunning) return
        try {
            touchCaptureView = TouchCaptureView(context)

            // ---------- FIX IS HERE ----------
            // We change the overlay from a full-screen view to a tiny 1x1 pixel view.
            // The FLAG_WATCH_OUTSIDE_TOUCH flag ensures this tiny view still receives
            // all touch events from across the entire screen without blocking them.
            val params = WindowManager.LayoutParams(
                1, // WIDTH = 1 PIXEL
                1, // HEIGHT = 1 PIXEL
                getOverlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            )
            // ---------------------------------

            params.gravity = Gravity.TOP or Gravity.START
            windowManager.addView(touchCaptureView, params)
            isRunning = true
            Log.d(tag, "TouchAgent started successfully.")
        } catch (e: Exception) {
            Log.e(tag, "Failed to start TouchAgent: ${e.message}")
        }
    }

    fun stop() {
        if (!isRunning) return
        try {
            touchCaptureView?.let { windowManager.removeView(it) }
            touchCaptureView = null
            isRunning = false
            Log.d(tag, "TouchAgent stopped successfully.")
        } catch (e: Exception) {
            Log.e(tag, "Failed to stop TouchAgent: ${e.message}")
        }
    }

    fun isRunning(): Boolean = isRunning

    private fun getOverlayType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }
}