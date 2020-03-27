package es.ollbap.radiodownloader.gui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.os.PowerManager;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import java.util.Timer;
import java.util.TimerTask;

import es.ollbap.radiodownloader.service.DownloadTask;
import es.ollbap.radiodownloader.R;
import es.ollbap.radiodownloader.util.Time;
import es.ollbap.radiodownloader.util.Util;

import static es.ollbap.radiodownloader.util.Util.isActiveNetworkMetered;
import static es.ollbap.radiodownloader.util.Util.logI;

public class MainActivity extends AppCompatActivity {

    private Timer autoUpdate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if(id == R.id.start_download) {
            Util.startForegroundService(this);
            logI("Download started.");
        }

        if(id == R.id.cancel_download) {
            Util.stopForegroundService(this);
            logI("Cancel download.");
        }

        if(id == R.id.open_radio_file) {
            Util.openRadioFile(this);
            logI("Cancel download.");
        }

        if(id == R.id.open_log_file) {
            Util.openLogFile(this);
            logI("Cancel download.");
        }

        if(id == R.id.open_vlc) {
            Util.playInVlc(this);
            logI("Cancel download.");
        }

        if(id == R.id.toggle_metered) {
            DownloadTask lastInstance = DownloadTask.getLastInstance();
            if (lastInstance != null) {
                lastInstance.toggleMetered();
                logI("toggled metered restrictions");
            }
        }

        if (id == R.id.action_settings) {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
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

    @Override
    protected void onResume() {
        super.onResume();
        requestPermissions();
        autoUpdate = new Timer();
        autoUpdate.schedule(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    public void run() {
                        refreshContent();
                    }
                });
            }
        }, 0, 1000); //
    }

    @Override
    protected void onPause() {
        autoUpdate.cancel();
        super.onPause();
    }

    private void refreshContent() {
        TextView toolbar = findViewById(R.id.textview_first);
        if (toolbar == null) {
            return;
        }

        StringBuilder sb = new StringBuilder();

        String weekdayTime;
        try {
            weekdayTime = Util.getStartTime(this, false).toString();
        } catch (Time.IncorrectTimeFormatException e) {
            weekdayTime = "Format error";
        }

        String weekendTime;
        try {
            weekendTime = Util.getStartTime(this, true).toString();
        } catch (Time.IncorrectTimeFormatException e) {
            weekendTime = "Format error";
        }

        sb.append("[L-V]   ");
        sb.append(weekdayTime);
        sb.append("\n");

        sb.append("[S-D]   ");
        sb.append(weekendTime);
        sb.append("\n");

        sb.append("\n");
        sb.append(Util.getDownloadTaskProgress());

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        assert pm != null;
        boolean ignoringBatteryOptimization = pm.isIgnoringBatteryOptimizations(getPackageName());

        sb.append("\n\n\n\n");

        if (!ignoringBatteryOptimization) {
            sb.append("\nNot ignoring battery optimization!!!!!!\n");
        }

        sb.append("Network is metered: ").append(isActiveNetworkMetered(this));
        sb.append("\n");

        toolbar.setText(sb.toString());
    }
}