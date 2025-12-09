package com.example.wearsensorbridgehost.mobile

import android.util.Log
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

class MqttManager {

    private var mqttClient: MqttClient? = null

    companion object {
        // Replace with your VPS IP address
        private const val BROKER_URL = "tcp://kdhistory.paulsfamilyhistory.com:1883" 
        private const val CLIENT_ID = "WearSensorBridgeMobileHost"
        private const val TOPIC = "sensor/heartrate"
        private const val CONTROL_TOPIC = "sensor/control"
    }

    var onMessageReceived: ((String) -> Unit)? = null

    fun connect() {
        try {
            mqttClient = MqttClient(BROKER_URL, CLIENT_ID, MemoryPersistence())
            val options = MqttConnectOptions()
            options.isCleanSession = true
            // options.userName = "your_username"
            // options.password = "your_password".toCharArray()
            
            mqttClient?.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    Log.e("MqttManager", "Connection lost", cause)
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    Log.d("MqttManager", "Message arrived: $topic")
                    message?.let {
                        onMessageReceived?.invoke(String(it.payload))
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {
                    Log.d("MqttManager", "Delivery complete")
                }
            })

            mqttClient?.connect(options)
            mqttClient?.subscribe(CONTROL_TOPIC)
            Log.d("MqttManager", "Connected to MQTT Broker")
        } catch (e: MqttException) {
            Log.e("MqttManager", "Error connecting to MQTT Broker", e)
        }
    }

    fun publish(payload: String) {
        try {
            if (mqttClient?.isConnected == true) {
                val message = MqttMessage(payload.toByteArray())
                message.qos = 1
                mqttClient?.publish(TOPIC, message)
                Log.d("MqttManager", "Message published")
            } else {
                Log.e("MqttManager", "Client not connected")
                // Attempt reconnect
                connect()
            }
        } catch (e: MqttException) {
            Log.e("MqttManager", "Error publishing message", e)
        }
    }

    fun disconnect() {
        try {
            mqttClient?.disconnect()
        } catch (e: MqttException) {
            Log.e("MqttManager", "Error disconnecting", e)
        }
    }
}
