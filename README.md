# DjiDroneControl
A project based on DJI Mobile SDK V5 Sample for automated control and telemetry logging of DJI drones.
The goal is to experiment with autonomous flight logic and real-time data recording.

- Current State the project is able to display telemetry logs of core drone information, including:
   - Attitude (roll, pitch, yaw)
   - GPS coordinates (latitude, longitude, altitude)
   - IMU data (accelerometer and gyroscope bias, temperature)
   - Battery level
 In future profress it schould save it an a file and transfer the file to another source.
 Logs are automatically generated when the drone is connected and stored in the app’s local directory for later analysis.

To set the applicarion open the project in android of "SampleCode-V5/android-sdk-v5-as" - sudjest to use Android Studio Meerkat | 2024.3.1 Patch 1
After the set the API keys in "SampleCode-V5/android-sdk-v5-as/gradle.properties"






