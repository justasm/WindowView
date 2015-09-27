package com.example.windowview;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import com.jmedeisis.windowview.WindowView;

public class DemoActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_demo);

        // re-center of WindowView tilt sensors on tap
        final WindowView windowView1 = (WindowView) findViewById(R.id.windowView1);
        windowView1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                windowView1.resetOrientationOrigin(false);
            }
        });

        final WindowView windowView2 = (WindowView) findViewById(R.id.windowView2);
        windowView2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                windowView2.resetOrientationOrigin(false);
            }
        });
    }

}
