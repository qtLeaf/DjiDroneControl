package dji.sampleV5.aircraft.tests.factorCorrection

import android.content.Context
import com.google.gson.Gson
import dji.sampleV5.aircraft.tests.control.VirtualFlightController
import kotlinx.coroutines.delay
import java.io.InputStreamReader
import kotlin.math.abs
import kotlin.math.pow

//Data model and json reader
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

object ModelLoader{
    fun LoadFromAssets(context: Context, fileName: String): ModelParameter{
        val inputStream=context.assets.open(fileName)
        val reader=InputStreamReader(inputStream)
        return Gson().fromJson(reader,ModelParameter::class.java)
    }
}


//Math core
class DroneCorrector (private val params: ModelParameter) {
    fun predictErr(timeMs: Double,power: Double):Double{
        val timeSc = ((timeMs- params.scaler_mean[0])/params.scaler_scale[0])
        val powerSc = ((power- params.scaler_mean[1])/params.scaler_scale[1])
        var predictError = params.intercept

        for(i in params.coefficients.indices){
            val coef=params.coefficients[i]
            if(abs(coef)>1e-8){
                val timeExp = params.powers[i][0].toDouble()
                val powExp =params.powers[i][1].toDouble()
                predictError += coef * (timeSc.pow(timeExp)) * (powerSc.pow(powExp))
            }
        }
        return predictError
    }
}

//Controller extension for movement
suspend fun VirtualFlightController.moveCor(action: String, targetTime: Long, power:Float,predictor: DroneCorrector, onDebug: (String) -> Unit = {}){
    if(power<=0f){
        return
    }

    val targetDistMeter=(targetTime/1000.0)*(power*10.0)
    val errorMeter= predictor.predictErr(targetTime.toDouble()/1000, power.toDouble())
    val excpectedActualDist=targetDistMeter + errorMeter
    val timeCor=if(excpectedActualDist >0 && targetDistMeter >0){
        (targetTime*(targetDistMeter/excpectedActualDist)).toLong().coerceAtLeast(0L)
    }else{
        0L
    }

    onDebug("$action | Err: ${String.format("%.2f", errorMeter)}m |MS: $timeCor $power")

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
        delay(timeCor)
        this.stop()
    }
}

suspend fun VirtualFlightController.rotationCor(action: String,targetTime: Long, power:Float,predictor: DroneCorrector, onDebug: (String)->Unit={}){
    if(power<=0f){
        return
    }

    val kDegMs=0.288
    val targetDistDegrees= targetTime*power*kDegMs
    val errorDegrees= predictor.predictErr(targetTime.toDouble()/1000.0, power.toDouble())
    val expectedActualDegrees=targetDistDegrees+errorDegrees
    val timeCor=if(expectedActualDegrees>0){
        (targetTime*(targetDistDegrees/expectedActualDegrees)).toLong()
    }else{
        0L
    }

    onDebug("$action $timeCor $power")

    if(timeCor >0){
        when(action){
            "rotateright" -> this.rotateRight(power)
            "rotateleft" -> this.rotateLeft(power)
            else ->{
                onDebug("direction not valid: $action")
                return
            }
        }
        delay(timeCor)
        this.stop()
    }
}