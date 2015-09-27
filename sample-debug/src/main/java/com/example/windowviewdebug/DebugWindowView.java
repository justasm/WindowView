package com.example.windowviewdebug;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;

import com.jmedeisis.windowview.WindowView;

/**
 * WindowView that exposes many internal properties through overlay debug text.
 */
public class DebugWindowView extends WindowView {
    private static final String LOG_TAG = DebugWindowView.class.getSimpleName();

    private boolean debugTilt = false;
    private boolean debugImage = false;
    private static final boolean DEBUG_LIFECYCLE = false;
    private final static int DEBUG_TEXT_SIZE = 32;
    private Paint debugTextPaint;

    private float latestYaw;
    private float latestPitch;
    private float latestRoll;

    public DebugWindowView(Context context) {
        super(context);
    }

    public DebugWindowView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DebugWindowView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public DebugWindowView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void init(Context context, AttributeSet attrs){
        super.init(context, attrs);

        debugTextPaint = new Paint();
        debugTextPaint.setColor(Color.MAGENTA);
        debugTextPaint.setTextSize(DEBUG_TEXT_SIZE);
        debugTextPaint.setTypeface(Typeface.MONOSPACE);
    }

    @Override
    public void onTiltUpdate(float yaw, float pitch, float roll){
        super.onTiltUpdate(yaw, pitch, roll);
        this.latestYaw = yaw;
        this.latestPitch = pitch;
        this.latestRoll = roll;
    }

    /**
     * Enables/disables on-screen debug information.
     * @param debugTilt if true, displays on-screen information about the current tilt values and limits.
     * @param debugImage if true, displays on-screen information about the source image and dimensions.
     */
    public void setDebugEnabled(boolean debugTilt, boolean debugImage){
        this.debugTilt = debugTilt;
        this.debugImage = debugImage;
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus){
        super.onWindowFocusChanged(hasWindowFocus);
        if(DEBUG_LIFECYCLE) Log.d(LOG_TAG, "onWindowFocusChanged(), hasWindowFocus: " + hasWindowFocus);
    }

    @Override
    protected void onAttachedToWindow(){
        super.onAttachedToWindow();
        if(DEBUG_LIFECYCLE) Log.d(LOG_TAG, "onAttachedToWindow()");
    }

    @Override
    protected void onDetachedFromWindow(){
        super.onDetachedFromWindow();
        if(DEBUG_LIFECYCLE) Log.d(LOG_TAG, "onDetachedFromWindow()");
    }


    @SuppressWarnings("UnusedAssignment")
    @Override
    protected void onDraw(@NonNull Canvas canvas){
        super.onDraw(canvas);

        int i = 0;
        if(debugImage){
            debugText(canvas, i++, "width      " + getWidth());
            debugText(canvas, i++, "height     " + getHeight());
            debugText(canvas, i++, "img width  " + getScaledImageWidth());
            debugText(canvas, i++, "img height " + getScaledImageHeight());

            debugText(canvas, i++, getTranslateMode() + " translateMode");

            float translateX = 0;
            float translateY = 0;
            if(heightMatches){
                translateX = (-getHorizontalOrigin() +
                        clampAbsoluteFloating(getHorizontalOrigin(), latestRoll, getMaxRoll())) / getMaxRoll();
            } else {
                translateY = (getVerticalOrigin() -
                        clampAbsoluteFloating(getVerticalOrigin(), latestPitch, getMaxPitch())) / getMaxPitch();
            }
            debugText(canvas, i++, "tx " + translateX);
            debugText(canvas, i++, "ty " + translateY);
            debugText(canvas, i++, "tx abs " + Math.round((widthDifference / 2) * translateX));
            debugText(canvas, i++, "ty abs " + Math.round((heightDifference / 2) * translateY));
            debugText(canvas, i++, "height matches " + heightMatches);
        }

        if(debugTilt){
            switch (sensor.getChosenSensorType()){
                case 0:
                    debugText(canvas, i++, "NO AVAILABLE SENSOR");
                    break;
                case Sensor.TYPE_ROTATION_VECTOR:
                    debugText(canvas, i++, "ROTATION_VECTOR");
                    break;
                case Sensor.TYPE_GRAVITY:
                    debugText(canvas, i++, "MAG + GRAVITY");
                    break;
                case Sensor.TYPE_ACCELEROMETER:
                    debugText(canvas, i++, "MAG + ACCELEROMETER");
                    break;
            }
            debugText(canvas, i++, getOrientationMode() + " orientationMode");

            /*if(haveOrigin){
                SensorManager.getOrientation(rotationMatrixOrigin, orientationOrigin);
                debugText(canvas, i++, "org yaw   " + orientationOrigin[0]*DEGREES_PER_RADIAN);
                debugText(canvas, i++, "org pitch " + orientationOrigin[1]*DEGREES_PER_RADIAN);
                debugText(canvas, i++, "org roll  " + orientationOrigin[2]*DEGREES_PER_RADIAN);
            }*/

            debugText(canvas, i++, "yaw   " + latestYaw);
            debugText(canvas, i++, "pitch " + latestPitch);
            debugText(canvas, i++, "roll  " + latestRoll);

            debugText(canvas, i++, "MAX_PITCH " + getMaxPitch());
            debugText(canvas, i++, "MAX_ROLL  " + getMaxRoll());

            debugText(canvas, i++, "HOR ORIGIN " + getHorizontalOrigin());
            debugText(canvas, i++, "VER ORIGIN " + getVerticalOrigin());

            switch(sensor.getScreenRotation()){
                case Surface.ROTATION_0:
                    debugText(canvas, i++, "ROTATION_0");
                    break;
                case Surface.ROTATION_90:
                    debugText(canvas, i++, "ROTATION_90");
                    break;
                case Surface.ROTATION_180:
                    debugText(canvas, i++, "ROTATION_180");
                    break;
                case Surface.ROTATION_270:
                    debugText(canvas, i++, "ROTATION_270");
                    break;
            }
        }
    }

    private void debugText(Canvas canvas, int i, String text){
        canvas.drawText(text, DEBUG_TEXT_SIZE, (2 + i) * DEBUG_TEXT_SIZE, debugTextPaint);
    }
}
