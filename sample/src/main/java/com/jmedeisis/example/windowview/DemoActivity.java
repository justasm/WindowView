/*
 * Copyright 2014 Justas Medeisis
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jmedeisis.example.windowview;

import android.content.pm.ActivityInfo;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.jmedeisis.windowview.WindowView;

public class DemoActivity extends ActionBarActivity {

    private static final String ORIENTATION = "orientation";
    private static final String DEBUG_TILT = "debugTilt";
    private static final String DEBUG_IMAGE = "debugImage";
    boolean debugTilt, debugImage;
    WindowView windowView1;
    WindowView windowView2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_demo);

        windowView1 = (WindowView) findViewById(R.id.windowView1);
        windowView2 = (WindowView) findViewById(R.id.windowView2);
        windowView1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                windowView1.resetOrientationOrigin();
            }
        });
        windowView2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                windowView2.resetOrientationOrigin();
            }
        });

        if(null != savedInstanceState && savedInstanceState.containsKey(ORIENTATION)
                && savedInstanceState.containsKey(DEBUG_TILT)
                && savedInstanceState.containsKey(DEBUG_IMAGE)){
            //noinspection ResourceType
            setRequestedOrientation(savedInstanceState.getInt(ORIENTATION));
            debugTilt = savedInstanceState.getBoolean(DEBUG_TILT);
            debugImage = savedInstanceState.getBoolean(DEBUG_IMAGE);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT); // default
            debugTilt = false;
            debugImage = false;
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState){
        super.onSaveInstanceState(outState);
        outState.putInt(ORIENTATION, getRequestedOrientation());
        outState.putBoolean(DEBUG_TILT, debugTilt);
        outState.putBoolean(DEBUG_IMAGE, debugImage);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.demo, menu);

        menu.findItem(R.id.action_lock_portrait)
                .setChecked(getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        menu.findItem(R.id.action_debug_tilt).setChecked(debugTilt);
        menu.findItem(R.id.action_debug_image).setChecked(debugImage);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        switch(item.getItemId()){
            case R.id.action_lock_portrait:
                item.setChecked(!item.isChecked());
                if(item.isChecked()){
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
