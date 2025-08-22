package com.example.multiagent.agents

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.greenrobot.eventbus.EventBus

/**
 * A simple, local data class to hold the event data we care about,
 * since the original UsageEvents.Event properties are read-only.
 */
private data class EventHolder(
    val packageName: String?,
    val eventType: Int,
    val timeStamp: Long
)

class AppUsageAgent(private val context: Context) {
    private val tag = "AppUsageAgent"
    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false

    private var currentForegroundApp: String? = null
    private var sessionStartTime: Long = 0

    private val usageCheckRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            checkForAppUsage()
            handler.postDelayed(this, 2000L)
        }
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        Log.d(tag, "AppUsageAgent started.")
        handler.post(usageCheckRunnable)
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        Log.d(tag, "AppUsageAgent stopped.")
        handler.removeCallbacks(usageCheckRunnable)
    }

    private fun checkForAppUsage() {
        val time = System.currentTimeMillis()
        val usageEvents = usageStatsManager.queryEvents(time - 5000, time)
        val currentEvent = UsageEvents.Event()
        var lastRelevantEvent: EventHolder? = null // Use our new, mutable holder

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(currentEvent)
            if (currentEvent.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                currentEvent.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                // --- FIX IS HERE ---
                // Create a new instance of our EventHolder with the data.
                lastRelevantEvent = EventHolder(
                    packageName = currentEvent.packageName,
                    eventType = currentEvent.eventType,
                    timeStamp = currentEvent.timeStamp
                )
                // ---------------------
            }
        }

        lastRelevantEvent?.let {
            if (it.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                val newApp = it.packageName
                if (newApp != null && newApp != currentForegroundApp) {
                    currentForegroundApp?.let { oldApp ->
                        val duration = System.currentTimeMillis() - sessionStartTime
                        Log.i(tag, "App session ended: $oldApp, Duration: ${duration / 1000}s")
                        EventBus.getDefault().post(AppUsageEvent(oldApp, EventType.END, duration))
                    }

                    currentForegroundApp = newApp
                    sessionStartTime = System.currentTimeMillis()
                    Log.i(tag, "App session started: $newApp")
                    EventBus.getDefault().post(AppUsageEvent(newApp, EventType.START))
                }
            }
        }
    }
}