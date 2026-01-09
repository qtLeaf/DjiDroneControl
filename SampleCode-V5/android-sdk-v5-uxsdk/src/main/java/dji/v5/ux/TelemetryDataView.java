package dji.v5.ux;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.graphics.Color;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class TelemetryDataView extends FrameLayout implements TelemetryLogger.LogListener {

    private TelemetryLogger logger;
    private TextView logTextView;
    private TextView logErrorView;

    public TelemetryDataView(@NonNull Context context) {
        this(context, null);
    }

    public TelemetryDataView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TelemetryDataView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {

        LayoutInflater.from(context).inflate(R.layout.uxsdk_view_telemetry, this, true);

        logTextView = findViewById(R.id.log_text);
        logErrorView = findViewById(R.id.log_error);

        logger = new TelemetryLogger(context);
        logger.setLogListener(this);
    }


    @Override
    public void onNewLogLine(String logLine) {
        // update the TextView only with the last data listened
        logTextView.setText(logLine);

        // also Log.i works
    }

    @Override
    public void onLogError(String error) {
        logErrorView.setText(error);
    }

    @Override
    protected void onAttachedToWindow() {//start
        super.onAttachedToWindow();
        logger.startLogging();
    }

    @Override
    protected void onDetachedFromWindow() {//stop
        logger.stopLogging();
        super.onDetachedFromWindow();
    }
}