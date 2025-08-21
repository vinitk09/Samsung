package com.example.multiagent

import android.content.Context
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Represents the learned behavioral profile of the user.
 * Includes metrics for Keyboard, Touchscreen, and Mouse.
 */
data class UserProfile(
    // Typing Metrics
    val averageLatency: Double = 0.0,
    val latencyStdDev: Double = 0.0,
    val typingSpeedWPM: Int = 0,
    // Touchscreen Metrics
    val averagePressure: Float = 0.0f,
    val pressureStdDev: Float = 0.0f,
    val averageSwipeSpeed: Double = 0.0, // pixels per millisecond
    val swipeSpeedStdDev: Double = 0.0,
    // Mouse Metrics
    val averageClickLatency: Double = 0.0, // Time button is held down
    val clickLatencyStdDev: Double = 0.0,
    // State
    val isBaselineEstablished: Boolean = false
)

/**
 * The core agent that performs learning, detection, and reaction
 * for keyboard, touchscreen, and mouse inputs.
 */
class DataAnalysisAgent(context: Context) {
    // --- Data Storage and State ---
    private val typingStorage = TypingDataStorage(context)
    private val touchStorage = TouchDataStorage(context)
    private val recentLatencies = mutableListOf<Long>()
    private val recentPressures = mutableListOf<Float>()
    private val recentSwipeSpeeds = mutableListOf<Double>()
    private val recentClickLatencies = mutableListOf<Long>()
    private val maxDataPoints = 200
    private val minDataPointsForBaseline = 50

    // --- State for Tracking Gestures ---
    private var swipeStartEvent: TouchEvent? = null
    private var mouseDownEvent: TouchEvent? = null

    private val _userProfile = MutableStateFlow(UserProfile())
    val userProfile = _userProfile.asStateFlow()

    init {
        loadInitialData()
        recalculateProfile()
        EventBus.getDefault().register(this)
    }

    private fun loadInitialData() {
        recentLatencies.addAll(typingStorage.loadAllTypingEvents().map { it.interKeyLatency }.takeLast(maxDataPoints))
        recentPressures.addAll(touchStorage.loadAllTouchEvents().map { it.pressure }.takeLast(maxDataPoints))
    }

    @Subscribe
    fun onTypingEvent(event: TypingEvent) {
        typingStorage.saveTypingEvent(event)
        if (recentLatencies.size >= maxDataPoints) recentLatencies.removeAt(0)
        recentLatencies.add(event.interKeyLatency)
        recalculateProfile()
        if (_userProfile.value.isBaselineEstablished) {
            checkForTypingAnomalies(event)
        }
    }

    @Subscribe
    fun onTouchEvent(event: TouchEvent) {
        // Route the event to the correct handler based on its source
        when (event.source) {
            InputDevice.SOURCE_TOUCHSCREEN -> handleTouchEvent(event)
            InputDevice.SOURCE_MOUSE -> handleMouseEvent(event)
        }
    }

