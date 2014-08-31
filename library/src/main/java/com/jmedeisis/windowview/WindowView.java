package com.jmedeisis.windowview;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.ImageView;

/**
 * An ImageView that automatically pans in response to device tilt.
 * Currently only supports {@link android.widget.ImageView.ScaleType#CENTER_CROP}.
 */
public class WindowView extends ImageView implements SensorEventListener {
    private static final String LOG_TAG = "WindowView";

    private final SensorManager sensorManager;

    private float latestYaw;
    private float latestPitch;
    private float latestRoll;

    // 1 radian = 57.2957795 degrees
    private static final float DEGREES_PER_RADIAN = 57.2957795f;

    private static final int NUM_FILTER_SAMPLES = 15;
    private static final float LOW_PASS_COEFF = 0.5f;
    private final Filter yawFilter;
    private final Filter pitchFilter;
    private final Filter rollFilter;

    /** @see {@link android.view.Display#getRotation()}. */
    private final int screenRotation;

    private final float[] rotationMatrix = new float[9];
    private final float[] rotationMatrixTemp = new float[9];
    private final float[] rotationMatrixOrigin = new float[9];
    private final float[] orientation = new float[3];
    private final float[] orientationOrigin = new float[3];
    private final float[] latestAccelerations = new float[3];
    private final float[] latestMagFields = new float[3];

    private boolean haveOrigin = false;
    private boolean haveGravData = false;
    private boolean haveAccelData = false;
    private boolean haveMagData = false;

    private static final float DEFAULT_MAX_PITCH = 30;
    private static final float DEFAULT_MAX_ROLL = 30;
    private static final float DEFAULT_HORIZONTAL_ORIGIN = 0;
    private static final float DEFAULT_VERTICAL_ORIGIN = 0;
    private float maxPitch;
    private float maxRoll;
    private float horizontalOrigin;
    private float verticalOrigin;

    /** Interface definition for a callback to be invoked when new orientation values are processed. */
    public static interface OnNewOrientationListener {
        /**
         * Called when new orientation values are present.
         * @see com.jmedeisis.windowview.WindowView#getLatestYaw()
         * @see com.jmedeisis.windowview.WindowView#getLatestPitch()
         * @see com.jmedeisis.windowview.WindowView#getLatestRoll()
         */
        public void onNewOrientation(WindowView windowView);
    }
    private OnNewOrientationListener orientationListener;

    /** Determines the basis in which device orientation is measured. */
    public static enum OrientationMode {
        /** Measures absolute yaw / pitch / roll (i.e. relative to the world). */
        ABSOLUTE,
        /**
         * Measures yaw / pitch / roll relative to the starting orientation.
         * The starting orientation is determined upon receiving the first sensor data,
         * but can be manually reset at any time using {@link #resetOrientationOrigin()}.
         */
        RELATIVE
    }
    private static final OrientationMode DEFAULT_ORIENTATION_MODE = OrientationMode.RELATIVE;
    private OrientationMode orientationMode;

    /** Determines the relationship between change in device tilt and change in image translation. */
    public static enum TranslateMode {
        /**
         * The image is translated by a constant amount per unit of device tilt.
         * Generally preferable when viewing multiple adjacent WindowViews that have different
         * contents but should move in tandem.
         * <p>
         * Same amount of tilt will result in the same translation for two images of differing size.
         */
        CONSTANT,
        /**
         * The image is translated proportional to its off-view size. Generally preferable when
         * viewing a single WindowView, this mode ensures that the full image can be 'explored'
         * within a fixed tilt amount range.
         * <p>
         * Same amount of tilt will result in different translation for two images of differing size.
         */
        PROPORTIONAL
    }
    private static final TranslateMode DEFAULT_TRANSLATE_MODE = TranslateMode.PROPORTIONAL;
    private TranslateMode translateMode;

    // layout
    private boolean heightMatches;
    private float widthDifference;
    private float heightDifference;

    // debug
    private boolean debugTilt = false;
    private boolean debugImage = false;
    private static final boolean DEBUG_LIFECYCLE = false;
    private final static int DEBUG_TEXT_SIZE = 32;
    private final Paint debugTextPaint;

    public WindowView(Context context) {
        this(context, null);
    }

