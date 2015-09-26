package com.jmedeisis.windowview;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.widget.ImageView;

import com.jmedeisis.windowview.sensor.TiltSensor;

/**
 * An ImageView that automatically pans in response to device tilt.
 * Currently only supports {@link android.widget.ImageView.ScaleType#CENTER_CROP}.
 */
public class WindowView extends ImageView implements TiltSensor.TiltListener {
    private static final String LOG_TAG = "WindowView";

    private float latestYaw;
    private float latestPitch;
    private float latestRoll;

    private TiltSensor sensor;

    private static final float DEFAULT_MAX_PITCH = 30;
    private static final float DEFAULT_MAX_ROLL = 30;
    private static final float DEFAULT_HORIZONTAL_ORIGIN = 0;
    private static final float DEFAULT_VERTICAL_ORIGIN = 0;
    private float maxPitch;
    private float maxRoll;
    private float horizontalOrigin;
    private float verticalOrigin;

    /** Determines the basis in which device orientation is measured. */
    public enum OrientationMode {
        /** Measures absolute yaw / pitch / roll (i.e. relative to the world). */
        ABSOLUTE,
        /**
         * Measures yaw / pitch / roll relative to the starting orientation.
         * The starting orientation is determined upon receiving the first sensor data,
         * but can be manually reset at any time using {@link #resetOrientationOrigin(boolean)}.
         */
        RELATIVE
    }
    private static final OrientationMode DEFAULT_ORIENTATION_MODE = OrientationMode.RELATIVE;
    private OrientationMode orientationMode;

    /** Determines the relationship between change in device tilt and change in image translation. */
    public enum TranslateMode {
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

        sensor = new TiltSensor(context, orientationMode == OrientationMode.RELATIVE);
        sensor.addListener(this);

        debugTextPaint = new Paint();
        debugTextPaint.setColor(Color.MAGENTA);
        debugTextPaint.setTextSize(DEBUG_TEXT_SIZE);
        debugTextPaint.setTypeface(Typeface.MONOSPACE);

        setScaleType(ScaleType.CENTER_CROP);
    }

    /*
     * LIFE-CYCLE
     * Registering for sensor events should be tied to Activity / Fragment lifecycle events.
     * However, this would mean that WindowView cannot be independent. We tie into a few
     * lifecycle-esque View events that allow us to make WindowView completely independent.
     *
     * Un-registering from sensor events is done aggressively to minimise battery drain and
     * performance impact.
     * ---------------------------------------------------------------------------------------------
     */
    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus){
        super.onWindowFocusChanged(hasWindowFocus);
        if(DEBUG_LIFECYCLE) Log.d(LOG_TAG, "onWindowFocusChanged(), hasWindowFocus: " + hasWindowFocus);
        if(hasWindowFocus){
            sensor.startTracking();
        } else {
            sensor.stopTracking();
        }
    }

    @Override
    protected void onAttachedToWindow(){
        super.onAttachedToWindow();
        if(DEBUG_LIFECYCLE) Log.d(LOG_TAG, "onAttachedToWindow()");
        sensor.startTracking();
    }

    @Override
    protected void onDetachedFromWindow(){
        super.onDetachedFromWindow();
        if(DEBUG_LIFECYCLE) Log.d(LOG_TAG, "onDetachedFromWindow()");
        sensor.stopTracking();
    }

    /*
     * DRAWING & LAYOUT
     * ---------------------------------------------------------------------------------------------
     */
    @SuppressWarnings("UnusedAssignment")
    @Override
    protected void onDraw(@NonNull Canvas canvas){
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

            /*if(haveOrigin){
                SensorManager.getOrientation(rotationMatrixOrigin, orientationOrigin);
                debugText(canvas, i++, "org yaw   " + orientationOrigin[0]*DEGREES_PER_RADIAN);
                debugText(canvas, i++, "org pitch " + orientationOrigin[1]*DEGREES_PER_RADIAN);
                debugText(canvas, i++, "org roll  " + orientationOrigin[2]*DEGREES_PER_RADIAN);
            }*/

            debugText(canvas, i++, "yaw   " + latestYaw);
            debugText(canvas, i++, "pitch " + latestPitch);
            debugText(canvas, i++, "roll  " + latestRoll);

            debugText(canvas, i++, "MAX_PITCH " + maxPitch);
            debugText(canvas, i++, "MAX_ROLL  " + maxRoll);

            debugText(canvas, i++, "HOR ORIGIN " + horizontalOrigin);
            debugText(canvas, i++, "VER ORIGIN " + verticalOrigin);

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
     * SENSOR DATA
     * ---------------------------------------------------------------------------------------------
     */
    @Override
    public void onTiltUpdate(float yaw, float pitch, float roll) {
        this.latestYaw = yaw;
        this.latestPitch = pitch;
        this.latestRoll = roll;
        invalidate();
    }

    public void addTiltListener(TiltSensor.TiltListener listener){
        sensor.addListener(listener);
    }

    public void removeTiltListener(TiltSensor.TiltListener listener){
        sensor.removeListener(listener);
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
     *
     * @param immediate if false, the sensor values smoothly interpolate to the new origin.
     */
    public void resetOrientationOrigin(boolean immediate){
        sensor.resetOrigin(immediate);
    }

    /**
     * Determines the mapping of orientation to image offset.
     * See {@link com.jmedeisis.windowview.WindowView.OrientationMode}.
     */
    public void setOrientationMode(OrientationMode orientationMode){
        this.orientationMode = orientationMode;
        sensor.setTrackRelativeOrientation(orientationMode == OrientationMode.RELATIVE);
        sensor.resetOrigin(true);
    }

    public OrientationMode getOrientationMode(){
        return orientationMode;
    }

    /** Ring buffer low-pass filter. */
    public static class Filter {
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

        /**
         * @param factor 0-1. Calculated as dt / (t + dt), where t is the system's time constant and dt
         *               is the sampling period, i.e. the rate that new values are delivered via
         *               {@link #push(float)}.
         *               The closer to 0, the greater the inertia, i.e. the filter responds more slowly
         *               to new input values.
         */
        public void setFactor(float factor){
            this.factor = factor;
        }

        /**
         * @param samples > 0.
         *                The larger the number of samples N, the greater the inertia, i.e. the
         *                filter responds more slowly to new input values.
         */
        public void setSampleCount(int samples){
            float initialValue = get();
            buffer = new float[samples];
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
