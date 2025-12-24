package com.example.wearsensorbridgehost.mobile

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Base64
import android.util.Log
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.UUID

@SuppressLint("MissingPermission")
class BleScannerManager(private val context: Context) {

    private var bluetoothManager: BluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private var bluetoothAdapter: BluetoothAdapter = bluetoothManager.adapter
    private var bluetoothGatt: BluetoothGatt? = null
    private val mqttManager = MqttManager()
    private var isMockMode = false
    private val handler = Handler(Looper.getMainLooper())
    
    var onBleStatusChanged: ((String) -> Unit)? = null
    var onMqttStatusChanged: ((String) -> Unit)? = null
    var onDataReceived: ((String) -> Unit)? = null

    init {
        // Connect to MQTT in a background thread
        mqttManager.onConnectionStatusChanged = { _, statusMessage ->
            handler.post { onMqttStatusChanged?.invoke(statusMessage) }
        }
        mqttManager.onMessageReceived = { message ->
            handler.post { onDataReceived?.invoke("Broadcast: $message") }
            sendToWatch(message)
        }
        Thread {
            handler.post { onMqttStatusChanged?.invoke("Connecting to MQTT...") }
            mqttManager.connect()
        }.start()
    }

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb")
        val CHARACTERISTIC_UUID: UUID = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb")
        val MESSAGE_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002A38-0000-1000-8000-00805f9b34fb")
    }

    fun startScanning() {
        val scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            Log.e("BleScanner", "BluetoothLeScanner is null. Bluetooth disabled or permissions missing?")
            onStatusChanged?.invoke("Error: Bluetooth disabled or no permission")
            return
        }
        
        onBleStatusChanged?.invoke("Scanning for watch...")
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()
            
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(listOf(filter), settings, scanCallback)
        
        // Timeout after 10 seconds
        handler.postDelayed({
            if (bluetoothGatt == null && !isMockMode) {
                Log.d("BleScanner", "Scan timeout. Starting Mock Mode.")
                onBleStatusChanged?.invoke("Scan timeout; switching to mock mode")
                scanner.stopScan(scanCallback)
                startMockMode()
            }
        }, 10000)
    }

    fun stopScanning() {
        val scanner = bluetoothAdapter.bluetoothLeScanner
        scanner?.stopScan(scanCallback)
        
        if (bluetoothGatt != null) {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
            bluetoothGatt = null
        }
        
        if (isMockMode) {
            isMockMode = false
        }
        
        onBleStatusChanged?.invoke("BLE stopped")
    }

    private fun startMockMode() {
        isMockMode = true
        onBleStatusChanged?.invoke("Mock mode: generating data")
        Thread {
            while (isMockMode) {
                val mockHeartRate = (60..100).random()
                val message = "HR: $mockHeartRate BPM (Mock)"
                
                handler.post {
                    onDataReceived?.invoke(message)
                }
                
                // Send only via MQTT (not REST)
                Log.d("BleScanner", "Sending mock data: $message")
                mqttManager.publish(message)
                
                try {
                    Thread.sleep(1000)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }.start()
    }

    private fun sendToWatch(message: String) {
        if (bluetoothGatt != null) {
            val service = bluetoothGatt?.getService(SERVICE_UUID)
            val characteristic = service?.getCharacteristic(MESSAGE_CHARACTERISTIC_UUID)
            if (characteristic != null) {
                characteristic.value = message.toByteArray()
                bluetoothGatt?.writeCharacteristic(characteristic)
                Log.d("BleScanner", "Sent message to watch: $message")
            }
        } else {
            Log.d("BleScanner", "Cannot send to watch: Not connected (Mock Mode active?)")
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            Log.d("BleScanner", "Found device: ${device.name} - ${device.address}")
            handler.post { onBleStatusChanged?.invoke("Found: ${device.name ?: "Unknown"}") }
            
            // Stop scanning and connect
            bluetoothAdapter.bluetoothLeScanner?.stopScan(this)
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                bluetoothGatt = gatt
                gatt.discoverServices()
                handler.post { onBleStatusChanged?.invoke("Connected to watch") }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                handler.post { onBleStatusChanged?.invoke("BLE disconnected") }
                bluetoothGatt = null
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                val characteristic = service?.getCharacteristic(CHARACTERISTIC_UUID)
                if (characteristic != null) {
                    gatt.setCharacteristicNotification(characteristic, true)
                    val descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val data = characteristic.value
            val message = String(data, Charsets.UTF_8)
            handler.post { onDataReceived?.invoke("Received: $message") }
            // Send only via MQTT (not REST)
            Log.d("BleScanner", "Sending BLE data: $message")
            mqttManager.publish(message)
        }
    }
}
