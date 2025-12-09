import express from 'express';
import mqtt from 'mqtt';
import { Server } from 'socket.io';
import http from 'http';
import path from 'path';
import cors from 'cors';

const app = express();
const server = http.createServer(app);
const io = new Server(server, {
    cors: {
        origin: "*",
        methods: ["GET", "POST"]
    }
    // path: '/wear/socket.io' // Removed because Nginx strips /wear/
});

const PORT = 3000; // Internal port
const MQTT_BROKER = 'mqtt://localhost:1883';
const TOPIC_DATA = 'sensor/heartrate';
const TOPIC_CONTROL = 'sensor/control';

// Store last 100 messages
const messageHistory: string[] = [];

app.use(cors());
app.use(express.json());

// Serve static files from public directory
// We use a relative path for the router to handle /wear/ prefix if needed, 
// but express.static usually serves from root. 
// Since nginx will strip /wear/, we can serve from root here.
app.use(express.static(path.join(__dirname, '../public')));

// MQTT Client
const mqttClient = mqtt.connect(MQTT_BROKER);

mqttClient.on('connect', () => {
    console.log('Connected to MQTT Broker');
    mqttClient.subscribe(TOPIC_DATA, (err) => {
        if (!err) {
            console.log(`Subscribed to ${TOPIC_DATA}`);
        }
    });
});

mqttClient.on('message', (topic, message) => {
    const msgString = message.toString();
    console.log(`Received: ${msgString} on ${topic}`);
    
    const timestamp = new Date().toISOString();
    const displayMsg = `[${timestamp}] ${msgString}`;
    
    // Add to history
    messageHistory.unshift(displayMsg);
    if (messageHistory.length > 100) {
        messageHistory.pop();
    }

    // Broadcast to frontend
    io.emit('new_message', displayMsg);
});

// Socket.IO
io.on('connection', (socket) => {
    console.log('Frontend connected');
    
    // Send history on connect
    socket.emit('history', messageHistory);

    // Handle broadcast request from frontend
    socket.on('broadcast_message', (msg: string) => {
        console.log(`Broadcasting to watch: ${msg}`);
        mqttClient.publish(TOPIC_CONTROL, msg);
    });

    socket.on('disconnect', () => {
        console.log('Frontend disconnected');
    });
});

server.listen(PORT, () => {
    console.log(`Server running on port ${PORT}`);
});