    public WindowView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WindowView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        maxPitch = DEFAULT_MAX_PITCH;
        maxRoll = DEFAULT_MAX_ROLL;
        verticalOrigin = DEFAULT_VERTICAL_ORIGIN;
        horizontalOrigin = DEFAULT_HORIZONTAL_ORIGIN;
        orientationMode = DEFAULT_ORIENTATION_MODE;
        translateMode = DEFAULT_TRANSLATE_MODE;

        if(null != attrs){
            final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.WindowView);
            maxPitch = a.getFloat(R.styleable.WindowView_maxPitch, maxPitch);
            maxRoll = a.getFloat(R.styleable.WindowView_maxRoll, maxRoll);
            verticalOrigin = a.getFloat(R.styleable.WindowView_verticalOrigin, verticalOrigin);
            horizontalOrigin = a.getFloat(R.styleable.WindowView_horizontalOrigin, horizontalOrigin);

            int orientationModeIndex = a.getInt(R.styleable.WindowView_orientationMode, -1);
            if(orientationModeIndex >= 0){
                orientationMode = OrientationMode.values()[orientationModeIndex];
            }
            int translateModeIndex = a.getInt(R.styleable.WindowView_translateMode, -1);
            if(translateModeIndex >= 0){
                translateMode = TranslateMode.values()[translateModeIndex];
            }
            a.recycle();
        }

        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);

        yawFilter = new Filter(NUM_FILTER_SAMPLES, LOW_PASS_COEFF, 0);
        pitchFilter = new Filter(NUM_FILTER_SAMPLES, LOW_PASS_COEFF, 0);
        rollFilter = new Filter(NUM_FILTER_SAMPLES, LOW_PASS_COEFF, 0);

        screenRotation = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay().getRotation();

        debugTextPaint = new Paint();
        debugTextPaint.setColor(Color.MAGENTA);
        debugTextPaint.setTextSize(DEBUG_TEXT_SIZE);
        debugTextPaint.setTypeface(Typeface.MONOSPACE);

        setScaleType(ScaleType.CENTER_CROP);
    }

    /*
     * LIFE-CYCLE
     * ---------------------------------------------------------------------------------------------
     */
    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus){
        super.onWindowFocusChanged(hasWindowFocus);
        if(DEBUG_LIFECYCLE) Log.d(LOG_TAG, "onWindowFocusChanged(), hasWindowFocus: " + hasWindowFocus);
        if(hasWindowFocus){
            registerListeners();
        } else {
            unregisterListeners();
        }
    }

    private void registerListeners(){
        if(DEBUG_LIFECYCLE) Log.d(LOG_TAG, "Started listening to sensor events.");
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
                SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY),
                SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_GAME);
    }

    private void unregisterListeners(){
        if(DEBUG_LIFECYCLE) Log.d(LOG_TAG, "Stopped listening to sensor events.");
        sensorManager.unregisterListener(this);
    }

    @Override
    protected void onAttachedToWindow(){
        super.onAttachedToWindow();
        if(DEBUG_LIFECYCLE) Log.d(LOG_TAG, "onAttachedToWindow()");
        registerListeners();
    }

    @Override
    protected void onDetachedFromWindow(){
        super.onDetachedFromWindow();
        if(DEBUG_LIFECYCLE) Log.d(LOG_TAG, "onDetachedFromWindow()");
        unregisterListeners();
    }

    /*
     * DRAWING & LAYOUT
     * ---------------------------------------------------------------------------------------------
     */
    @SuppressWarnings("UnusedAssignment")
    @Override
    protected void onDraw(Canvas canvas){
        // -1 -> 1
        float translateX = 0f;
        float translateY = 0f;
        if(heightMatches){
            // only let user tilt horizontally
            translateX = (-horizontalOrigin +
                    clampAbsoluteFloating(horizontalOrigin, latestRoll, maxRoll)) / maxRoll;
        } else {
            // only let user tilt vertically
            translateY = (verticalOrigin -
                    clampAbsoluteFloating(verticalOrigin, latestPitch, maxPitch)) / maxPitch;
        }
        canvas.save();
        switch(translateMode){
            case CONSTANT:
                canvas.translate(clampAbsoluteFloating(0, 300 * translateX, widthDifference / 2),
                        clampAbsoluteFloating(0, 300 * translateY, heightDifference / 2));
                break;
            case PROPORTIONAL:
                canvas.translate(Math.round((widthDifference / 2) * translateX),
                        Math.round((heightDifference / 2) * translateY));
                break;
        }
        super.onDraw(canvas);
        canvas.restore();

        // debug from here on!
        int i = 0;
        if(debugImage){
            debugText(canvas, i++, "width      " + getWidth());
            debugText(canvas, i++, "height     " + getHeight());
            debugText(canvas, i++, "img width  " + getScaledImageWidth());
            debugText(canvas, i++, "img height " + getScaledImageHeight());

            debugText(canvas, i++, translateMode + " translateMode");

            debugText(canvas, i++, "tx " + translateX);
            debugText(canvas, i++, "ty " + translateY);
            debugText(canvas, i++, "tx abs " + Math.round((widthDifference / 2) * translateX));
            debugText(canvas, i++, "ty abs " + Math.round((heightDifference / 2) * translateY));
            debugText(canvas, i++, "height matches " + heightMatches);
        }

        if(debugTilt){
            debugText(canvas, i++, orientationMode + " orientationMode");

            if(haveOrigin){
                SensorManager.getOrientation(rotationMatrixOrigin, orientationOrigin);
                debugText(canvas, i++, "org yaw   " + orientationOrigin[0]*DEGREES_PER_RADIAN);
                debugText(canvas, i++, "org pitch " + orientationOrigin[1]*DEGREES_PER_RADIAN);
                debugText(canvas, i++, "org roll  " + orientationOrigin[2]*DEGREES_PER_RADIAN);
            }

            debugText(canvas, i++, "yaw   " + latestYaw);
            debugText(canvas, i++, "pitch " + latestPitch);
            debugText(canvas, i++, "roll  " + latestRoll);

            debugText(canvas, i++, "MAX_PITCH " + maxPitch);
            debugText(canvas, i++, "MAX_ROLL  " + maxRoll);

            debugText(canvas, i++, "HOR ORIGIN " + horizontalOrigin);
            debugText(canvas, i++, "VER ORIGIN " + verticalOrigin);

            switch(screenRotation){
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

    private float clampAbsoluteFloating(float origin, float value, float maxAbsolute){
        return value < origin ?
                Math.max(value, origin - maxAbsolute) : Math.min(value, origin + maxAbsolute);
    }

    /** See {@link com.jmedeisis.windowview.WindowView.TranslateMode}. */
    public void setTranslateMode(TranslateMode translateMode){
        this.translateMode = translateMode;
    }

    public TranslateMode getTranslateMode(){
        return translateMode;
    }

    /** Maximum angle (in degrees) from origin for vertical tilts. */
    public void setMaxPitch(float maxPitch) {
        this.maxPitch = maxPitch;
    }

    public float getMaxPitch() {
        return maxPitch;
    }

    /** Maximum angle (in degrees) from origin for horizontal tilts. */
    public void setMaxRoll(float maxRoll) {
        this.maxRoll = maxRoll;
    }

    public float getMaxRoll() {
        return maxRoll;
    }

    /**
     * Horizontal origin (in degrees). When {@link #latestRoll} equals this value, the image
     * is centered horizontally.
     */
    public void setHorizontalOrigin(float horizontalOrigin) {
        this.horizontalOrigin = horizontalOrigin;
    }

    public float getHorizontalOrigin() {
        return horizontalOrigin;
    }

    /**
     * Vertical origin (in degrees). When {@link #latestPitch} equals this value, the image
     * is centered vertically.
     */
    public void setVerticalOrigin(float verticalOrigin) {
        this.verticalOrigin = verticalOrigin;
    }

    public float getVerticalOrigin() {
        return verticalOrigin;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh){
        heightMatches = !widthRatioGreater(w, h, getDrawable().getIntrinsicWidth(),
                getDrawable().getIntrinsicHeight());
        widthDifference = getScaledImageWidth() - getWidth();
        heightDifference = getScaledImageHeight() - getHeight();
    }

    private static boolean widthRatioGreater(float width, float height,
                                             float otherWidth, float otherHeight){
        return height / otherHeight < width / otherWidth;
    }

    private float getScaledImageWidth(){
        final ScaleType scaleType = getScaleType();
        float intrinsicImageWidth = getDrawable().getIntrinsicWidth();
        float intrinsicImageHeight = getDrawable().getIntrinsicHeight();

        if(ScaleType.CENTER_CROP == scaleType){
            if(widthRatioGreater(getWidth(), getHeight(), intrinsicImageWidth, intrinsicImageHeight)){
                intrinsicImageWidth = getWidth();
            } else {
                intrinsicImageWidth *= getHeight() / intrinsicImageHeight;
            }
            return intrinsicImageWidth;
        }

        return 0f;
    }

    private float getScaledImageHeight(){
        final ScaleType scaleType = getScaleType();
        float intrinsicImageWidth = getDrawable().getIntrinsicWidth();
        float intrinsicImageHeight = getDrawable().getIntrinsicHeight();

        if(ScaleType.CENTER_CROP == scaleType){
            if(widthRatioGreater(getWidth(), getHeight(), intrinsicImageWidth, intrinsicImageHeight)){
                intrinsicImageHeight *= getWidth() / intrinsicImageWidth;
            } else {
                intrinsicImageHeight = getHeight();
            }
            return intrinsicImageHeight;
        }

        return 0f;
    }

    @Override
    public void setScaleType(ScaleType scaleType){
        if(ScaleType.CENTER_CROP != scaleType)
            throw new IllegalArgumentException("Image scale type " + scaleType +
                    " is not supported by WindowView. Use CENTER_CROP instead.");
        super.setScaleType(scaleType);
    }

    /*
     * SENSOR DATA ACQUISITION / PROCESSING
     * ---------------------------------------------------------------------------------------------
     */
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // nothing to do here..
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch(event.sensor.getType()){
            case Sensor.TYPE_GRAVITY:
                System.arraycopy(event.values, 0, latestAccelerations, 0, 3);
                haveGravData = true;
                break;
            case Sensor.TYPE_ACCELEROMETER:
                if(haveGravData){
                    // gravity sensor data is better! let's not listen to the accelerometer anymore
                    sensorManager.unregisterListener(this,
                            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER));
                    break;
                }
                System.arraycopy(event.values, 0, latestAccelerations, 0, 3);
                haveAccelData = true;
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                System.arraycopy(event.values, 0, latestMagFields, 0, 3);
                haveMagData = true;
                break;
        }

        if(haveDataNecessaryToComputeOrientation()){
            computeOrientation();
        }
    }

    /** @return true if both {@link #latestAccelerations} and {@link #latestMagFields} have valid values. */
    private boolean haveDataNecessaryToComputeOrientation(){
        return (haveGravData || haveAccelData) && haveMagData;
    }

    /**
     * Computes the latest rotation, and stores it in {@link #rotationMatrix}.<p>
     * Should only be called if {@link #haveDataNecessaryToComputeOrientation()} returns true,
     * else result may be undefined.
     * @return true if rotation was retrieved and recalculated, false otherwise.
     */
    private boolean computeRotationMatrix(){
        if(SensorManager.getRotationMatrix(rotationMatrixTemp, null, latestAccelerations, latestMagFields)){
            switch(screenRotation){
                case Surface.ROTATION_0:
                    SensorManager.remapCoordinateSystem(rotationMatrixTemp,
                            SensorManager.AXIS_X, SensorManager.AXIS_Y, rotationMatrix);
                    break;
                case Surface.ROTATION_90:
                    SensorManager.remapCoordinateSystem(rotationMatrixTemp,
                            SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, rotationMatrix);
                    break;
                case Surface.ROTATION_180:
                    SensorManager.remapCoordinateSystem(rotationMatrixTemp,
                            SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y, rotationMatrix);
                    break;
                case Surface.ROTATION_270:
                    SensorManager.remapCoordinateSystem(rotationMatrixTemp,
                            SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X, rotationMatrix);
                    break;
            }
            return true;
        }
        return false;
    }

    /**
     * Computes the latest orientation and stores it in {@link #orientation}.
     * Also updates {@link #latestYaw}, {@link #latestPitch} and {@link #latestRoll}
     * as filtered versions of {@link #orientation}.
     */
    private void computeOrientation(){
        synchronized(rotationMatrix){
            if(computeRotationMatrix()){
                switch(orientationMode){
                    case ABSOLUTE:
                        // get absolute yaw / pitch / roll
                        SensorManager.getOrientation(rotationMatrix, orientation);
                        break;
                    case RELATIVE:
                        if(!haveOrigin){
                            updateOrigin();
                        }

                        // get yaw / pitch / roll relative to original rotation
                        SensorManager.getAngleChange(orientation, rotationMatrix, rotationMatrixOrigin);
                        break;
                }
				
				/* [0] : yaw, rotation around z axis
				 * [1] : pitch, rotation around x axis
				 * [2] : roll, rotation around y axis */
                final float yaw = orientation[0] * DEGREES_PER_RADIAN;
                final float pitch = orientation[1] * DEGREES_PER_RADIAN;
                final float roll = orientation[2] * DEGREES_PER_RADIAN;

                latestYaw = yawFilter.push(yaw);
                latestPitch = pitchFilter.push(pitch);
                latestRoll = rollFilter.push(roll);

                if(null != orientationListener) orientationListener.onNewOrientation(this);

                // redraw image
                invalidate();
            }
        }
    }

    public void setOnNewOrientationListener(OnNewOrientationListener orientationListener){
        this.orientationListener = orientationListener;
    }

    /**
     * @return the latest smoothed yaw value. The basis is defined by the current
     * {@link #getOrientationMode()}.
     */
    public float getLatestYaw(){
        return latestYaw;
    }
    /**
     * @return the latest smoothed pitch value. The basis is defined by the current
     * {@link #getOrientationMode()}.
     */
    public float getLatestPitch(){
        return latestPitch;
    }
    /**
     * @return the latest smoothed roll value. The basis is defined by the current
     * {@link #getOrientationMode()}.
     */
    public float getLatestRoll(){
        return latestRoll;
    }

    /**
     * Manually resets the orientation origin. Has no effect unless {@link #getOrientationMode()}
     * is {@link com.jmedeisis.windowview.WindowView.OrientationMode#RELATIVE}.
     */
    public boolean resetOrientationOrigin(){
        if(haveDataNecessaryToComputeOrientation()){
            synchronized(rotationMatrix){
                if(computeRotationMatrix()){
                    updateOrigin();
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Resets the internal orientation origin matrix.
     * {@link #computeRotationMatrix()} must have been called prior.
     */
    private void updateOrigin(){
        System.arraycopy(rotationMatrix, 0, rotationMatrixOrigin, 0, 9);
        haveOrigin = true;
    }

    /**
     * Determines the mapping of orientation to image offset.
     * See {@link com.jmedeisis.windowview.WindowView.OrientationMode}.
     */
    public void setOrientationMode(OrientationMode orientationMode){
        this.orientationMode = orientationMode;
        haveOrigin = false;
    }

    public OrientationMode getOrientationMode(){
        return orientationMode;
    }

    /** Ring buffer low-pass filter. */
    private class Filter {
        float[] buffer;
        float sum;
        int lastIndex;
        /** 0-1*/
        float factor;

        public Filter(int samples, float factor, float initialValue){
            buffer = new float[samples];
            this.factor = factor;
            lastIndex = 0;
            reset(initialValue);
        }

        public void reset(float value){
            sum = value * buffer.length;
            for(int i = 0; i < buffer.length; i++){
                buffer[i] = value;
            }
        }

        /**
         * Pushes new sample to filter.
         * @return new smoothed value.
         */
        public float push(float value){
            // do low-pass
            value = buffer[lastIndex] + factor * (value - buffer[lastIndex]);

            // subtract oldest sample
            sum -= buffer[lastIndex];
            // add new sample
            sum += value;
            buffer[lastIndex] = value;

            // advance index
            lastIndex = lastIndex >= buffer.length - 1? 0 : lastIndex + 1;

            return get();
        }

        /** @return smoothed value. */
        public float get(){
            return sum / buffer.length;
        }
    }

    /*
     * DEBUG
     * ---------------------------------------------------------------------------------------------
     */
    /**
     * Enables/disables on-screen debug information.
     * @param debugTilt if true, displays on-screen information about the current tilt values and limits.
     * @param debugImage if true, displays on-screen information about the source image and dimensions.
     */
    public void setDebugEnabled(boolean debugTilt, boolean debugImage){
        this.debugTilt = debugTilt;
        this.debugImage = debugImage;
    }
}
