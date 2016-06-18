package com.example.windowviewdebug;

import android.annotation.TargetApi;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.view.GravityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.jmedeisis.windowview.sensor.TiltSensor;

public class DebugActivity extends AppCompatActivity {

    private static final String ORIENTATION = "orientation";
    private static final String DEBUG_TILT = "debugTilt";
    private static final String DEBUG_IMAGE = "debugImage";
    private boolean debugTilt;
    private boolean debugImage;
    private DebugWindowView windowView1;
    private DebugWindowView windowView2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debug);

        windowView1 = (DebugWindowView) findViewById(R.id.windowView1);
        windowView2 = (DebugWindowView) findViewById(R.id.windowView2);
        /*
         * For sake of demo interface simplicity & compass visualisation, tapping on either
         * WindowView resets both of their orientation origins.
         * Typically, this would be done individually.
         */
        View.OnClickListener onClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetWindowViewOrientationOrigins();
            }
        };
        windowView1.setOnClickListener(onClickListener);
        windowView2.setOnClickListener(onClickListener);

        if (null != savedInstanceState && savedInstanceState.containsKey(ORIENTATION)
                && savedInstanceState.containsKey(DEBUG_TILT)
                && savedInstanceState.containsKey(DEBUG_IMAGE)) {
            //noinspection ResourceType
            setRequestedOrientation(savedInstanceState.getInt(ORIENTATION));
            debugTilt = savedInstanceState.getBoolean(DEBUG_TILT);
            debugImage = savedInstanceState.getBoolean(DEBUG_IMAGE);

            windowView1.setDebugEnabled(debugTilt, debugImage);
            windowView2.setDebugEnabled(debugTilt, debugImage);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT); // default
            debugTilt = false;
            debugImage = false;
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(ORIENTATION, getRequestedOrientation());
        outState.putBoolean(DEBUG_TILT, debugTilt);
        outState.putBoolean(DEBUG_IMAGE, debugImage);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_debug, menu);

        menu.findItem(R.id.action_lock_portrait)
                .setChecked(getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        menu.findItem(R.id.action_debug_tilt).setChecked(debugTilt);
        menu.findItem(R.id.action_debug_image).setChecked(debugImage);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            final View actionView = View.inflate(this, R.layout.device_compass, null);
            final View xy = actionView.findViewById(R.id.compass_xy);
            final View z = actionView.findViewById(R.id.compass_z);
            windowView1.addTiltListener(new TiltSensor.TiltListener() {
                @Override
                public void onTiltUpdate(float yaw, float pitch, float roll) {
                    xy.setRotation(yaw);
                    xy.setRotationX(pitch);
                    xy.setRotationY(roll);

                    z.setRotation(yaw);
                    z.setRotationX(pitch);
                    z.setRotationY(roll - 90);
                }
            });
            actionView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    resetWindowViewOrientationOrigins();
                }
            });
            actionView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    Toast t = Toast.makeText(DebugActivity.this,
                            R.string.action_reset_orientation, Toast.LENGTH_SHORT);
                    int[] pos = new int[2];
                    actionView.getLocationInWindow(pos);
                    t.setGravity(Gravity.TOP | GravityCompat.START,
                            pos[0], pos[1] + actionView.getHeight() / 2);
                    t.show();
                    return true;
                }
            });


            menu.findItem(R.id.action_reset_orientation).setActionView(actionView);
        }

        return true;
    }

    private void resetWindowViewOrientationOrigins() {
        windowView1.resetOrientationOrigin(false);
        windowView2.resetOrientationOrigin(false);
        Toast.makeText(DebugActivity.this, R.string.hint_orientation_reset, Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_lock_portrait:
                item.setChecked(!item.isChecked());
                if (item.isChecked()) {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                } else {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
                }
                return true;
            case R.id.action_debug_tilt:
                item.setChecked(!item.isChecked());
                debugTilt = item.isChecked();
                windowView1.setDebugEnabled(debugTilt, debugImage);
                windowView2.setDebugEnabled(debugTilt, debugImage);
                return true;
            case R.id.action_debug_image:
                item.setChecked(!item.isChecked());
                debugImage = item.isChecked();
                windowView1.setDebugEnabled(debugTilt, debugImage);
                windowView2.setDebugEnabled(debugTilt, debugImage);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
