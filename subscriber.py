import paho.mqtt.client as mqtt
import json
import base64
import time
from pathlib import Path

BROKER_IP = "192.168.1.3"
BROKER_PORT = 1883

PHOTO_DIR = Path("photos")
PHOTO_DIR.mkdir(exist_ok=True)

# telematry file
TELEMETRY_FILE = Path("telemetry_log.jsonl")



# ---------------- CALLBACKS ----------------

def on_connect(client, userdata, flags, rc):
    if rc == 0:
        print("Connected to the broker")
        client.subscribe("drone/#", qos=1)
    else:
        print("Error connection:", rc)

def on_message(client, userdata, msg):
    topic = msg.topic
    payload = msg.payload.decode()

    if topic == "drone/telemetry":
        handle_telemetry(payload)

    elif topic == "drone/photo":
        handle_photo(payload)

    else:
        print(f"Messagge on {topic}")

# ---------------- HANDLERS ----------------

def handle_telemetry(payload):
    try:
        data = json.loads(payload)
        
        print(f"[TEL] lat={data['lat']} lon={data['lon']} alt={data['altitude']} "
              f"yaw={data['yaw']} pitch={data['pitch']} roll={data['roll']} "
              f"t={data['timestamp']}")

        # save on file
        with open(TELEMETRY_FILE, "a") as f:
            f.write(json.dumps(data) + "\n")

    except Exception as e:
        print("Error telemetry:", e)
            


def handle_photo(payload):
    try:
        data = json.loads(payload)
        filename = f"{int(time.time())}_{data['filename']}"
        image_bytes = base64.b64decode(data["data"])

        path = PHOTO_DIR / filename
        with open(path, "wb") as f:
            f.write(image_bytes)

        print(f"[PHOTO] saved in {path}")

    except Exception as e:
        print("Error photo:", e)

#main

client = mqtt.Client(client_id="pc-subscriber")

client.on_connect = on_connect
client.on_message = on_message

client.connect(BROKER_IP, BROKER_PORT, keepalive=60)
client.loop_forever()
