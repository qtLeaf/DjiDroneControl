package dji.sampleV5.aircraft.tests.navigation

/** import of libraries */
import dji.sampleV5.aircraft.tests.control.VirtualFlightController
import dji.sdk.keyvalue.key.FlightControllerKey
import dji.sdk.keyvalue.key.KeyTools
import dji.sdk.keyvalue.value.common.LocationCoordinate3D
import dji.v5.manager.KeyManager
import kotlin.math.abs
import kotlin.math.pow
/**
 * Handles autonomously waypoint navigation using VirtualFlightController commands.
 * The drone receive the command (es: goto latitude longitude altitude) and call the right command to reach that point.
 * The drone follows a strict priority every cicle: Altitude -> Rotation -> Forward movement*/
class WayPointNavigation (private val vfc: VirtualFlightController, private val onDebug: (String)->Unit){
    //Variable of navigation tolerances and thresholds
    private val ARRIVED_THRESH= 0.8   //target radius in meters to be considered arrived (it can be lowered, but a too tiny Threshold is risky)
    private val SLOW_DOWN=4.0         //distance in meters where the drone start decelerating (so we can obtain more precision at the end)
    private val ANGULAR_THRESH= 5.0   //Rotation (YAW) alignment tolerance in degrees
    private val HIGH_THRESH=0.5       //Altitude tolerance in meters
    @Volatile
    private var running=false

    /**Defines the current action of the drone to prevent overlapping commands:
     * -IDLE: holding position
     * -CLIMBING: adjusting altitude(up or down)
     * -ROTATING: adjusting yaw to face the target
     * -FORWARD: flying towards the target
     */

    private enum class DroneState{ IDLE, CLIMBING, ROTATING, FORWARD }
    private var currentState=DroneState.IDLE

    /**start of the waypoint navigation loop to reach a GPS coordinate
     * it runs on a separate thread to continuously monitor the telemetry
     */
    fun gotogps(endLat: Double, endLong: Double, endAlt: Double){
        if(running){
            return //prevent multiple navigation threads from running simultaneously
        }
        running=true
        currentState=DroneState.IDLE
        Thread{
            try{
                while(running){
                    val location = getLocation()
                    if(location == null){
                        vfc.stop() // safety stop if telemetry is lost
                        return@Thread
                    }

                    val curLat =location.latitude
                    val curLong = location.longitude
                    val curAlt = location.altitude
                    val errAlt = endAlt - curAlt

                    /**Priority 1: Altitude adjustment
                     * the drone must reach the right altitude to continue with the next priority
                     */
                    if(abs(errAlt)>HIGH_THRESH){
                        if(currentState!= DroneState.CLIMBING){
                            vfc.stop()  //safety stop other movement before climbing
                            currentState= DroneState.CLIMBING
                        }
                        if(errAlt > HIGH_THRESH){
                            vfc.up(0.1f)
                        }else if (errAlt < -HIGH_THRESH){
                            vfc.down(0.1f)
                        }
                        Thread.sleep(50L)
                        continue    //skip the rest of the loop until altitude is correct
                    }

                    //calculate distance and target heading
                    val aligned = bearingtocommandrot(bearing(curLat, endLat, curLong, endLong))
                    val dist = haversinedist(curLat, endLat, curLong, endLong)
                    //check if the final destination is reached
                    if(dist<ARRIVED_THRESH){
                        vfc.stop()
                        onDebug("Arrived")
                        break //if is arrived exit the navigation loop
                    }

                    /**
                     * Priority 2: YAW alignment(in the bearingtocommandrot function)
                     * wait until the drone is facing the target before moving forward
                     */
                    if(!aligned){
                        Thread.sleep(50L)
                        continue
                    }

                    /**Priority 3: Forward Movement
                     * move towards the target, with adjusting speed based on distance
                     */
                    if(currentState!=DroneState.FORWARD){
                        vfc.stop()  //safety stop other movement before going forward
                        currentState=DroneState.FORWARD
                    }

                    if(dist>SLOW_DOWN){
                        vfc.forward(0.05f) //normal cruising
                    }else{
                        vfc.forward(0.01f) //approach speed(deceleration)
                    }

                    Thread.sleep(200L) //main loop delay to allow telemetry update before another cicle
                }
            } finally {
                vfc.stop()     //safety stop of the drone in case the drone exit the loop or crashes
                running=false
            }
        }.start()
    }

    /** haversine formule
     * calculates the distance between two gps coordinate with haversine formule
     */
    private fun haversinedist(lat1: Double, lat2: Double, longi1: Double, longi2: Double): Double{
        val R=6371000.0 //earth's radius in meters
        val dphi=Math.toRadians(lat2-lat1)
        val phi1=Math.toRadians(lat1)
        val phi2=Math.toRadians(lat2)
        val dlam=Math.toRadians(longi2-longi1)

        val a=Math.sin(dphi/2).pow(2)+Math.cos(phi1)*Math.cos(phi2)*Math.sin(dlam/2).pow(2)
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a))
    }

    /**bearing formule
     * calculate the initial bearing from the current position to the target
     * @return Bearing in degrees(0° to 360°)
     */
    private fun bearing(lat1: Double, lat2: Double, longi1: Double, longi2: Double): Double{
        val phi1=Math.toRadians(lat1)
        val phi2=Math.toRadians(lat2)
        val dlam=Math.toRadians(longi2-longi1)

        val x= Math.sin(dlam)*Math.cos(phi2)
        val y= Math.cos(phi1)*Math.sin(phi2)-Math.sin(phi1)*Math.cos(phi2)*Math.cos(dlam)

        return (Math.toDegrees(Math.atan2(x,y)) + 360 ) % 360
    }

    /**
     * Compares the current drone yaw with the target bearing and command rotation
     * @param bear The target bearing in degrees.
     * @return true if the drone is aligned within the angular threshold, false otherwise
     */
    private fun bearingtocommandrot(bear: Double): Boolean{
        val attitude = KeyTools.createKey(FlightControllerKey.KeyAircraftAttitude)
        val curYaw = KeyManager.getInstance().getValue(attitude)?.yaw ?: return false
        var errang= bear - curYaw

        //normalize the error to find the shortest rotation path(-180 to +180 degrees)
        if(errang > 180){
            errang -= 360
        }else if (errang< -180){
            errang +=360
        }

        //check if the current heading is within the acceptable tolerance
        if (Math.abs(errang)<= ANGULAR_THRESH){
            vfc.stop() //stop rotation to prevent inertia overshooting
            return true
        }else{

            if(currentState!=DroneState.ROTATING){
                vfc.stop() //safety stop other movement before rotation
                currentState=DroneState.ROTATING
            }

            //command rotation based on the direction of the error
            if(errang >0){
                vfc.rotateRight(0.05f)
            }else if(errang < 0){
                vfc.rotateLeft(0.05f)
            }
            return false
        }
    }

    /**fetches the real time 3d location from the DJI Mobile SDK
     * @return LocationCoordinates3D object or null if telemetry is unavailable
     */
    private fun getLocation(): LocationCoordinate3D? {
        val locationKey = KeyTools.createKey(FlightControllerKey.KeyAircraftLocation3D)

        val location = KeyManager.getInstance().getValue(locationKey)

        return location
    }

}