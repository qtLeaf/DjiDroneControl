# DjiDroneControl
A project based on DJI Mobile SDK V5 Sample for automated control and telemetry logging of DJI drones.
The goal is to experiment with autonomous flight logic and real-time data recording.

- Current State the project is able to display telemetry logs of core drone information, including:
   - Attitude (roll, pitch, yaw)
   - GPS coordinates (latitude, longitude, altitude)
   - IMU data (accelerometer and gyroscope bias, temperature)
   - Battery level
 
- Able to make automated movements with virualstick (in a raw state)

- Able to sent telemetry with mqtt to another machine (usinf pc_mqtt_pubsub.py)
     
 In future progress it should have a better movements options of drone and camere, 
 be able to take photos/videos and send them trought mqtt

To set the application open the project in android of "SampleCode-V5/android-sdk-v5-as" - suggest to use Android Studio Meerkat | 2024.3.1 Patch 1
After the set the API keys in "SampleCode-V5/android-sdk-v5-as/gradle.properties"

