package com.jmedeisis.windowview;

import android.content.Context;
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

/** An ImageView that automatically pans in response to device tilt. Currently only supports {@link android.widget.ImageView.ScaleType#CENTER_CROP}. */
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
	
	// TODO make set-able, +xml attribs
	private static final float MAX_PITCH = 30;
	private static final float MAX_ROLL = 30;
	
	private static final float HORIZONTAL_ORIGIN = 0;
	private static final float VERTICAL_ORIGIN = 0;
	
	public static enum Mode {
		/** Measures absolute yaw / pitch / roll (i.e. relative to the world). */
		ABSOLUTE,
		/** Measures yaw / pitch / roll relative to the starting orientation. */
		RELATIVE
	}
	// TODO make set-able, +xml attribs
	private Mode mode = Mode.RELATIVE;
	
	// layout
	private boolean heightMatches;
	private float widthDifference;
	private float heightDifference;
	
	// debug
	private static final boolean DEBUG_TILT = true;
	private static final boolean DEBUG_IMAGE = false;
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
		sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
		
		yawFilter = new Filter(NUM_FILTER_SAMPLES, LOW_PASS_COEFF, 0);
		pitchFilter = new Filter(NUM_FILTER_SAMPLES, LOW_PASS_COEFF, 0);
		rollFilter = new Filter(NUM_FILTER_SAMPLES, LOW_PASS_COEFF, 0);
		
		screenRotation = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
		
		debugTextPaint = new Paint();
		debugTextPaint.setColor(Color.MAGENTA);
		debugTextPaint.setTextSize(DEBUG_TEXT_SIZE);
		debugTextPaint.setTypeface(Typeface.MONOSPACE);
		
		setScaleType(ScaleType.CENTER_CROP);
	}
	
	/*
	 * LIFE-CYCLE
	 * -----------------------------------------------------------------------------------------------------------------------------------
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
		sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), SensorManager.SENSOR_DELAY_GAME);
		sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY), SensorManager.SENSOR_DELAY_GAME);
		sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME);
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
	 * -----------------------------------------------------------------------------------------------------------------------------------
	 */
	@SuppressWarnings("UnusedAssignment")
    @Override
	protected void onDraw(Canvas canvas){
		// -1 -> 1
		float translateX = 0f;
		float translateY = 0f;
		if(heightMatches){
			// only let user tilt horizontally
			translateX = (-HORIZONTAL_ORIGIN + clampAbsoluteFloating(HORIZONTAL_ORIGIN, latestRoll, MAX_ROLL)) / MAX_ROLL;
		} else {
			// only let user tilt vertically
			translateY = (VERTICAL_ORIGIN - clampAbsoluteFloating(VERTICAL_ORIGIN, latestPitch, MAX_PITCH)) / MAX_PITCH;
		}
		canvas.save();
		canvas.translate(Math.round((widthDifference / 2) * translateX), Math.round((heightDifference / 2) * translateY));
		super.onDraw(canvas);
		canvas.restore();
		
		// TODO DEBUG
		int i = 0;
		if(DEBUG_IMAGE){
			debugText(canvas, i++, "width      " + getWidth());
			debugText(canvas, i++, "height     " + getHeight());
			debugText(canvas, i++, "img width  " + getScaledImageWidth());
			debugText(canvas, i++, "img height " + getScaledImageHeight());
			
			debugText(canvas, i++, "tx " + translateX);
			debugText(canvas, i++, "ty " + translateY);
			debugText(canvas, i++, "height matches " + heightMatches);
		}
		
		if(DEBUG_TILT){
			debugText(canvas, i++, mode + " mode");
			
			if(haveOrigin){
				SensorManager.getOrientation(rotationMatrixOrigin, orientationOrigin);
				debugText(canvas, i++, "org yaw   " + orientationOrigin[0]*DEGREES_PER_RADIAN);
				debugText(canvas, i++, "org pitch " + orientationOrigin[1]*DEGREES_PER_RADIAN);
				debugText(canvas, i++, "org roll  " + orientationOrigin[2]*DEGREES_PER_RADIAN);
			}
			
			debugText(canvas, i++, "yaw   " + latestYaw);
			debugText(canvas, i++, "pitch " + latestPitch);
			debugText(canvas, i++, "roll  " + latestRoll);
			
			debugText(canvas, i++, "MAX_PITCH " + MAX_PITCH);
			debugText(canvas, i++, "MAX_ROLL  " + MAX_ROLL);
			
			debugText(canvas, i++, "HOR ORIGIN " + HORIZONTAL_ORIGIN);
			debugText(canvas, i++, "VER ORIGIN " + VERTICAL_ORIGIN);
			
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
		return value < origin ? Math.max(value, origin - maxAbsolute) : Math.min(value, origin + maxAbsolute);
	}
	
	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh){
		heightMatches = !widthRatioGreater(w, h, getDrawable().getIntrinsicWidth(), getDrawable().getIntrinsicHeight());
		widthDifference = getScaledImageWidth() - getWidth();
		heightDifference = getScaledImageHeight() - getHeight();
	}
	
	private static boolean widthRatioGreater(float width, float height, float otherWidth, float otherHeight){
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
			throw new IllegalArgumentException("Image scale type " + scaleType + " is not supported by WindowView. Use CENTER_CROP instead.");
		super.setScaleType(scaleType);
	}
	
	/*
	 * SENSOR DATA ACQUISITION / PROCESSING
	 * -----------------------------------------------------------------------------------------------------------------------------------
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
                sensorManager.unregisterListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER));
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
				SensorManager.remapCoordinateSystem(rotationMatrixTemp, SensorManager.AXIS_X, SensorManager.AXIS_Y, rotationMatrix);
				break;
			case Surface.ROTATION_90:
				SensorManager.remapCoordinateSystem(rotationMatrixTemp, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, rotationMatrix);
				break;
			case Surface.ROTATION_180:
				SensorManager.remapCoordinateSystem(rotationMatrixTemp, SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y, rotationMatrix);
				break;
			case Surface.ROTATION_270:
				SensorManager.remapCoordinateSystem(rotationMatrixTemp, SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X, rotationMatrix);
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
				switch(mode){
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
				
				// redraw image
				invalidate();
			}
		}
	}
	
	/** Manually resets the orientation origin. Has no effect unless the mode is {@link com.jmedeisis.windowview.WindowView.Mode#RELATIVE}. */
	public boolean resetOrigin(){
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
	
	/** Resets the internal orientation origin matrix. {@link #computeRotationMatrix()} must have been called prior. */
	private void updateOrigin(){
		System.arraycopy(rotationMatrix, 0, rotationMatrixOrigin, 0, 9);
		haveOrigin = true;
	}
	
	/** Determines the mapping of orientation to image offset. See {@link com.jmedeisis.windowview.WindowView.Mode}. */
	public void setMode(Mode mode){
		this.mode = mode;
		
		haveOrigin = false;
		// TODO reset other parameters? test
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
}
