# DjiDroneControl
A project based on DJI Mobile SDK V5 Sample for automated control and telemetry logging of DJI drones.
The goal is to experiment with autonomous flight logic and real-time data recording.

- The project is currently able to display telemetry logs of core drone information, including:
   - Attitude (roll, pitch, yaw)
   - GPS coordinates (latitude, longitude, altitude)
 
- Perform basic automated movements with virtual stick (in a raw state)

- Able to send telemetry with mqtt to another machine (using pc_mqtt_pubsub.py)

- Send photos taken from video stream (low resolution 720p)
     
 Planned improvements include: have a better movements options of drone and camera, 
 be able to take videos and send them through mqtt

 
How to set up the application:

Open the project in android of "SampleCode-V5/android-sdk-v5-as" - suggest to use Android Studio Meerkat | 2024.3.1 Patch 1 -

Make sure to have at least java 17 in Setting -> Build, Execution, Deployment -> Build Tools -> Gradle  in Gradle JDK section.

After, set the API keys in "SampleCode-V5/android-sdk-v5-as/gradle.properties", then Sync the project and build it to make sure everything works.





