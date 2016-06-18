package com.jmedeisis.windowview.sensor;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Interprets sensor data to calculate device tilt in terms of yaw, pitch and roll.
 * Requires one of the following sensor combinations to be accessible via {@link SensorManager}:
 * <ul>
 * <li>TYPE_ROTATION_VECTOR</li>
 * <li>TYPE_MAGNETIC_FIELD + TYPE_GRAVITY</li>
 * <li>TYPE_MAGNETIC_FIELD + TYPE_ACCELEROMETER</li>
 * </ul>
 */
public class TiltSensor implements SensorEventListener {
    // 1 radian = 180 / PI = 57.2957795 degrees
    private static final float DEGREES_PER_RADIAN = 57.2957795f;

    private final SensorManager sensorManager;

    private boolean tracking;

    /**
     * @see {@link Display#getRotation()}.
     */
    private final int screenRotation;

    private boolean relativeTilt;

    /**
     * Interface for callback to be invoked when new orientation values are available.
     */
    public interface TiltListener {
        /**
         * Euler angles defined as per {@link SensorManager#getOrientation(float[], float[])}.
         * <p>
         * All three are in <b>radians</b> and <b>positive</b> in the <b>counter-clockwise</b>
         * direction.
         *
         * @param yaw   rotation around -Z axis. -PI to PI.
         * @param pitch rotation around -X axis. -PI/2 to PI/2.
         * @param roll  rotation around Y axis. -PI to PI.
         */
        void onTiltUpdate(float yaw, float pitch, float roll);
    }

    private List<TiltListener> listeners;

    private final float[] rotationMatrix = new float[9];
    private final float[] rotationMatrixTemp = new float[9];
    private final float[] rotationMatrixOrigin = new float[9];
    /**
     * [w, x, y, z]
     */
    private final float[] latestQuaternion = new float[4];
    /**
     * [w, x, y, z]
     */
    private final float[] invQuaternionOrigin = new float[4];
    /**
     * [w, x, y, z]
     */
    private final float[] rotationQuaternion = new float[4];
    private final float[] latestAccelerations = new float[3];
    private final float[] latestMagFields = new float[3];
    private final float[] orientation = new float[3];
    private boolean haveGravData = false;
    private boolean haveAccelData = false;
    private boolean haveMagData = false;
    private boolean haveRotOrigin = false;
    private boolean haveQuatOrigin = false;
    private boolean haveRotVecData = false;

    private Filter yawFilter;
    private Filter pitchFilter;
    private Filter rollFilter;

    /**
     * See {@link ExponentialSmoothingFilter#setSmoothingFactor(float)}.
     */
    private static final float SMOOTHING_FACTOR_HIGH_ACC = 0.8f;
    private static final float SMOOTHING_FACTOR_LOW_ACC = 0.05f;

    public TiltSensor(Context context, boolean trackRelativeOrientation) {
        listeners = new ArrayList<>();

        initialiseDefaultFilters(SMOOTHING_FACTOR_LOW_ACC);

        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        tracking = false;

        screenRotation = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay().getRotation();

        this.relativeTilt = trackRelativeOrientation;
    }

