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
    
    var onStatusChanged: ((String) -> Unit)? = null
    var onDataReceived: ((String) -> Unit)? = null

    init {
        // Connect to MQTT in a background thread
        Thread {
            handler.post { onStatusChanged?.invoke("Connecting to MQTT...") }
            mqttManager.connect()
            handler.post { onStatusChanged?.invoke("MQTT Connected") }
            mqttManager.onMessageReceived = { message ->
                sendToWatch(message)
            }
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
        
        onStatusChanged?.invoke("Scanning for Watch...")
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
                onStatusChanged?.invoke("Scan Timeout. Switching to Mock Mode.")
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
        
        onStatusChanged?.invoke("Disconnected")
    }

    private fun startMockMode() {
        isMockMode = true
        onStatusChanged?.invoke("Mock Mode Active: Generating Data")
        Thread {
            while (isMockMode) {
                val mockHeartRate = (60..100).random()
                val data = java.nio.ByteBuffer.allocate(4).putInt(mockHeartRate).array()
                // In a real app, we would encrypt this too, but for mock we just send base64 of raw
                val encryptedString = Base64.encodeToString(data, Base64.NO_WRAP)
                
                handler.post {
                    onDataReceived?.invoke("HR: $mockHeartRate BPM (Mock)")
                }
                
                sendToServer(encryptedString)
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
            handler.post { onStatusChanged?.invoke("Found: ${device.name ?: "Unknown"}") }
            
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
                handler.post { onStatusChanged?.invoke("Connected to Watch") }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                handler.post { onStatusChanged?.invoke("Disconnected") }
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
            val encryptedString = Base64.encodeToString(data, Base64.NO_WRAP)
            handler.post { onDataReceived?.invoke("Encrypted Data Received") }
            sendToServer(encryptedString)
        }
    }

    private fun sendToServer(encryptedData: String) {
        // Send via REST API
        NetworkManager.api.sendData(SensorData(encryptedData)).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                Log.d("BleScanner", "Data sent to server via REST")
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                Log.e("BleScanner", "Failed to send data via REST", t)
            }
        })

        // Send via MQTT
        Thread {
            mqttManager.publish(encryptedData)
        }.start()
    }
}
