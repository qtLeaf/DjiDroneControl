package dji.v5.ux;


import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


import dji.sdk.keyvalue.value.common.LocationCoordinate3D;
import dji.sdk.keyvalue.value.flightcontroller.GPSSignalLevel;
import dji.v5.manager.KeyManager;
import dji.sdk.keyvalue.key.FlightControllerKey;
import dji.sdk.keyvalue.key.KeyTools;
import dji.sdk.keyvalue.value.common.Attitude;
import dji.v5.common.callback.CommonCallbacks;

public class TelemetryLogger {

    private static final String TAG = "TelemetryLogger";
    private BufferedWriter writer = null;
    private final Context context;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

    public interface LogListener {
        void onNewLogLine(String logLine);
        void onLogError(String error);
    }

    private LogListener listener;

    // KeyManager e Listener DJI
    private final KeyManager keyManager = KeyManager.getInstance();
    private final Object listenerObject = new Object();

    public TelemetryLogger(Context context) {
        this.context = context.getApplicationContext();
    }

    public void setLogListener(LogListener listener) {
        this.listener = listener;
    }

    public void startLogging() {//file

        File rootDir = context.getFilesDir();
        File dir = new File(rootDir, "DroneData");
        File file = new File(dir, "telemetry_log_" + System.currentTimeMillis() + ".txt");

        if (!dir.exists()) {
            boolean success = dir.mkdirs();
            if (!success) {
                Log.e(TAG, "Error during the creation of the log directory: " + dir.getAbsolutePath());
                return;
            }
        }

        try {
            writer = new BufferedWriter(new FileWriter(file, true));
            Log.i(TAG, "Logging telemetry located in: " + file.getAbsolutePath());
            listener.onLogError("Logging telemetry located in: " + file.getAbsolutePath());
        } catch (IOException e) {

            listener.onLogError("Error during the creation of the log file: " + e.getMessage());

            Log.e(TAG, "Error during the creation of the log file: " + e.getMessage());
            e.printStackTrace();
            writer = null;
            return;
        }

        keyManager.listen(KeyTools.createKey(FlightControllerKey.KeyAircraftAttitude), listenerObject, new CommonCallbacks.KeyListener<Attitude>() {
            @Override
            public void onValueChange(Attitude oldValue, Attitude newValue) {
                if (newValue != null) {
                    record("Attitude: roll=" + newValue.getRoll() + ", pitch=" + newValue.getPitch() + ", yaw=" + newValue.getYaw());
                }
            }
        });

        keyManager.listen(KeyTools.createKey(FlightControllerKey.KeyGPSSignalLevel), listenerObject, new CommonCallbacks.KeyListener<GPSSignalLevel>() {
            @Override
            public void onValueChange(GPSSignalLevel oldValue, GPSSignalLevel newValue) {
                if (newValue != null) {
                    record("GPS Signal: " + newValue.name());
                }
            }
        });

        keyManager.listen(KeyTools.createKey(FlightControllerKey.KeyAircraftLocation3D), listenerObject, new CommonCallbacks.KeyListener<LocationCoordinate3D>() {
            @Override
            public void onValueChange(LocationCoordinate3D oldValue, LocationCoordinate3D newValue) {
                if (newValue != null) {
                    record("Location: lat=" + newValue.getLatitude() + ", lon=" + newValue.getLongitude() + ", alt=" + newValue.getAltitude());
                }
            }
        });


    }

    private void record(String data) {//updete the view and add of the string to the file
        try {
            String timestamp = dateFormat.format(new Date());
            String logLine = timestamp + " | " + data;

            //notify the UI
            if (listener != null) {
                listener.onNewLogLine(logLine);
            }

            //write on file
            if (writer != null) {
                writer.write(logLine + "\n");
                writer.flush();
            }
        } catch (IOException e) {
            listener.onLogError("Error during writing to log file: "  + e.getMessage());
            Log.e(TAG, "Error during writing to log file: " + e.getMessage());
        }
    }

    public void stopLogging() {
        try {
            if (writer != null) {
                writer.close();
            }
        } catch (IOException e) {
            listener.onLogError("Error during the closing of the log file: " + e.getMessage());
            Log.e(TAG, "Error during the closing of the log file: " + e.getMessage());
        }
        writer = null;

        //delete all the listeners
        keyManager.cancelListen(listenerObject);
    }
}