package com.example.multiagent

import android.os.Build
import android.os.Bundle
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.multiagent.ui.theme.MultiAgentTheme
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe

class MainActivity : ComponentActivity() {
    private lateinit var touchAgent: TouchAgent
    private var hasOverlayPermission by mutableStateOf(false)
    private var touchCount by mutableStateOf(0)
    private val touchEvents = mutableStateListOf<TouchEvent>()

    // Event listener for touch events
    @Subscribe
    fun onTouchEvent(event: TouchEvent) {
        touchCount++
        // Keep only the last 50 events for display
        if (touchEvents.size >= 50) {
            touchEvents.removeAt(0)
        }
        touchEvents.add(event)

        // Log the event with more details
        val actionName = when (event.action) {
            MotionEvent.ACTION_DOWN -> "DOWN"
            MotionEvent.ACTION_UP -> "UP"
            MotionEvent.ACTION_MOVE -> "MOVE"
            MotionEvent.ACTION_SCROLL -> "SCROLL"
            else -> "OTHER(${event.action})"
        }
        Log.d("MainActivity", "Touch: $actionName at (${event.x.toInt()}, ${event.y.toInt()})")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Register for touch events with EventBus
        EventBus.getDefault().register(this)

        // Check if we have the overlay permission
        hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }

        if (hasOverlayPermission) {
            startTouchAgent()
        }

        setContent {
            MultiAgentTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        TouchAgentUI(
                            hasPermission = hasOverlayPermission,
                            touchCount = touchCount,
                            touchEvents = touchEvents,
                            onRequestPermission = { requestOverlayPermission() }
                        )
                    }
                }
            }
        }
    }

    private fun startTouchAgent() {
        touchAgent = TouchAgent(this)
        touchAgent.start()
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                hasOverlayPermission = true
                startTouchAgent()
            }
        } else {
            hasOverlayPermission = true
            startTouchAgent()
        }
    }

    override fun onResume() {
        super.onResume()
        // Check permission again when returning to the app
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val hasPermissionNow = Settings.canDrawOverlays(this)
            if (hasPermissionNow != hasOverlayPermission) {
                hasOverlayPermission = hasPermissionNow
                if (hasPermissionNow && !::touchAgent.isInitialized) {
                    startTouchAgent()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister from EventBus
        EventBus.getDefault().unregister(this)

        if (::touchAgent.isInitialized) {
            touchAgent.stop()
        }
    }
}

@Composable
fun TouchAgentUI(
    hasPermission: Boolean,
    touchCount: Int,
    touchEvents: List<TouchEvent>,
    onRequestPermission: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Multi-Agent Touch Detection",
            fontSize = 20.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (hasPermission) {
            Text(
                text = "Touch Agent: RUNNING",
                color = Color.Green,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "Touch events captured: $touchCount",
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Display recent touch events
            Text(
                text = "Recent Touch Events:",
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                touchEvents.reversed().forEach { event ->
                    TouchEventCard(event = event)
                }
            }
        } else {
            Text(
                text = "Permission needed for Touch Agent",
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Button(onClick = onRequestPermission) {
                Text(text = "Grant Overlay Permission")
            }
        }
    }
}

@Composable
fun TouchEventCard(event: TouchEvent) {
    val actionName = when (event.action) {
        MotionEvent.ACTION_DOWN -> "DOWN"
        MotionEvent.ACTION_UP -> "UP"
        MotionEvent.ACTION_MOVE -> "MOVE"
        MotionEvent.ACTION_SCROLL -> "SCROLL"
        else -> "OTHER(${event.action})"
    }

    Card(
        modifier = Modifier
            .padding(4.dp)
            .width(320.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = "Action: $actionName",
                fontWeight = FontWeight.Bold,
                color = when (event.action) {
                    MotionEvent.ACTION_DOWN -> Color.Blue
                    MotionEvent.ACTION_UP -> Color.Red
                    MotionEvent.ACTION_MOVE -> Color.Green
                    else -> Color.Gray
                }
            )
            Row {
                Text(text = "Position: ", fontWeight = FontWeight.Bold)
                Text(text = "X=${event.x.toInt()}, Y=${event.y.toInt()}")
            }
            Row {
                Text(text = "Pressure: ", fontWeight = FontWeight.Bold)
                Text(text = "%.2f".format(event.pressure))
            }
            Row {
                Text(text = "Time: ", fontWeight = FontWeight.Bold)
                Text(text = "${event.timestamp % 100000}")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MultiAgentTheme {
        TouchAgentUI(
            hasPermission = true,
            touchCount = 5,
            touchEvents = listOf(
                TouchEvent(System.currentTimeMillis(), 100f, 200f, 0.8f, MotionEvent.ACTION_DOWN, System.currentTimeMillis()),
                TouchEvent(System.currentTimeMillis(), 105f, 205f, 0.7f, MotionEvent.ACTION_MOVE, System.currentTimeMillis()),
                TouchEvent(System.currentTimeMillis(), 110f, 210f, 0.0f, MotionEvent.ACTION_UP, System.currentTimeMillis())
            ),
            onRequestPermission = {}
        )
    }
}