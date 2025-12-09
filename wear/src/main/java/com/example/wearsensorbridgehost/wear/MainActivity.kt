package com.example.wearsensorbridgehost.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.rememberScalingLazyListState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearApp(
                onStartService = {
                    startForegroundService(Intent(this, SensorService::class.java))
                },
                onStopService = {
                    stopService(Intent(this, SensorService::class.java))
                }
            )
        }
    }
}

@Composable
fun WearApp(onStartService: () -> Unit, onStopService: () -> Unit) {
    var lastMessage by remember { mutableStateOf("No messages yet") }
    var isServiceRunning by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val listState = rememberScalingLazyListState()

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.getStringExtra("message")?.let {
                    lastMessage = it
                }
            }
        }
        val filter = IntentFilter("com.example.wearsensorbridgehost.MESSAGE_RECEIVED")
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    MaterialTheme {
        Scaffold(
            timeText = { TimeText() },
            positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
        ) {
            ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState
            ) {
                item {
                    ListHeader {
                        Text(text = "Sensor Bridge", textAlign = TextAlign.Center)
                    }
                }
                
                item {
                    Text(
                        text = "Last Msg: $lastMessage",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.caption1
                    )
                }

                item {
                    Chip(
                        label = { Text("Start Service") },
                        onClick = {
                            onStartService()
                            isServiceRunning = true
                        },
                        secondaryLabel = { if (isServiceRunning) Text("Running") else null },
                        colors = ChipDefaults.primaryChipColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Chip(
                        label = { Text("Stop Service") },
                        onClick = {
                            onStopService()
                            isServiceRunning = false
                        },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
