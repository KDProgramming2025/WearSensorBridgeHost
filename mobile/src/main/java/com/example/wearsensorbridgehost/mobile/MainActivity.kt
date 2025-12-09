package com.example.wearsensorbridgehost.mobile

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MobileApp()
                }
            }
        }
    }
}

@Composable
fun MobileApp() {
    val context = LocalContext.current
    // Use remember to keep the manager alive across recompositions
    val scannerManager = remember { BleScannerManager(context) }
    
    // UI State
    var statusText by remember { mutableStateOf("Ready to Scan") }
    var dataText by remember { mutableStateOf("No Data") }
    var isScanningOrConnected by remember { mutableStateOf(false) }

    // Setup callbacks once
    LaunchedEffect(scannerManager) {
        scannerManager.onStatusChanged = { status ->
            statusText = status
            isScanningOrConnected = status.contains("Scanning") || 
                                  status.contains("Connected") || 
                                  status.contains("Mock Mode")
        }
        scannerManager.onDataReceived = { data ->
            dataText = data
        }
    }

    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            scannerManager.startScanning()
        } else {
            statusText = "Bluetooth not enabled"
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = bluetoothManager.adapter
            if (adapter?.isEnabled == true) {
                scannerManager.startScanning()
            } else {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                enableBluetoothLauncher.launch(enableBtIntent)
            }
        } else {
            statusText = "Permissions missing"
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Mobile Host App", style = MaterialTheme.typography.headlineMedium)
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(text = "Status: $statusText", style = MaterialTheme.typography.bodyLarge)
        Text(text = "Data: $dataText", style = MaterialTheme.typography.bodyMedium)
        
        Spacer(modifier = Modifier.height(32.dp))

        Button(onClick = {
            if (isScanningOrConnected) {
                scannerManager.stopScanning()
            } else {
                permissionLauncher.launch(permissionsToRequest)
            }
        }) {
            Text(if (isScanningOrConnected) "Disconnect" else "Start Scanning")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(onClick = {
            val workRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueue(workRequest)
            statusText = "Background Sync Scheduled"
        }) {
            Text("Schedule Background Sync")
        }
    }
}
