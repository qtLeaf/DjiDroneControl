import paho.mqtt.client as mqtt
import json
import base64
import time
import threading
from pathlib import Path

class DroneController:
    def __init__(self, broker_ip="172.20.10.2", broker_port=1883):
        self.broker_ip = broker_ip
        self.broker_port = broker_port
        
        # Files and Directories
        self.photo_dir = Path("photos")
        self.photo_dir.mkdir(exist_ok=True)
        self.telemetry_file = Path("telemetry_log.jsonl")
        
        # MQTT Client Setup
        self.client = mqtt.Client(client_id="pc_controller_main")        
        self.client.on_connect = self.on_connect
        self.client.on_message = self.on_message
        
        self.running = True

        self.start_times = {}
    # ---------------- CALLBACKS ----------------

    def on_connect(self, client, userdata, flags, rc):
        if rc == 0:
            print(f"[SYSTEM] Connected to broker at {self.broker_ip}")
            # Subscribe to all drone topics
            self.client.subscribe("drone/#", qos=1)
        else:
            print(f"[ERROR] Connection failed with code {rc}")

    def on_message(self, client, userdata, msg):
        topic = msg.topic
        try:
            payload = msg.payload.decode()
            if topic == "drone/telemetry":
                self.handle_telemetry(payload)
            elif topic == "drone/photo":
                self.handle_photo(payload)
            elif topic == "drone/ping":
                self.handle_ping(payload)
            elif topic == "drone/ping_test":  # handle the Gallery + Telemetry + Ping packet
                self.handle_gallery_ping(payload)
            else:
                print(f"\n[MSG] Topic {topic}: {payload[:100]}...") 
        except Exception as e:
            print(f"\n[ERROR] Callback execution: {e}")

    # ---------------- HANDLERS ----------------

    def handle_telemetry(self, payload):
        """Silent logging of telemetry data"""
        try:
            data = json.loads(payload)
            with open(self.telemetry_file, "a") as f:
                f.write(json.dumps(data) + "\n")
        except:
            pass 

    def handle_photo(self, payload):
        """Handles standard real-time frames"""
        try:
            data = json.loads(payload)
            filename = f"live_{int(time.time())}.jpg"
            image_bytes = base64.b64decode(data["data"])
            
            path = self.photo_dir / filename
            with open(path, "wb") as f:
                f.write(image_bytes)
            print(f"\n[LIVE PHOTO] Saved: {path}")
        except Exception as e:
            print(f"\n[ERROR] Photo decoding: {e}")

    def handle_ping(self, payload):
        """Calculates standard RTT"""
        try:
            data = json.loads(payload)
            sent_time = data.get("timestamp", 0)
            if sent_time > 0:
                rtt = (time.time() * 1000) - sent_time
                print(f"\n[LATENCY] RTT: {rtt:.2f} ms")
        except Exception as e:
            print(f"\n[ERROR] Latency calculation: {e}")

    def handle_gallery_ping(self, payload):
        try:
            data = json.loads(payload)
            t_recv_pc = time.time() * 1000  # Current PC time in ms
            
            #timestamp the PC sent originally
            t_sent_pc = data.get("pc_timestamp", 0)
            #how long the drone worked (measured internally by drone)
            t_proc_drone = data.get("drone_proc_ms", 0)
            
            if t_sent_pc > 0:
                #total RTT (Total time from PC click to PC reception)
                total_rtt = t_recv_pc - t_sent_pc
                
                #total Network Time (Time spent on the air, both ways)
                total_network_time = total_rtt - t_proc_drone
                
       
                time_a_to_b = 10 
                time_b_to_a = total_network_time - time_a_to_b

                print(f"\n--- PERFORMANCE ANALYSIS ---")
                print(f"Total RTT: {total_rtt:.2f} ms")
                print(f"Drone Processing Time: {t_proc_drone:.2f} ms")
                print(f"Network Travel (Both ways): {total_network_time:.2f} ms")
                print(f"Estimated Photo Transmission (B->A): {max(0, time_b_to_a):.2f} ms")
                print(f"----------------------------")
                
            #Decode Photo
            photo_b64 = data.get("photo_base64", "")
            if photo_b64:
                image_bytes = base64.b64decode(photo_b64)
                filename = f"gallery_ping_{int(time.time())}.jpg"
                path = self.photo_dir / filename
                with open(path, "wb") as f:
                    f.write(image_bytes)
                
                print(f"\n--- GALLERY PING RESPONSE ---")
                print(f"Image Saved: {path} ({len(image_bytes)/1024:.1f} KB)")
                print(f"-----------------------------")
        except Exception as e:
            print(f"\n[ERROR] Gallery Ping decoding: {e}")

    # ---------------- ACTIONS ----------------

    def send_action(self, action, extra_params=None):
        payload = {"action": action}
        # Save PC time when sending
        t_start = time.time() * 1000
        self.start_times[action] = t_start 
        
        if action == "ping":
            payload["timestamp"] = t_start #standard ping
        
        if extra_params:
            payload.update(extra_params)
            
        self.client.publish("drone/commands", json.dumps(payload), qos=1)
        print(f"[CMD] Sent: {action}")

    # ---------------- INTERFACE ----------------

    def run(self):
        try:
            self.client.connect(self.broker_ip, self.broker_port, 60)
            self.client.loop_start()
            
            print("\n=== DRONE COMMAND INTERFACE ===")
            print("h: to see all commands")
            
            while self.running:
                line = input("Command > ").lower().strip().split()
                if not line: continue
                
                cmd = line[0]
                args = line[1:]

                # todo: videos - pipe of cmds/script interpreter
                if cmd == 'h':
                    print("takeoff | Land | stop | forward <time> <power> | backward <time> <power> | right <time> <power> | left<time> <power> ")
                    print(" rotateright <time> <power> | rotateleft <time> <power> | up <time> <power> | down <time> <power> | orbit <time>")
                    print("enablevs | disablevs | gimbal <pitch> <yaw> | zoom <value>") # to enable or disable the virtual stick (rn takeoff deos it automatically
                    print(" p: photo | pg: photo (Gallery Ping) | i: Ping | q: Quit")
                elif cmd == 'takeoff':
                    self.send_action("takeoff")
                elif cmd == 'land':
                    self.send_action("land")
                elif cmd == 'stop':
                    self.send_action("stop")
                elif cmd == 'setspeed':
                    try:
                        val = float(args[0])
                        if not (0 < speed <= 1.0):
                            print("Speed must be > 0 and <= 1.0")
                            continue
                        else:
                            self.send_action("speed", {"value": val})
                    except (IndexError, ValueError):
                        print("Use: setspeed 0.5")
                elif cmd == 'forward':
                    try:
                        duration = float(args[0])
                        speed = float(args[1])
                        if duration <= 0:
                            print("Duration must be > 0")
                            continue
                        if not (0 < speed <= 1.0):
                            print("Speed must be > 0 and <= 1.0")
                            continue
                        self.send_action(
                            "forward",
                            {
                                "duration": duration,
                                "speed": speed
                            }
                        )
                    except (IndexError, ValueError):
                        print("Use: forward 1000 0.1")
                elif cmd == 'backward':
                    try:
                        duration = float(args[0])
                        speed = float(args[1])
                        if duration <= 0:
                            print("Duration must be > 0")
                            continue
                        if not (0 < speed <= 1.0):
                            print("Speed must be > 0 and <= 1.0")
                            continue
                        self.send_action(
                            "backwards",
                            {
                                "duration": duration,
                                "speed": speed
                            }
                        )
                    except (IndexError, ValueError):
                        print("Use: backward 1000 0.1")
                
                elif cmd == 'right':
                    try:
                        duration = float(args[0])
                        speed = float(args[1])
                        if duration <= 0:
                            print("Duration must be > 0")
                            continue
                        if not (0 < speed <= 1.0):
                            print("Speed must be > 0 and <= 1.0")
                            continue
                        self.send_action(
                            "right",
                            {
                                "duration": duration,
                                "speed": speed
                            }
                        )
                    except (IndexError, ValueError):
                        print("Use: right 1000 0.1")
                elif cmd == 'left':
                    try:
                        duration = float(args[0])
                        speed = float(args[1])
                        if duration <= 0:
                            print("Duration must be > 0")
                            continue
                        if not (0 < speed <= 1.0):
                            print("Speed must be > 0 and <= 1.0")
                            continue
                        self.send_action(
                            "left",
                            {
                                "duration": duration,
                                "speed": speed
                            }
                        )
                    except (IndexError, ValueError):
                        print("Use: left 1000 0.1")

                elif cmd == 'rotateright':
                    try:
                        duration = float(args[0])
                        speed = float(args[1])
                        if duration <= 0:
                            print("Duration must be > 0")
                            continue
                        if not (0 < speed <= 1.0):
                            print("Speed must be > 0 and <= 1.0")
                            continue
                        self.send_action(
                            "rotateright",
                            {
                                "duration": duration,
                                "speed": speed
                            }
                        )
                    except (IndexError, ValueError):
                        print("Use: rotateright 1000 0.1")
                elif cmd == 'rotateleft':
                    try:
                        duration = float(args[0])
                        speed = float(args[1])
                        if duration <= 0:
                            print("Duration must be > 0")
                            continue
                        if not (0 < speed <= 1.0):
                            print("Speed must be > 0 and <= 1.0")
                            continue
                        self.send_action(
                            "rotateleft",
                            {
                                "duration": duration,
                                "speed": speed
                            }
                        )
                    except (IndexError, ValueError):
                        print("Use: rotateleft 1000 0.1")

                elif cmd == 'up':
                    try:
                        duration = float(args[0])
                        speed = float(args[1])
                        if duration <= 0:
                            print("Duration must be > 0")
                            continue
                        if not (0 < speed <= 1.0):
                            print("Speed must be > 0 and <= 1.0")
                            continue
                        self.send_action(
                            "up",
                            {
                                "duration": duration,
                                "speed": speed
                            }
                        )
                    except (IndexError, ValueError):
                        print("Use: up 1000 0.1")

                elif cmd == 'down':
                    try:
                        duration = float(args[0])
                        speed = float(args[1])
                        if duration <= 0:
                            print("Duration must be > 0")
                            continue
                        if not (0. <= speed <= 1.0):
                            print("Speed out of range(0 - 1.0)")
                            continue
                        self.send_action(
                            "down",
                            {
                                "duration": duration,
                                "speed": speed
                            }
                        )
                    except (IndexError, ValueError):
                        print("Use: down 1000 0.1")

                elif cmd == 'orbit':
                    try:
                        duration = float(args[0])
                        speed = float(args[1])
                        if duration <= 0:
                            print("Duration must be > 0")
                            continue
                    
                        self.send_action(
                            "orbit",
                            {
                                "duration": duration,
                            }
                        )
                    except (IndexError, ValueError):
                        print("Use: down 1000 0.1")

                elif cmd == 'stop':
                    self.send_action("stop")
                elif cmd == 'gimbal':
                    try:
                        pitch = float(args[0])
                        yaw = float(args[1])
                        
                        if not (-90 <= pitch <= 30):
                            print("Pitch range: -90 to 30")
                            continue
                            
                        self.send_action(
                            "gimbal",
                            {
                                "pitch": pitch,
                                "yaw": yaw
                            }
                        )
                    except (IndexError, ValueError):
                        print("Use: gimbal -45 0")
                elif cmd == 'zoom':
                    try:
                        val = float(args[0])
                        
                        if val < 1:
                            print("Zoom must be >= 1") #2.0 max for 4k, 4.0 max for 1080p (rn photo are staked at 720p)
                            continue
                            
                        self.send_action(
                            "zoom",
                            {
                                "value": val
                            }
                        )
                    except (IndexError, ValueError):
                        print("Use: zoom 2.0")
                elif cmd == 'photo':
                    self.send_action("photo")
                elif cmd == 'pg':
                    self.send_action("ping-photo") # This triggers executeGalleryPingTest()
                elif cmd == 'i':
                    self.send_action("ping")
                elif cmd == 'enablevs':
                    self.send_action("enablevs")
                elif cmd == 'disablevs':
                    self.send_action("disablevs")
                elif cmd == 'q': #quit
                    self.running = False
                elif cmd == '':
                    continue
                else:
                    print("Unknown command shortcut.")

        except KeyboardInterrupt:
            print("\nShutting down...")
        finally:
            self.client.loop_stop()
            self.client.disconnect()

if __name__ == "__main__":
    controller = DroneController()
    controller.run()