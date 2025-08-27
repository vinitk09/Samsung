package com.example.multiagent

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import androidx.core.app.NotificationCompat
import com.example.multiagent.agents.AppUsageEvent
import com.example.multiagent.agents.EventType
import com.example.multiagent.agents.MovementEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sqrt

// UserProfile data class is unchanged
data class UserProfile(
    val averageLatency: Double = 0.0,
    val latencyStdDev: Double = 0.0,
    val typingSpeedWPM: Int = 0,
    val averagePressure: Float = 0.0f,
    val pressureStdDev: Float = 0.0f,
    val averageSwipeSpeed: Double = 0.0,
    val swipeSpeedStdDev: Double = 0.0,
    val averageMovement: Double = 0.0,
    val movementStdDev: Double = 0.0,
    val isBaselineEstablished: Boolean = false
)

class DataAnalysisAgent(context: Context) {
    // --- Data Storage and State ---
    private val typingStorage = TypingDataStorage(context)
    private val touchStorage = TouchDataStorage(context)
    private val recentLatencies = mutableListOf<Long>()
    private val recentPressures = mutableListOf<Float>()
    private val recentSwipeSpeeds = mutableListOf<Double>()
    private val recentMovements = mutableListOf<Double>()
    private val maxDataPoints = 200
    private val minDataPointsForBaseline = 75

    private val shortTermLatencies = mutableListOf<Long>()
    private val shortTermBufferSize = 10

    private var swipeStartEvent: TouchEvent? = null
    private val _userProfile = MutableStateFlow(UserProfile())
    val userProfile = _userProfile.asStateFlow()

    // --- ADD NOTIFICATION CONSTANTS AND CONTEXT ---
    companion object {
        const val NOTIFICATION_CHANNEL_ID = "anomaly_alerts_channel"
        const val NOTIFICATION_ID = 1
    }

    private val appContext: Context = context.applicationContext
    private var notificationIdCounter = 1000 // Start from 1000 to avoid conflicts

    init {
        loadInitialData()
        recalculateProfile()
        EventBus.getDefault().register(this)
        createNotificationChannel() // Create channel on initialization
    }

    private fun loadInitialData() {
        recentLatencies.addAll(typingStorage.loadAllTypingEvents().map { it.interKeyLatency }.takeLast(maxDataPoints))
        recentPressures.addAll(touchStorage.loadAllTouchEvents().map { it.pressure }.takeLast(maxDataPoints))
    }

    @Subscribe
    fun onTypingEvent(event: TypingEvent) {
        if (recentLatencies.size >= maxDataPoints) recentLatencies.removeAt(0)
        recentLatencies.add(event.interKeyLatency)

        if (shortTermLatencies.size >= shortTermBufferSize) shortTermLatencies.removeAt(0)
        shortTermLatencies.add(event.interKeyLatency)

        recalculateProfile()
        if (_userProfile.value.isBaselineEstablished) {
            checkForTypingAnomalies(event)
        }
    }

    @Subscribe
    fun onTouchEvent(event: TouchEvent) {
        if (event.source == InputDevice.SOURCE_TOUCHSCREEN) {
            handleTouchEvent(event)
        }
    }

    @Subscribe
    fun onMovementEvent(event: MovementEvent) {
        val magnitude = sqrt(event.accX.toDouble().pow(2) + event.accY.toDouble().pow(2) + event.accZ.toDouble().pow(2))
        if (magnitude > 10.5 || magnitude < 9.0) {
            if (recentMovements.size >= maxDataPoints) recentMovements.removeAt(0)
            recentMovements.add(magnitude)
            recalculateProfile()
            if (_userProfile.value.isBaselineEstablished) {
                checkForMovementAnomalies(magnitude)
            }
        }
    }

