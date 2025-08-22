package com.example.multiagent

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
import com.example.multiagent.agents.AnomalyDetectionAgent
import com.example.multiagent.agents.AnomalyScoreEvent
import com.example.multiagent.agents.AppUsageAgent
import com.example.multiagent.agents.AppUsageEvent
import com.example.multiagent.agents.MovementAgent
import com.example.multiagent.agents.MovementEvent
import com.example.multiagent.ui.theme.MultiAgentTheme
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class MainActivity : ComponentActivity() {
    // Agent Declarations
    private lateinit var touchAgent: TouchAgent
    private lateinit var analysisAgent: DataAnalysisAgent
    private lateinit var appUsageAgent: AppUsageAgent
    private lateinit var movementAgent: MovementAgent
    private lateinit var anomalyDetectionAgent: AnomalyDetectionAgent

    // ANOMALY SCORE THRESHOLD (Response Logic)
    private val anomalyThreshold = 0.015f

    // Permission State
    private var hasOverlayPermission by mutableStateOf(false)
    private var isAccessibilityEnabled by mutableStateOf(false)
    private var hasUsageStatsPermission by mutableStateOf(false)

    // Event Count State
    private var touchCount by mutableStateOf(0)
    private var typingCount by mutableStateOf(0)
    private var appUsageEventCount by mutableStateOf(0)
    private var movementEventCount by mutableStateOf(0)
    private var lastAnomaly by mutableStateOf<AnomalyEvent?>(null)
    private var lastForegroundApp by mutableStateOf("No data yet")

    // EventBus Subscribers
    @Subscribe
    fun onTouchEvent(event: TouchEvent) {
        runOnUiThread { touchCount++ }
    }

    @Subscribe
    fun onTypingEvent(event: TypingEvent) {
        runOnUiThread { typingCount++ }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onAnomalyScoreEvent(event: AnomalyScoreEvent) {
        if (event.score > anomalyThreshold) {
            val reason = "ML Model Detected Anomaly (Score: %.4f)".format(event.score)
            lastAnomaly = AnomalyEvent(reason, AnomalySeverity.HIGH)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onAppUsageEvent(event: AppUsageEvent) {
        appUsageEventCount++
        lastForegroundApp = event.packageName.substringAfterLast('.')
    }

    @Subscribe
    fun onMovementEvent(event: MovementEvent) {
        runOnUiThread { movementEventCount++ }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        EventBus.getDefault().register(this)
        // Instantiate all agents
        analysisAgent = DataAnalysisAgent(this)
        appUsageAgent = AppUsageAgent(this)
        movementAgent = MovementAgent(this)
        anomalyDetectionAgent = AnomalyDetectionAgent(this)

        updatePermissionsStatus()

        setContent {
            MultiAgentTheme {
                val userProfile by analysisAgent.userProfile.collectAsState()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AgentDashboard(
                        modifier = Modifier.padding(innerPadding),
                        hasOverlayPermission = hasOverlayPermission,
                        isAccessibilityEnabled = isAccessibilityEnabled,
                        hasUsagePermission = hasUsageStatsPermission,
                        touchCount = touchCount,
                        typingCount = typingCount,
                        appUsageCount = appUsageEventCount,
                        movementCount = movementEventCount,
                        lastForegroundApp = lastForegroundApp,
                        userProfile = userProfile,
                        lastAnomaly = lastAnomaly,
                        onRequestOverlayPermission = { requestOverlayPermission() },
                        onEnableAccessibility = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                        onRequestUsagePermission = { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                    )
                }
            }
        }
    }

    private fun startAllAgents() {
        if (hasOverlayPermission) {
            if (!::touchAgent.isInitialized) {
                touchAgent = TouchAgent(this)
            }
            touchAgent.start()
        }
        if (hasUsageStatsPermission) {
            appUsageAgent.start()
        }
        movementAgent.start()
    }

    private fun stopAllAgents() {
        if (::touchAgent.isInitialized) touchAgent.stop()
        if (::appUsageAgent.isInitialized) appUsageAgent.stop()
        if (::movementAgent.isInitialized) movementAgent.stop()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionsStatus()
        startAllAgents()
    }

    override fun onPause() {
        super.onPause()
        stopAllAgents()
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
        analysisAgent.unregister()
        anomalyDetectionAgent.unregister()
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = "${packageName}/${MasAccessibilityService::class.java.canonicalName}"
        try {
            val settingValue = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            return settingValue?.contains(service, ignoreCase = true) ?: false
        } catch (e: Settings.SettingNotFoundException) {
            return false
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        } else {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun updatePermissionsStatus() {
        hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true
        isAccessibilityEnabled = isAccessibilityServiceEnabled()
        hasUsageStatsPermission = hasUsageStatsPermission()
    }
}

@Composable
fun AgentDashboard(
    modifier: Modifier = Modifier,
    hasOverlayPermission: Boolean,
    isAccessibilityEnabled: Boolean,
    hasUsagePermission: Boolean,
    touchCount: Int,
    typingCount: Int,
    appUsageCount: Int,
    movementCount: Int,
    lastForegroundApp: String,
    userProfile: UserProfile,
    lastAnomaly: AnomalyEvent?,
    onRequestOverlayPermission: () -> Unit,
    onEnableAccessibility: () -> Unit,
    onRequestUsagePermission: () -> Unit
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
            agentName = "Movement Agent",
            isPermissionGranted = true,
            eventCount = movementCount,
            permissionText = "This agent uses device sensors to detect physical movement and orientation.",
            onGrantPermission = {}
        )
        Spacer(Modifier.height(16.dp))
        AgentStatusCard(
            agentName = "App Usage Agent",
            isPermissionGranted = hasUsagePermission,
            eventCount = appUsageCount,
            eventDetail = "In Foreground: $lastForegroundApp",
            permissionText = "This agent requires 'Usage Access' permission to identify which app is currently in use.",
            onGrantPermission = onRequestUsagePermission,
            permissionButtonText = "Grant Usage Permission"
        )
        Spacer(Modifier.height(16.dp))
        AgentStatusCard(
            agentName = "Touch Agent",
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
            Text("Keyboard Biometrics", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
            ProfileRow("Avg. Typing Latency:", "%.1f ms".format(profile.averageLatency))
            ProfileRow("Typing Rhythm Consistency:", "±%.1f ms".format(profile.latencyStdDev))
            Spacer(Modifier.height(12.dp))
            Text("Touchscreen Biometrics", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
            ProfileRow("Avg. Touch Pressure:", "%.2f".format(profile.averagePressure))
            ProfileRow("Avg. Swipe Speed:", "%.1f px/ms".format(profile.averageSwipeSpeed))
            Spacer(Modifier.height(12.dp))
            Text("Movement Biometrics", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
            ProfileRow("Avg. Device Movement:", "%.2f".format(profile.averageMovement))
            ProfileRow("Movement Consistency:", "±%.2f".format(profile.movementStdDev))
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
    permissionButtonText: String? = null,
    eventDetail: String? = null
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
                if (eventDetail != null) {
                    Text(
                        text = eventDetail,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            } else {
                Text("Status: PERMISSION NEEDED", color = Color(0xFFF44336), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = permissionText,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                if (permissionButtonText != null) {
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onGrantPermission) {
                        Text(permissionButtonText)
                    }
                }
            }
        }
    }
}