    /**
     * Registers for motion sensor events.
     * Do this to begin receiving {@link TiltListener#onTiltUpdate(float, float, float)} callbacks.
     * <p>
     * <b>You must call {@link #stopTracking()} to unregister when tilt updates are no longer
     * needed.</b>
     *
     * @param samplingPeriodUs see {@link SensorManager#registerListener(SensorEventListener, Sensor, int)}
     */
    public void startTracking(int samplingPeriodUs) {
        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR), samplingPeriodUs);
        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), samplingPeriodUs);
        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY), samplingPeriodUs);
        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), samplingPeriodUs);
        tracking = true;
    }

    public boolean isTracking() {
        return tracking;
    }

    /**
     * Unregisters from motion sensor events.
     */
    public void stopTracking() {
        sensorManager.unregisterListener(this);
        if (null != yawFilter) yawFilter.reset(0);
        if (null != pitchFilter) pitchFilter.reset(0);
        if (null != rollFilter) rollFilter.reset(0);
        tracking = false;
    }

    public void addListener(TiltListener listener) {
        listeners.add(listener);
    }

    public void removeListener(TiltListener listener) {
        listeners.remove(listener);
    }

    public void setTrackRelativeOrientation(boolean trackRelative) {
        this.relativeTilt = trackRelative;
    }

    /**
     * @see Display#getRotation()
     */
    public int getScreenRotation() {
        return screenRotation;
    }

    /**
     * @param factor see {@link ExponentialSmoothingFilter#setSmoothingFactor(float)}
     */
    private void initialiseDefaultFilters(float factor) {
        yawFilter = new ExponentialSmoothingFilter(factor, null == yawFilter ? 0 : yawFilter.get());
        pitchFilter = new ExponentialSmoothingFilter(factor, null == pitchFilter ? 0 : pitchFilter.get());
        rollFilter = new ExponentialSmoothingFilter(factor, null == rollFilter ? 0 : rollFilter.get());
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ROTATION_VECTOR:
                SensorManager.getQuaternionFromVector(latestQuaternion, event.values);
                if (!haveRotVecData) {
                    initialiseDefaultFilters(SMOOTHING_FACTOR_HIGH_ACC);
                }
                haveRotVecData = true;
                break;
            case Sensor.TYPE_GRAVITY:
                if (haveRotVecData) {
                    // rotation vector sensor data is better
                    sensorManager.unregisterListener(this,
                            sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY));
                    break;
                }
                System.arraycopy(event.values, 0, latestAccelerations, 0, 3);
                haveGravData = true;
                break;
            case Sensor.TYPE_ACCELEROMETER:
                if (haveGravData || haveRotVecData) {
                    // rotation vector / gravity sensor data is better!
                    // let's not listen to the accelerometer anymore
                    sensorManager.unregisterListener(this,
                            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER));
                    break;
                }
                System.arraycopy(event.values, 0, latestAccelerations, 0, 3);
                haveAccelData = true;
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                if (haveRotVecData) {
                    // rotation vector sensor data is better
                    sensorManager.unregisterListener(this,
                            sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD));
                    break;
                }
                System.arraycopy(event.values, 0, latestMagFields, 0, 3);
                haveMagData = true;
                break;
        }

        if (haveDataNecessaryToComputeOrientation()) {
            computeOrientation();
        }
    }

    /**
     * After {@link #startTracking(int)} has been called and sensor data has been received,
     * this method returns the sensor type chosen for orientation calculations.
     *
     * @return one of {@link Sensor#TYPE_ROTATION_VECTOR}, {@link Sensor#TYPE_GRAVITY},
     * {@link Sensor#TYPE_ACCELEROMETER} or 0 if none of the previous are available or
     * {@link #startTracking(int)} has not yet been called.
     */
    public int getChosenSensorType() {
        if (haveRotVecData) return Sensor.TYPE_ROTATION_VECTOR;
        if (haveGravData) return Sensor.TYPE_GRAVITY;
        if (haveAccelData) return Sensor.TYPE_ACCELEROMETER;
        return 0;
    }

    /**
     * @return true if both {@link #latestAccelerations} and {@link #latestMagFields} have valid values.
     */
    private boolean haveDataNecessaryToComputeOrientation() {
        return haveRotVecData || ((haveGravData || haveAccelData) && haveMagData);
    }

    /**
     * Computes the latest rotation, remaps it according to the current {@link #screenRotation},
     * and stores it in {@link #rotationMatrix}.
     * <p>
     * Should only be called if {@link #haveDataNecessaryToComputeOrientation()} returns true and
     * {@link #haveRotVecData} is false, else result may be undefined.
     *
     * @return true if rotation was retrieved and recalculated, false otherwise.
     */
    private boolean computeRotationMatrix() {
        if (SensorManager.getRotationMatrix(rotationMatrixTemp, null, latestAccelerations, latestMagFields)) {
            switch (screenRotation) {
                case Surface.ROTATION_0:
                    SensorManager.remapCoordinateSystem(rotationMatrixTemp,
                            SensorManager.AXIS_X, SensorManager.AXIS_Y, rotationMatrix);
                    break;
                case Surface.ROTATION_90:
                    //noinspection SuspiciousNameCombination
                    SensorManager.remapCoordinateSystem(rotationMatrixTemp,
                            SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, rotationMatrix);
                    break;
                case Surface.ROTATION_180:
                    SensorManager.remapCoordinateSystem(rotationMatrixTemp,
                            SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y, rotationMatrix);
                    break;
                case Surface.ROTATION_270:
                    //noinspection SuspiciousNameCombination
                    SensorManager.remapCoordinateSystem(rotationMatrixTemp,
                            SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X, rotationMatrix);
                    break;
            }
            return true;
        }
        return false;
    }

    /**
     * Computes the latest orientation and notifies any {@link TiltListener}s.
     */
    private void computeOrientation() {
        boolean updated = false;
        float yaw = 0;
        float pitch = 0;
        float roll = 0;

        if (haveRotVecData) {
            remapQuaternionToScreenRotation(latestQuaternion, screenRotation);
            if (relativeTilt) {
                if (!haveQuatOrigin) {
                    System.arraycopy(latestQuaternion, 0, invQuaternionOrigin, 0, 4);
                    invertQuaternion(invQuaternionOrigin);
                    haveQuatOrigin = true;
                }
                multQuaternions(rotationQuaternion, invQuaternionOrigin, latestQuaternion);
            } else {
                System.arraycopy(latestQuaternion, 0, rotationQuaternion, 0, 4);
            }

            // https://en.wikipedia.org/wiki/Conversion_between_quaternions_and_Euler_angles
            final float q0 = rotationQuaternion[0]; // w
            final float q1 = rotationQuaternion[1]; // x
            final float q2 = rotationQuaternion[2]; // y
            final float q3 = rotationQuaternion[3]; // z

            float rotXRad = (float) Math.atan2(2 * (q0 * q1 + q2 * q3), 1 - 2 * (q1 * q1 + q2 * q2));
            float rotYRad = (float) Math.asin(2 * (q0 * q2 - q3 * q1));
            float rotZRad = (float) Math.atan2(2 * (q0 * q3 + q1 * q2), 1 - 2 * (q2 * q2 + q3 * q3));

            // constructed to match output of SensorManager#getOrientation
            yaw = -rotZRad * DEGREES_PER_RADIAN;
            pitch = -rotXRad * DEGREES_PER_RADIAN;
            roll = rotYRad * DEGREES_PER_RADIAN;
            updated = true;
        } else if (computeRotationMatrix()) {
            if (relativeTilt) {
                if (!haveRotOrigin) {
                    System.arraycopy(rotationMatrix, 0, rotationMatrixOrigin, 0, 9);
                    haveRotOrigin = true;
                }
                // get yaw / pitch / roll relative to original rotation
                SensorManager.getAngleChange(orientation, rotationMatrix, rotationMatrixOrigin);
            } else {
                // get absolute yaw / pitch / roll
                SensorManager.getOrientation(rotationMatrix, orientation);
            }
            /*
             * [0] : yaw, rotation around -z axis
             * [1] : pitch, rotation around -x axis
             * [2] : roll, rotation around y axis
             */
            yaw = orientation[0] * DEGREES_PER_RADIAN;
            pitch = orientation[1] * DEGREES_PER_RADIAN;
            roll = orientation[2] * DEGREES_PER_RADIAN;
            updated = true;
        }

        if (!updated) return;


        if (null != yawFilter) yaw = yawFilter.push(yaw);
        if (null != pitchFilter) pitch = pitchFilter.push(pitch);
        if (null != rollFilter) roll = rollFilter.push(roll);

        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).onTiltUpdate(yaw, pitch, roll);
        }
    }

    /**
     * @param immediate if true, any sensor data filters are reset to new origin immediately.
     *                  If false, values transition smoothly to new origin.
     */
    public void resetOrigin(boolean immediate) {
        haveRotOrigin = false;
        haveQuatOrigin = false;
        if (immediate) {
            if (null != yawFilter) yawFilter.reset(0);
            if (null != pitchFilter) pitchFilter.reset(0);
            if (null != rollFilter) rollFilter.reset(0);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    /**
     * Please drop me a PM if you know of a more elegant way to accomplish this - Justas
     *
     * @param q              [w, x, y, z]
     * @param screenRotation see {@link Display#getRotation()}
     */
    private static void remapQuaternionToScreenRotation(float[] q, int screenRotation) {
        final float x = q[1];
        final float y = q[2];
        switch (screenRotation) {
            case Surface.ROTATION_0:
                break;
            case Surface.ROTATION_90:
                q[1] = -y;
                q[2] = x;
                break;
            case Surface.ROTATION_180:
                q[1] = -x;
                q[2] = -y;
                break;
            case Surface.ROTATION_270:
                q[1] = y;
                q[2] = -x;
                break;
        }
    }

    /**
     * @param qOut [w, x, y, z] result.
     * @param q1   [w, x, y, z] left.
     * @param q2   [w, x, y, z] right.
     */
    private static void multQuaternions(float[] qOut, float[] q1, float[] q2) {
        // multiply quaternions
        final float a = q1[0];
        final float b = q1[1];
        final float c = q1[2];
        final float d = q1[3];

        final float e = q2[0];
        final float f = q2[1];
        final float g = q2[2];
        final float h = q2[3];

        qOut[0] = a * e - b * f - c * g - d * h;
        qOut[1] = b * e + a * f + c * h - d * g;
        qOut[2] = a * g - b * h + c * e + d * f;
        qOut[3] = a * h + b * g - c * f + d * e;
    }

    /**
     * @param q [w, x, y, z]
     */
    private static void invertQuaternion(float[] q) {
        for (int i = 1; i < 4; i++) {
            q[i] = -q[i]; // invert quaternion
        }
    }
}