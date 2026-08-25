package dji.sampleV5.aircraft.tests.navigation

import dji.sampleV5.aircraft.tests.control.VirtualFlightController
import dji.sdk.keyvalue.key.FlightControllerKey
import dji.sdk.keyvalue.key.KeyTools
import dji.sdk.keyvalue.value.common.LocationCoordinate3D
import dji.v5.manager.KeyManager
import kotlin.math.abs
import kotlin.math.pow

class WayPointNavigation (
    private val vfc: VirtualFlightController,
    private val onDebug: (String)->Unit
){
    private val ARRIVED_THRESH= 1.0
    private val SLOW_DOWN=4.5
    @Volatile
    private var running=false

    fun gotogps(endLat: Double, endLong: Double, endAlt: Double){
        if(running){
            return
        }
        running=true
        Thread{
            try{
                while(running){
                    val location = getLocation()
                    if(location == null){
                        vfc.stop()
                        return@Thread
                    }
                    val curLat =location.latitude
                    val curLong = location.longitude
                    val curAlt = location.altitude
                    val errAlt = endAlt - curAlt

                    if(abs(errAlt)>0.5){
                        if(errAlt > 0.5){
                            vfc.up(0.1f)
                            Thread.sleep(50L)
                            continue
                        }else if (errAlt < -0.5){
                            vfc.down(0.1f)
                            Thread.sleep(50L)
                            continue
                        }
                    }

                    /**with rotation mode*/
                    val aligned = bearingtocommandrot(bearing(curLat, endLat, curLong, endLong))
                    val dist = haversinedist(curLat, endLat, curLong, endLong)
                    if(dist<ARRIVED_THRESH){
                        vfc.stop()
                        onDebug("Arrived")
                        break
                    }
                    if(aligned){
                        if(dist>SLOW_DOWN) {
                            vfc.forward(0.05f)
                        }else{
                            vfc.forward(0.01f)
                        }
                    }else{
                        Thread.sleep(50L)
                        continue
                    }

                    /** without the rotation mode
                    val dist= haversinedist(curLat,endLat,curLong,endLong)
                    if(dist<ARRIVED_THRESH){
                    vfc.stop()
                    onDebug("Arrived")
                    break
                    }

                    val aligned =bearingtocommand(curLat,endLat,curLong,endLong)
                     */
                    Thread.sleep(200L)
                }
            } finally {
                vfc.stop()
                running=false
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

        if (Math.abs(errang)<= 10.0){
            return true
        }else{
            if(errang >0){
                vfc.rotateRight(0.05f)
            }else if(errang < 0){
                vfc.rotateLeft(0.05f)
            }
            return false
        }
    }

    /** function for goto without the rotation of the drone
    private fun bearingtocommand(lat1: Double, lat2: Double, longi1: Double, longi2: Double): Boolean{
        var err_lat=lat1-lat2
        var err_longi= longi1-longi2

        if (abs(err_lat)>=abs(err_longi)){
            if(err_lat>0){
                while(abs(err_lat)>0.5) {
                    if (err_lat > SLOW_DOWN) {
                        vfc.backward(0.1f)
                    } else {
                        vfc.backward(0.05f)
                    }
                }
            }else {
                while (abs(err_lat) > 0.5) {
                    if (abs(err_lat) > SLOW_DOWN) {
                        vfc.forward(0.1f)
                    } else {
                        vfc.forward(0.05f)
                    }
                }
            }
        }else{
            if(err_longi>0){
                while(abs(err_longi)>0.5) {
                    if (err_longi > SLOW_DOWN) {
                        vfc.left(0.1f)
                    } else {
                        vfc.left(0.05f)
                    }
                }
            }else{
                while(abs(err_longi)>0.5) {
                    if (abs(err_longi) > SLOW_DOWN) {
                        vfc.right(0.1f)
                    } else {
                        vfc.right(0.05f)
                    }
                }
            }
        }

        return true

    }
    */
    private fun getLocation(): LocationCoordinate3D? {
        val locationKey = KeyTools.createKey(FlightControllerKey.KeyAircraftLocation3D)

        val location = KeyManager.getInstance().getValue(locationKey)

        return location
    }

}