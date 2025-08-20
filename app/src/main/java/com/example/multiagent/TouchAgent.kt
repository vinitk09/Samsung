package com.example.multiagent

import android.content.Context
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.graphics.PixelFormat
import android.os.Build
import org.greenrobot.eventbus.EventBus

class TouchAgent(private val context: Context) {
    private val tag = "TouchAgent"
    private val windowManager: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var touchCaptureView: TouchCaptureView? = null
    private var isRunning = false

    fun start() {
        Log.d(tag, "Attempting to start TouchAgent")
        if (isRunning) {
            Log.w(tag, "TouchAgent is already running")
            return
        }

        try {
            Log.d(tag, "Creating TouchCaptureView")
            // Create the touch capture view
            touchCaptureView = TouchCaptureView(context)

            // Set up layout parameters for the overlay - CORRECT FLAGS
            val params = WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                getOverlayType(),
                // THESE ARE THE CRITICAL FLAGS FOR TOUCH PASS-THROUGH:
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            )

            // Make the view completely transparent
            params.alpha = 0.0f
            params.gravity = Gravity.TOP or Gravity.START

            Log.d(tag, "Adding view to WindowManager")
            // Add the view to the window manager
            windowManager.addView(touchCaptureView, params)

            isRunning = true
            Log.d(tag, "TouchAgent started successfully - WILL DETECT ALL TOUCHES")
        } catch (e: Exception) {
            Log.e(tag, "Failed to start TouchAgent: ${e.message}")
            e.printStackTrace()
        }
    }

    fun stop() {
        Log.d(tag, "Attempting to stop TouchAgent")
        if (!isRunning) {
            Log.w(tag, "TouchAgent is not running")
            return
        }

        try {
            Log.d(tag, "Removing view from WindowManager")
            touchCaptureView?.let {
                windowManager.removeView(it)
            }
            touchCaptureView = null

            isRunning = false
            Log.d(tag, "TouchAgent stopped successfully")
        } catch (e: Exception) {
            Log.e(tag, "Failed to stop TouchAgent: ${e.message}")
            e.printStackTrace()
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