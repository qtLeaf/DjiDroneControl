package dji.sampleV5.aircraft.tests.navigation

import dji.sampleV5.aircraft.tests.control.VirtualFlightController
import dji.sdk.keyvalue.key.FlightControllerKey
import dji.sdk.keyvalue.key.KeyTools
import dji.sdk.keyvalue.value.common.LocationCoordinate3D
import dji.v5.manager.KeyManager
import kotlin.math.pow

class WayPointNavigation (
    private val vfc: VirtualFlightController,
    private val onDebug: (String)->Unit
){
    private val ARRIVED_THRESH= 2.0
    private val LOOP_DUR=200L
    // private val SLOW_DOWN=6.0

    fun gotogps(endLat: Double, endLong: Double, endAlt: Double){
        Thread{
            while(true){
                val location = getLocation() ?: return@Thread
                val curLat =location.latitude
                val curLong = location.longitude
                val curAlt = location.altitude
                val errAlt = endAlt - curAlt

                if(errAlt > 0.5){
                    vfc.up(0.1f)
                }else if (errAlt < -0.5){
                    vfc.down(0.1f)
                }

                /**with rotation mode*/
                val aligned = bearingtocommandrot(bearing(curLat, endLat, curLong, endLong))
                val dist = haversinedist(curLat, endLat, curLong, endLong)
                if(dist<ARRIVED_THRESH){
                    vfc.stop()
                    onDebug("Arrived")
                    break
                }
                if(aligned) {
                    vfc.forward(0.1f)
                }
                Thread.sleep(LOOP_DUR)
//*/
                /** without the rotation mode //
                val dist= haversinedist(curLat,endLat,curLong,endLong)
                if(dist<ARRIVED_THRESH){
                    vfc.stop()
                    onDebug("Arrived")
                    break
                }

                val aligned =bearingtocommand(bearing(curLat,endLat,curLong,endLong))
                if(aligned){
                    vfc.forward(0.1f)
                }
                Thread.sleep(LOOP_DUR)
*/
            }
        }.start()
    }



    /** haversine formule */
    private fun haversinedist(lat1: Double, lat2: Double, longi1: Double, longi2: Double): Double{
        val R=6371000.0
        val dphi=Math.toRadians(lat2-lat1)
        val phi1=Math.toRadians(lat1)
        val phi2=Math.toRadians(lat2)
        val dlam=Math.toRadians(longi2-longi1)

        val a=Math.sin(dphi/2).pow(2)+Math.cos(phi1)*Math.cos(phi2)*Math.sin(dlam/2).pow(2)
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a))
    }

    /**bearing formule */
    private fun bearing(lat1: Double, lat2: Double, longi1: Double, longi2: Double): Double{
        val phi1=Math.toRadians(lat1)
        val phi2=Math.toRadians(lat2)
        val dlam=Math.toRadians(longi2-longi1)

        val x= Math.sin(dlam)*Math.cos(phi2)
        val y= Math.cos(phi1)*Math.sin(phi2)-Math.sin(phi1)*Math.cos(phi2)*Math.cos(dlam)

        return (Math.toDegrees(Math.atan2(x,y)) + 360 ) % 360
    }

    /** function for goto with rotation of the drone*/
    private fun bearingtocommandrot(bear: Double): Boolean{
        val attitude = KeyTools.createKey(FlightControllerKey.KeyAircraftAttitude)
        val curYaw = KeyManager.getInstance().getValue(attitude)?.yaw ?: return false
        var errang= bear - curYaw

        if(errang > 180){
            errang -= 360
        }else if (errang< -180){
            errang +=360
        }

        if(errang >0){
            vfc.rotateRight(0.1f)
        }else if(errang < 0){
            vfc.rotateLeft(0.1f)
        }

        if (Math.abs(errang)<= 5.0){
            return true
        }else{
            if(errang >0){
                vfc.rotateRight(0.1f)
            }else if(errang < 0){
                vfc.rotateLeft(0.1f)
            }
            return false
        }
    }

    /** function for goto without the rotation of the drone*/
    private fun bearingtocommand(bear: Double): Boolean{
        when{
            bear>=315 || bear<=45 -> vfc.forward(0.1f)
            bear <= 135 -> vfc.right(0.1f)
            bear <= 225 -> vfc.backward(0.1f)
            bear < 315 -> vfc.left(0.1f)
            else -> vfc.stop()
        }

        return true

    }

    private fun getLocation(): LocationCoordinate3D? {
        val locationKey = KeyTools.createKey(FlightControllerKey.KeyAircraftLocation3D)

        val location = KeyManager.getInstance().getValue(locationKey)

        return location
    }

}