    // --- Logic for FINGER actions on the touchscreen ---
    private fun handleTouchEvent(event: TouchEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                swipeStartEvent = event
                if (recentPressures.size >= maxDataPoints) recentPressures.removeAt(0)
                recentPressures.add(event.pressure)
                recalculateProfile()
                if (_userProfile.value.isBaselineEstablished) {
                    checkForTouchAnomalies(event)
                }
            }
            MotionEvent.ACTION_UP -> {
                swipeStartEvent?.let { startEvent ->
                    processSwipe(startEvent, event)
                    swipeStartEvent = null
                }
            }
        }
    }

    // --- Logic for MOUSE actions ---
    private fun handleMouseEvent(event: TouchEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                mouseDownEvent = event
            }
            MotionEvent.ACTION_UP -> {
                mouseDownEvent?.let { startEvent ->
                    val clickLatency = event.eventTime - startEvent.eventTime
                    if (clickLatency > 0) {
                        if (recentClickLatencies.size >= maxDataPoints) recentClickLatencies.removeAt(0)
                        recentClickLatencies.add(clickLatency)
                        recalculateProfile()
                        if (_userProfile.value.isBaselineEstablished) {
                            checkForMouseAnomalies(clickLatency)
                        }
                    }
                    mouseDownEvent = null
                }
            }
        }
    }

    private fun processSwipe(startEvent: TouchEvent, endEvent: TouchEvent) {
        val duration = (endEvent.eventTime - startEvent.eventTime).toDouble()
        if (duration < 50) return // Ignore simple taps

        val deltaX = (endEvent.x - startEvent.x).toDouble()
        val deltaY = (endEvent.y - startEvent.y).toDouble()
        val distance = hypot(deltaX, deltaY)

        if (distance > 0 && duration > 0) {
            val speed = distance / duration
            if (recentSwipeSpeeds.size >= maxDataPoints) recentSwipeSpeeds.removeAt(0)
            recentSwipeSpeeds.add(speed)
            recalculateProfile()
            if (_userProfile.value.isBaselineEstablished) {
                checkForSwipeAnomalies(speed)
            }
        }
    }

    private fun recalculateProfile() {
        val avgLatency = recentLatencies.average()
        val latencyStdDev = calculateStdDev(recentLatencies.map { it.toDouble() }, avgLatency)
        val wpm = if (avgLatency > 0) ((60 * 1000) / (avgLatency * 5)).toInt() else 0

        val avgPressure = recentPressures.average().toFloat()
        val pressureStdDev = calculateStdDevFloat(recentPressures, avgPressure)
        val avgSwipeSpeed = recentSwipeSpeeds.average()
        val swipeSpeedStdDev = calculateStdDev(recentSwipeSpeeds, avgSwipeSpeed)

        val avgClickLatency = recentClickLatencies.average()
        val clickLatencyStdDev = calculateStdDev(recentClickLatencies.map { it.toDouble() }, avgClickLatency)

        _userProfile.value = UserProfile(
            averageLatency = avgLatency,
            latencyStdDev = latencyStdDev,
            typingSpeedWPM = wpm,
            averagePressure = avgPressure,
            pressureStdDev = pressureStdDev,
            averageSwipeSpeed = avgSwipeSpeed,
            swipeSpeedStdDev = swipeSpeedStdDev,
            averageClickLatency = avgClickLatency,
            clickLatencyStdDev = clickLatencyStdDev,
            isBaselineEstablished = recentLatencies.size >= minDataPointsForBaseline && recentPressures.size >= minDataPointsForBaseline
        )
    }

    private fun checkForTypingAnomalies(event: TypingEvent) {
        val profile = _userProfile.value
        val latency = event.interKeyLatency
        if (latency > 10 && latency < 40 && profile.latencyStdDev < 5) {
            postAnomaly("Bot-like typing detected (too consistent)", AnomalySeverity.HIGH)
            return
        }
        val deviation = abs(latency - profile.averageLatency)
        val deviationThreshold = profile.latencyStdDev * 3.5
        if (deviation > deviationThreshold && profile.latencyStdDev > 0) {
            postAnomaly("Typing rhythm is highly unusual", AnomalySeverity.MEDIUM)
        }
    }

    private fun checkForTouchAnomalies(event: TouchEvent) {
        val profile = _userProfile.value
        val pressure = event.pressure
        if (pressure == 0.0f) {
            postAnomaly("Spoofed touch detected (zero pressure)", AnomalySeverity.HIGH)
            return
        }
        val deviation = abs(pressure - profile.averagePressure)
        val deviationThreshold = profile.pressureStdDev * 4.0f
        if (deviation > deviationThreshold && profile.pressureStdDev > 0) {
            postAnomaly("Touch pressure is highly unusual", AnomalySeverity.LOW)
        }
    }

    private fun checkForSwipeAnomalies(currentSpeed: Double) {
        val profile = _userProfile.value
        if (currentSpeed > 50.0) {
            postAnomaly("Bot-like swipe detected (inhuman speed)", AnomalySeverity.HIGH)
            return
        }
        val deviation = abs(currentSpeed - profile.averageSwipeSpeed)
        val deviationThreshold = profile.swipeSpeedStdDev * 3.5
        if (deviation > deviationThreshold && profile.swipeSpeedStdDev > 0) {
            postAnomaly("Swipe speed is highly unusual", AnomalySeverity.MEDIUM)
        }
    }

    private fun checkForMouseAnomalies(clickLatency: Long) {
        val profile = _userProfile.value
        if (clickLatency < 10) {
            postAnomaly("Bot-like mouse click detected (inhuman speed)", AnomalySeverity.HIGH)
            return
        }
        val deviation = abs(clickLatency - profile.averageClickLatency)
        val deviationThreshold = profile.clickLatencyStdDev * 3.5
        if (deviation > deviationThreshold && profile.clickLatencyStdDev > 0) {
            postAnomaly("Mouse click speed is highly unusual", AnomalySeverity.MEDIUM)
        }
    }

    private fun postAnomaly(reason: String, severity: AnomalySeverity) {
        Log.e("AnomalyDetector", "REACTION TRIGGERED: $reason, Severity: $severity")
        EventBus.getDefault().post(AnomalyEvent(reason, severity))
    }

    private fun calculateStdDev(data: List<Double>, mean: Double): Double {
        return if (data.size > 1) {
            val sumOfSquares = data.sumOf { (it - mean) * (it - mean) }
            sqrt(sumOfSquares / data.size)
        } else 0.0
    }

    private fun calculateStdDevFloat(data: List<Float>, mean: Float): Float {
        return if (data.size > 1) {
            val sumOfSquares = data.sumOf { (it - mean).toDouble() * (it - mean).toDouble() }
            sqrt(sumOfSquares / data.size).toFloat()
        } else 0.0f
    }

    fun unregister() {
        EventBus.getDefault().unregister(this)
    }
}