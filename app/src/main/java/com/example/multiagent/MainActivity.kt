package com.example.multiagent

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.multiagent.ui.theme.MultiAgentTheme
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class MainActivity : ComponentActivity() {
    private lateinit var touchAgent: TouchAgent
    private lateinit var analysisAgent: DataAnalysisAgent
    private var hasOverlayPermission by mutableStateOf(false)
    private var isAccessibilityEnabled by mutableStateOf(false)
    private var touchCount by mutableStateOf(0)
    private var typingCount by mutableStateOf(0)
    private var lastAnomaly by mutableStateOf<AnomalyEvent?>(null)

    @Subscribe
    fun onTouchEvent(event: TouchEvent) {
        runOnUiThread { touchCount++ }
    }

    @Subscribe
    fun onTypingEvent(event: TypingEvent) {
        runOnUiThread { typingCount++ }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onAnomalyEvent(event: AnomalyEvent) {
        lastAnomaly = event
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        EventBus.getDefault().register(this)
        analysisAgent = DataAnalysisAgent(this)
        updatePermissionsStatus()
        if (hasOverlayPermission) {
            startTouchAgent()
        }
        setContent {
            MultiAgentTheme {
                val userProfile by analysisAgent.userProfile.collectAsState()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AgentDashboard(
                        modifier = Modifier.padding(innerPadding),
                        hasOverlayPermission = hasOverlayPermission,
                        isAccessibilityEnabled = isAccessibilityEnabled,
                        touchCount = touchCount,
                        typingCount = typingCount,
                        userProfile = userProfile,
                        lastAnomaly = lastAnomaly,
                        onRequestOverlayPermission = { requestOverlayPermission() },
                        onEnableAccessibility = {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                    )
                }
            }
        }
    }

    private fun startTouchAgent() {
        if (!::touchAgent.isInitialized) {
            touchAgent = TouchAgent(this)
            touchAgent.start()
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            packageName?.let { pkg ->
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$pkg")
                )
                startActivity(intent)
            }
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val service = "${packageName}/${MasAccessibilityService::class.java.canonicalName}"
        try {
            val settingValue = Settings.Secure.getString(
                context.applicationContext.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            return settingValue?.contains(service, ignoreCase = true) ?: false
        } catch (e: Settings.SettingNotFoundException) {
            Log.e("MainActivity", "Could not find accessibility settings.", e)
            return false
        }
    }

    private fun updatePermissionsStatus() {
        hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
        isAccessibilityEnabled = isAccessibilityServiceEnabled(this)
    }

    override fun onResume() {
        super.onResume()
        updatePermissionsStatus()
        if (hasOverlayPermission && (!::touchAgent.isInitialized || !touchAgent.isRunning())) {
            startTouchAgent()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
        analysisAgent.unregister()
        if (::touchAgent.isInitialized) {
            touchAgent.stop()
        }
    }
}

@Composable
fun AgentDashboard(
    modifier: Modifier = Modifier,
    hasOverlayPermission: Boolean,
    isAccessibilityEnabled: Boolean,
    touchCount: Int,
    typingCount: Int,
    userProfile: UserProfile,
    lastAnomaly: AnomalyEvent?,
    onRequestOverlayPermission: () -> Unit,
    onEnableAccessibility: () -> Unit
) {
    Column(
        modifier = modifier
            .padding(16.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Multi-Agent System Dashboard",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        AnomalyCard(lastAnomaly)
        UserProfileCard(userProfile)
        Spacer(Modifier.height(16.dp))
        AgentStatusCard(
            agentName = "Touch & Mouse Agent",
            isPermissionGranted = hasOverlayPermission,
            eventCount = touchCount,
            permissionText = "This agent requires the 'draw over other apps' permission to capture screen-wide events.",
            onGrantPermission = onRequestOverlayPermission,
            permissionButtonText = "Grant Overlay Permission"
        )
        Spacer(Modifier.height(16.dp))
        AgentStatusCard(
            agentName = "Typing Agent",
            isPermissionGranted = isAccessibilityEnabled,
            eventCount = typingCount,
            permissionText = "This agent requires the Accessibility Service to capture keystroke dynamics across all apps.",
            onGrantPermission = onEnableAccessibility,
            permissionButtonText = "Open Accessibility Settings"
        )
    }
}

@Composable
fun AnomalyCard(anomaly: AnomalyEvent?) {
    AnimatedVisibility(visible = anomaly != null) {
        val cardColor = when (anomaly?.severity) {
            AnomalySeverity.LOW -> Color(0xFFFFF3E0)
            AnomalySeverity.MEDIUM -> Color(0xFFFFE0B2)
            AnomalySeverity.HIGH -> Color(0xFFFFCDD2)
            else -> MaterialTheme.colorScheme.surface
        }
        val textColor = when (anomaly?.severity) {
            AnomalySeverity.HIGH -> Color(0xFFB71C1C)
            else -> Color(0xFFE65100)
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = cardColor)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "⚠️ Suspicious Activity Detected!",
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = anomaly?.reason ?: "",
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun UserProfileCard(profile: UserProfile) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Learned User Profile", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(8.dp))
                if (profile.isBaselineEstablished) Text("✅", fontSize = 20.sp) else Text("⏳", fontSize = 20.sp)
            }
            if (!profile.isBaselineEstablished) {
                Text(
                    "(Learning in progress...)",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            // --- Keyboard Metrics ---
            Text("Keyboard Biometrics", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
            ProfileRow("Avg. Typing Latency:", "%.1f ms".format(profile.averageLatency))
            ProfileRow("Typing Rhythm Consistency:", "±%.1f ms".format(profile.latencyStdDev))
            Spacer(Modifier.height(12.dp))
            // --- Touchscreen Metrics ---
            Text("Touchscreen Biometrics", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
            ProfileRow("Avg. Touch Pressure:", "%.2f".format(profile.averagePressure))
            ProfileRow("Avg. Swipe Speed:", "%.1f px/ms".format(profile.averageSwipeSpeed))
            Spacer(Modifier.height(12.dp))
            // --- Mouse Metrics ---
            Text("Mouse Biometrics", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
            ProfileRow("Avg. Click Duration:", "%.1f ms".format(profile.averageClickLatency))
            ProfileRow("Click Consistency:", "±%.1f ms".format(profile.clickLatencyStdDev))
        }
    }
}

@Composable
fun ProfileRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text(text = value, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AgentStatusCard(
    agentName: String,
    isPermissionGranted: Boolean,
    eventCount: Int,
    permissionText: String,
    onGrantPermission: () -> Unit,
    permissionButtonText: String
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(agentName, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (isPermissionGranted) {
                Text("Status: RUNNING", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Events Captured: $eventCount")
            } else {
                Text("Status: PERMISSION NEEDED", color = Color(0xFFF44336), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = permissionText,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onGrantPermission) {
                    Text(permissionButtonText)
                }
            }
        }
    }
}