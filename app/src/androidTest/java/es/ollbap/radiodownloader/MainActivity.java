/*
* Copyright 2013 The Android Open Source Project
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


package es.ollbap.radiodownloader;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.Menu;
import android.view.View;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentTransaction;

/**
 * A simple launcher activity containing a summary sample description
 * and a few action bar buttons.
 */
public class MainActivity extends FragmentActivity {
    public static final String URL = "http://19093.live.streamtheworld.com:80/CADENASER_SC";

    public static final String TAG = "MainActivity";

    public static final String FRAGTAG = "RepeatingAlarmFragment";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.initializeApp(this);

        setContentView(R.layout.activity_main_legacy);

        if (getSupportFragmentManager().findFragmentByTag(FRAGTAG) == null ) {
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            RepeatingAlarmFragment fragment = new RepeatingAlarmFragment();
            transaction.add(fragment, FRAGTAG);
            transaction.commit();
        }

        TextView textView = findViewById(R.id.sample_output);
        textView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                updateProgress();
            }
        });
    }


    @Override
    protected void onResume() {
        super.onResume();
        requestPermissions();
        updateProgress();
    }

    private void requestPermissions() {
        int p = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (p != PackageManager.PERMISSION_GRANTED) {
            int requestCode = 123;
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    requestCode);
        }
    }

    private void updateProgress() {
        TextView textView = findViewById(R.id.sample_output);
        StringBuilder sb = new StringBuilder();

        sb.append("[L-V]   ");
        sb.append(String.format("%02d:%02d", Configuration.ALARM_WEEKDAY_HOUR, Configuration.ALARM_WEEKDAY_MINUTE));
        sb.append("\n");

        sb.append("[S-D]   ");
        sb.append(String.format("%02d:%02d", Configuration.ALARM_WEEKEND_HOUR, Configuration.ALARM_WEEKEND_MINUTE));
        sb.append("\n");

        sb.append(Util.getDownloadTaskProgress());
        sb.append("\n");

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        boolean ignoringBatteryOptimization = pm.isIgnoringBatteryOptimizations("com.example.android.repeatingalarm");

        if (!ignoringBatteryOptimization) {
            sb.append("Not ignoring battery optimization!!!!!!\n");
        }

        textView.setText(sb.toString());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

}
