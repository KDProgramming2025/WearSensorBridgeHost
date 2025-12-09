package com.example.wearsensorbridgehost.wear

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.nio.ByteBuffer

class SensorService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var heartRateSensor: Sensor? = null
    private lateinit var bleServerManager: BleServerManager
    private lateinit var cryptoManager: CryptoManager

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        bleServerManager = BleServerManager(this)
        cryptoManager = CryptoManager()
        
        startForegroundService()
        bleServerManager.startAdvertising()
        
        bleServerManager.onMessageReceived = { message ->
            // Broadcast message to UI
            val intent = Intent("com.example.wearsensorbridgehost.MESSAGE_RECEIVED")
            intent.putExtra("message", message)
            sendBroadcast(intent)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        heartRateSensor?.also { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_HEART_RATE) {
            val heartRate = event.values[0].toInt()
            // Send as plain text
            val message = "HR: $heartRate"
            bleServerManager.updateSensorValue(message.toByteArray(Charsets.UTF_8))
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Handle accuracy changes
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun startForegroundService() {
        val channelId = "SensorServiceChannel"
        val channel = NotificationChannel(
            channelId,
            "Sensor Service Channel",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Sensor Service")
            .setContentText("Reading and broadcasting sensor data...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        startForeground(1, notification)
    }
}