    @Subscribe
    fun onAppUsageEvent(event: AppUsageEvent) {
        // Check if the app is a financial app
        val isFinancialApp = event.packageName.contains("bank", ignoreCase = true) ||
                event.packageName.contains("chase", ignoreCase = true) ||
                event.packageName.contains("paypal", ignoreCase = true) ||
                event.packageName.contains("wellsfargo", ignoreCase = true) ||
                event.packageName.contains("investment", ignoreCase = true) ||
                event.packageName.contains("crypto", ignoreCase = true) ||
                event.packageName.contains("capitalone", ignoreCase = true) ||
                event.packageName.contains("venmo", ignoreCase = true) ||
                event.packageName.contains("zelle", ignoreCase = true)

        if (isFinancialApp && event.eventType == EventType.START) {
            val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            // Define "unusual hours" as between 1 AM and 5 AM
            if (currentHour in 1..5) {
                val reason = "Suspicious: Accessing financial app (${
                    event.packageName.substringAfterLast('.')
                }) at unusual hour ($currentHour:00)"
                postAnomaly(reason, AnomalySeverity.HIGH)
            }
        }
    }

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

    private fun processSwipe(startEvent: TouchEvent, endEvent: TouchEvent) {
        val duration = (endEvent.eventTime - startEvent.eventTime).toDouble()
        if (duration < 50) return
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
        val avgMovement = recentMovements.average()
        val movementStdDev = calculateStdDev(recentMovements, avgMovement)

        val typingBaselineMet = recentLatencies.size >= minDataPointsForBaseline
        val touchBaselineMet = recentPressures.size >= minDataPointsForBaseline
        val movementBaselineMet = recentMovements.size >= minDataPointsForBaseline
        val conditionsMet = listOf(typingBaselineMet, touchBaselineMet, movementBaselineMet).count { it }
        val isEstablished = conditionsMet >= 2

        _userProfile.value = UserProfile(
            averageLatency = avgLatency,
            latencyStdDev = latencyStdDev,
            typingSpeedWPM = wpm,
            averagePressure = avgPressure,
            pressureStdDev = pressureStdDev,
            averageSwipeSpeed = avgSwipeSpeed,
            swipeSpeedStdDev = swipeSpeedStdDev,
            averageMovement = avgMovement,
            movementStdDev = movementStdDev,
            isBaselineEstablished = isEstablished
        )
    }

    private fun checkForTypingAnomalies(event: TypingEvent) {
        val profile = _userProfile.value
        val latency = event.interKeyLatency

        if (latency < 40) {
            postAnomaly("Bot-like typing detected (inhuman programmatic speed)", AnomalySeverity.HIGH)
            return
        }

        if (shortTermLatencies.size == shortTermBufferSize) {
            val shortTermAverage = shortTermLatencies.average()
            val shortTermStdDev = calculateStdDev(shortTermLatencies.map { it.toDouble() }, shortTermAverage)
            val shortTermWPM = if (shortTermAverage > 0) ((60 * 1000) / (shortTermAverage * 5)).toInt() else 0
            val speedDifference = abs(shortTermWPM - profile.typingSpeedWPM)

            if (profile.typingSpeedWPM > 0 && speedDifference > profile.typingSpeedWPM * 0.5) {
                postAnomaly("Typing speed is significantly different from user profile", AnomalySeverity.MEDIUM)
                shortTermLatencies.clear()
                return
            }

            if (shortTermAverage < profile.averageLatency * 0.7 && shortTermStdDev < profile.latencyStdDev * 0.7) {
                postAnomaly("Unusual burst of rhythmic typing detected", AnomalySeverity.MEDIUM)
                shortTermLatencies.clear()
                return
            }
        }

        val deviation = abs(latency - profile.averageLatency)
        val deviationThreshold = profile.latencyStdDev * 4.0

        // --- NEW RULE ADDED HERE ---
        // This rule specifically checks for very slow key presses.
        val absoluteSlowThreshold = profile.averageLatency + 700 // e.g., 700ms slower than average

        if (deviation > deviationThreshold && profile.latencyStdDev > 0) {
            postAnomaly("Typing rhythm is highly unusual", AnomalySeverity.MEDIUM)
        } else if (latency > absoluteSlowThreshold && profile.averageLatency > 0) {
            postAnomaly("Unusually long pause detected between keys", AnomalySeverity.LOW)
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
        val deviationThreshold = profile.pressureStdDev * 4.5f
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

    private fun checkForMovementAnomalies(currentMagnitude: Double) {
        val profile = _userProfile.value
        val deviation = abs(currentMagnitude - profile.averageMovement)
        val deviationThreshold = profile.movementStdDev * 5.0
        if (deviation > deviationThreshold && profile.movementStdDev > 0) {
            postAnomaly("Sudden, high-intensity device movement detected", AnomalySeverity.MEDIUM)
        }
    }

    // --- MODIFIED POSTANOMALY METHOD WITH NOTIFICATIONS FOR ALL ANOMALIES ---
    private fun postAnomaly(reason: String, severity: AnomalySeverity) {
        Log.e("AnomalyDetector", "REACTION TRIGGERED: $reason, Severity: $severity")
        EventBus.getDefault().post(AnomalyEvent(reason, severity))

        // Send push notification for ALL types of anomalies
        sendNotification(reason, severity)
    }

    // --- NEW NOTIFICATION METHODS ---
    private fun sendNotification(reason: String, severity: AnomalySeverity) {
        // Ensure notification channel exists
        if (!isNotificationChannelCreated()) {
            Log.d("NotificationDebug", "Creating notification channel")

            createNotificationChannel()
        }

        // Use a unique ID for each notification so they don't overwrite each other
        val uniqueNotificationId = notificationIdCounter++
        Log.d("NotificationDebug", "Using notification ID: $uniqueNotificationId")

        // Intent to open the app when notification is tapped
        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Choose appropriate icon and color based on severity
        val (icon, color) = when (severity) {
            AnomalySeverity.HIGH -> Pair(android.R.drawable.ic_dialog_alert, android.R.color.holo_red_light)
            AnomalySeverity.MEDIUM -> Pair(android.R.drawable.ic_dialog_info, android.R.color.holo_orange_light)
            AnomalySeverity.LOW -> Pair(android.R.drawable.ic_dialog_info, android.R.color.holo_blue_light)
        }

        // Build the notification
        // Build the notification
        val notification = NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(icon)
            .setColor(appContext.resources.getColor(color))
            .setContentTitle("⚠️ Suspicious Activity Detected")
            .setContentText(reason)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false) // Changed to false to always alert
            .setCategory(NotificationCompat.CATEGORY_ALARM) // Important category
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Show on lock screen
            .build()

        // Send the notification
        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(uniqueNotificationId, notification)
    }

    private fun isNotificationChannelCreated(): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            return notificationManager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) != null
        }
        return true // For pre-Oreo, channels don't exist
    }
    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val name = "Anomaly Alerts"
            val descriptionText = "Notifications for suspicious activity detection"
            val importance = NotificationManager.IMPORTANCE_HIGH // Makes it a heads-up notification
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                setShowBadge(true)
            }
            val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
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