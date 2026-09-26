# DjiDroneControl
A project based on DJI Mobile SDK V5 Sample for automated control and telemetry logging of DJI drones.
The goal is to experiment with autonomous flight logic and real-time data recording.

- The project is currently able to display telemetry logs of core drone information, including:
   - Attitude (roll, pitch, yaw)
   - GPS coordinates (latitude, longitude, altitude)
 
- Perform basic automated movements with virtual stick (in a raw state)

- Able to send telemetry with mqtt to another machine (using pc_mqtt_pubsub.py)

- Send photos taken from video stream (low resolution 720p)

- Automatically navigate to a target point using a waypoint navigation system
     
 Planned improvements include: have a better movements options of drone and camera, 
 be able to take videos and send them through mqtt

 
How to set up the application:

Open the project in android of "SampleCode-V5/android-sdk-v5-as" - suggest to use Android Studio Meerkat | 2024.3.1 Patch 1 -

Make sure to have at least java 17 in Setting -> Build, Execution, Deployment -> Build Tools -> Gradle  in Gradle JDK section.

After, set the API keys in "SampleCode-V5/android-sdk-v5-as/gradle.properties", in the 'AIRCRAFT_API_KEY' variable.

Then, sync the project by clicking the Gradle sync button (the elephant icon) in the top-right corner of the application

After syncing, build the project to make sure everything works: go to Menu -> Build -> Generate App Bundles or APK -> Generate APKs.

A build progress bar will appear in the bottom-right corner. Once it finishes, click the 'locate' link to open the directory containing the APK.

Transfer the .apk file to the phone you will be using for the flight and install the app.


How to set up the mqtt comunication system:

Create a file named 'test.conf' and add these two lines: 'listener 1883 0.0.0.0' and 'allow_anonymous true'. then save and close the file.

Create the broker executable file named 'mqttbroker.sh'. On linux, add these lines to the file: '#!/bin/bash' and 'mosquitto -c </home/tes.conf path> -v', then save it and close it.

Create the publisher executable file named 'pubblisher.sh'. On linux add these lines: '#!/bin/bash' and 'mosquitto_pub -h localhost -...' then save and close it.

Create the subscriber executable file named 'subscriber.sh'. On Linux add theese lines: '#!/bin/bash' and 'mosquitto_sub -h localhost -t test' then save and close it.


How to fly the drone:

On Linux, execute the broker script by running: './mqttbroker.sh'.

Navigate to DjiDroneControl/pc_mqtt_pubsub directory. Open the 'pc_mqtt_pubsub.py' file, make sure to enter the broker's IP address in the 'broker_ip' variable, then save and close it. Execute the script by running, 'python3 pc_mqtt_pubsub.py' On linux.

Connect the phone with the APK to the drone's remote controller using a USB cable, and power on the remote.

Then open the Application and enter the Broker's IP adress in the text input field.

Power on the drone and wait a couple of second until the indicator light on the drone's arms flash green.

Press the 'start test' button in the app and wait until it say that it has started successfully.

Now you can enter commands in the terminal where the 'pc_mqtt_pubsub.py' scrpit is running.

If you type 'h' in the terminal and press Enter, you will see a list of all available commands and their required inputs.

Example: if you want to use the waypoint navigation command, you need to type 'goto <latitude> <longitude> <altitude>' where altitude is in meter from the ground.



