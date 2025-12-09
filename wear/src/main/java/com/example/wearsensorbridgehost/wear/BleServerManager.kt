package com.example.wearsensorbridgehost.wear

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import java.util.UUID

@SuppressLint("MissingPermission")
class BleServerManager(private val context: Context) {

    private var bluetoothManager: BluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private var bluetoothAdapter: BluetoothAdapter = bluetoothManager.adapter
    private var bluetoothGattServer: BluetoothGattServer? = null
    private var registeredDevices = mutableSetOf<BluetoothDevice>()

    companion object {
        // Using standard Heart Rate Service UUIDs for demonstration
        val SERVICE_UUID: UUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb") 
        val CHARACTERISTIC_UUID: UUID = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb")
        val MESSAGE_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002A38-0000-1000-8000-00805f9b34fb") // Custom UUID for messages
    }

    var onMessageReceived: ((String) -> Unit)? = null

    fun startAdvertising() {
        val advertiser: BluetoothLeAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser ?: return
        
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        advertiser.startAdvertising(settings, data, advertiseCallback)
        startServer()
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.d("BleServer", "Advertising started")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e("BleServer", "Advertising failed: $errorCode")
        }
    }

    private fun startServer() {
        bluetoothGattServer = bluetoothManager.openGattServer(context, gattServerCallback)
        
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        
        val characteristic = BluetoothGattCharacteristic(
            CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        
        val messageCharacteristic = BluetoothGattCharacteristic(
            MESSAGE_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        service.addCharacteristic(characteristic)
        service.addCharacteristic(messageCharacteristic)
        bluetoothGattServer?.addService(service)
    }

    fun updateSensorValue(value: ByteArray) {
        val characteristic = bluetoothGattServer?.getService(SERVICE_UUID)?.getCharacteristic(CHARACTERISTIC_UUID)
        characteristic?.value = value
        
        for (device in registeredDevices) {
            bluetoothGattServer?.notifyCharacteristicChanged(device, characteristic, false)
        }
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                registeredDevices.add(device)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                registeredDevices.remove(device)
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray
        ) {
             bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (characteristic.uuid == MESSAGE_CHARACTERISTIC_UUID) {
                val message = String(value)
                Log.d("BleServer", "Message received: $message")
                onMessageReceived?.invoke(message)
                bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            } else {
                bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
            }
        }
    }
}
