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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_demo);

        if(null != savedInstanceState && savedInstanceState.containsKey(ORIENTATION)){
            //noinspection ResourceType
            setRequestedOrientation(savedInstanceState.getInt(ORIENTATION));
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT); // default
        }

        final WindowView windowView1 = (WindowView) findViewById(R.id.windowView1);
        final WindowView windowView2 = (WindowView) findViewById(R.id.windowView2);
        windowView1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                windowView1.resetOrigin();
            }
        });
        windowView2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                windowView2.resetOrigin();
            }
        });
    }

    @Override
    public void onSaveInstanceState(Bundle outState){
        super.onSaveInstanceState(outState);
        outState.putInt(ORIENTATION, getRequestedOrientation());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.demo, menu);

        menu.findItem(R.id.action_lock_portrait)
                .setChecked(getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

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
        }
        return super.onOptionsItemSelected(item);
    }
}
