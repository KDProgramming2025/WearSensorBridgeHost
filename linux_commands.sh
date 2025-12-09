
PID=$!
sleep 1
docker exec mosquitto mosquitto_pub -t sensor/heartrate -m "Live Test Data"
wait $PID

