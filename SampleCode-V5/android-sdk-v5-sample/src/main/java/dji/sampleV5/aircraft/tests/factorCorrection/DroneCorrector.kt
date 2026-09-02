package dji.sampleV5.aircraft.tests.factorCorrection

import android.content.Context
import com.google.gson.Gson
import dji.sampleV5.aircraft.tests.control.VirtualFlightController
import kotlinx.coroutines.delay
import java.io.InputStreamReader
import kotlin.math.abs
import kotlin.math.pow

/**
 * Data class that represent the polynomial Regression model parameters.
 * this structure maps the exactly the JSON file exported from the python
 */
data class ModelParameter(
    val model_type:String,
    val degree: Int,
    val features: List<String>,
    val scaler_mean: List<Double>,
    val scaler_scale: List<Double>,
    val intercept: Double,
    val coefficients: List<Double>,
    val powers: List<List<Int>>
)

/**
 * Utility object responsible for loading and parsing the JSON file
 */

object ModelLoader{
    fun LoadFromAssets(context: Context, fileName: String): ModelParameter{
        val inputStream=context.assets.open(fileName)
        val reader=InputStreamReader(inputStream)
        return Gson().fromJson(reader,ModelParameter::class.java)
    }
}


/**
 * Math core that execute the polynomial regression formula.
 * it uses the loaded parameter to predict the spatial error of the drone
 */
class DroneCorrector (private val params: ModelParameter) {

    /**
     * Predicts the overshoot error
     * @param timeSec the target flight time in seconds
     * @param power the target engine power
     * @return the predicted overshoot error in meters or degrees*/
    fun predictErr(timeMs: Double,power: Double):Double{
        //Scale input using the StandardScaler parameter from Python
        val timeSc = ((timeMs- params.scaler_mean[0])/params.scaler_scale[0])
        val powerSc = ((power- params.scaler_mean[1])/params.scaler_scale[1])
        var predictError = params.intercept

        //Reconstruct the polynomial equation
        for(i in params.coefficients.indices){
            val coef=params.coefficients[i]
            //ignore near zero coefficients to optimize calculation speed
            if(abs(coef)>1e-8){
                val timeExp = params.powers[i][0].toDouble()
                val powExp =params.powers[i][1].toDouble()
                predictError += coef * (timeSc.pow(timeExp)) * (powerSc.pow(powExp))
            }
        }
        return predictError
    }
}

/**
 * Extension function for VirtualFlightController to execute movement with machine learning overshoot correction
 * It uses a proportional approach to calculate the right time needed to reach the target distance compensating the inertia
 */
suspend fun VirtualFlightController.moveCor(action: String, targetTime: Long, power:Float,predictor: DroneCorrector, onDebug: (String) -> Unit = {}){
    if(power<=0f){
        return
    }

    //calculate the theoretical distance the drone should travel
    val targetDistMeter=(targetTime/1000.0)*(power*10.0)
    //predict the overshoot error passing the time in seconds
    val errorMeter= predictor.predictErr(targetTime.toDouble()/1000, power.toDouble())
    //Calculate the total distance the drone would travel travel without correction
    val excpectedActualDist=targetDistMeter + errorMeter
    //proportional time correction to stop exactly at the target distance
    val timeCor=if(excpectedActualDist >0 && targetDistMeter >0){
        (targetTime*(targetDistMeter/excpectedActualDist)).toLong().coerceAtLeast(0L)
    }else{
        0L
    }

    //output
    onDebug("$action | Err: ${String.format("%.2f", errorMeter)}m |MS: $timeCor $power")

    //Execute the movement if the corrected time is greater than zero
    if(timeCor >0){
        when(action){
            "forward" -> this.forward(power)
            "backwards" -> this.backward(power)
            "right" -> this.right(power)
            "left" -> this.left(power)
            else ->{
                onDebug("direction not valid: $action")
                return
            }
        }
        //keep engines running for the corrected time and then stop
        delay(timeCor)
        this.stop()
    }
}

/**
 * Extension function for VirtualFlightController to execute rotation movement with machine learning overshoot correction
 */
suspend fun VirtualFlightController.rotationCor(action: String,targetTime: Long, power:Float,predictor: DroneCorrector, onDebug: (String)->Unit={}){
    if(power<=0f){
        return
    }
    //constant mapping power to degrees per milliseconds
    val kDegMs=0.288
    //calculate the theoretical degrees the drone should rotate
    val targetDistDegrees= targetTime*power*kDegMs
    //predict the rotational overshoot error
    val errorDegrees= predictor.predictErr(targetTime.toDouble()/1000.0, power.toDouble())
    //calculate total expected rotation without correction
    val expectedActualDegrees=targetDistDegrees+errorDegrees
    //proportional time correction for precise rotation
    val timeCor=if(expectedActualDegrees>0){
        (targetTime*(targetDistDegrees/expectedActualDegrees)).toLong()
    }else{
        0L
    }
    //Output
    onDebug("$action $timeCor $power")
    //execute the rotation if the corrected time is greater than zero
    if(timeCor >0){
        when(action){
            "rotateright" -> this.rotateRight(power)
            "rotateleft" -> this.rotateLeft(power)
            else ->{
                onDebug("direction not valid: $action")
                return
            }
        }
        //keep engine running for the corrected time then stop
        delay(timeCor)
        this.stop()
    }
}