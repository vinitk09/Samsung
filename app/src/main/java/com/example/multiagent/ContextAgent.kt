package com.example.multiagent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyManager
import android.util.Log
import org.greenrobot.eventbus.EventBus

// New data class to hold all context information
data class ContextEvent(
    val accelX: Float = 0.0f,
    val accelY: Float = 0.0f,
    val accelZ: Float = 0.0f,
    val batteryState: String = "Waiting...",
    val networkState: String = "Waiting...",
    val signalState: String = "Waiting..."
)

class ContextAgent(private val context: Context) : SensorEventListener {
    private var lastSensorUpdateTime: Long = 0


    private val tag = "ContextAgent"

    // Managers
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    // Sensor
    private var accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    // Internal state holder
    private var currentEvent = ContextEvent()

    // --- Listeners and Callbacks ---

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            val batteryString = "Battery: $level% ${if (isCharging) "(Charging)" else "(Not Charging)"}"

            if (currentEvent.batteryState != batteryString) {
                currentEvent = currentEvent.copy(batteryState = batteryString)
                postEvent()
            }
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = updateNetworkStatus()
        override fun onLost(network: Network) = updateNetworkStatus()
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = updateNetworkStatus()
    }

    private val phoneStateListener = object : PhoneStateListener() {
        @Deprecated("Deprecated in Java")
        override fun onSignalStrengthsChanged(signalStrength: SignalStrength?) {
            super.onSignalStrengthsChanged(signalStrength)
            updateNetworkStatus()
        }
    }

    // --- Public Control Methods ---

    fun start() {
        Log.d(tag, "Starting ContextAgent listeners.")
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
        updateNetworkStatus()
    }

    fun stop() {
        Log.d(tag, "Stopping ContextAgent listeners.")
        sensorManager.unregisterListener(this)
        try {
            context.unregisterReceiver(batteryReceiver)
        } catch (e: IllegalArgumentException) {
            Log.w(tag, "Battery receiver was not registered.")
        }
        connectivityManager.unregisterNetworkCallback(networkCallback)
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
    }

    // --- Event Handling ---

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val currentTime = System.currentTimeMillis()
            // Only post an update every 200 milliseconds
            if (currentTime - lastSensorUpdateTime > 200) {
                lastSensorUpdateTime = currentTime
                currentEvent = currentEvent.copy(
                    accelX = event.values[0],
                    accelY = event.values[1],
                    accelZ = event.values[2]
                )
                postEvent()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun updateNetworkStatus() {
        val network = connectivityManager.activeNetwork
        val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }

        var newNetworkState = "Network: Unknown"
        var newSignalState = "Signal: Unknown"

        when {
            capabilities == null -> {
                newNetworkState = "Network: No Connection"
                newSignalState = "Signal: N/A"
            }
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                newNetworkState = "Network: Wi-Fi"
                val rssi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    wifiManager.connectionInfo.rssi
                } else {
                    @Suppress("DEPRECATION")
                    wifiManager.connectionInfo.rssi
                }
                val level = WifiManager.calculateSignalLevel(rssi, 5)
                newSignalState = "Wi-Fi Signal: $level/5"
            }
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                newNetworkState = "Network: Mobile Data"
                val level = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    telephonyManager.signalStrength?.level ?: 0
                } else {
                    0
                }
                newSignalState = "Cellular Signal: $level/4"
            }
        }

        if (currentEvent.networkState != newNetworkState || currentEvent.signalState != newSignalState) {
            currentEvent = currentEvent.copy(networkState = newNetworkState, signalState = newSignalState)
            postEvent()
        }
    }

    private fun postEvent() {
        EventBus.getDefault().post(currentEvent)
    }
}
