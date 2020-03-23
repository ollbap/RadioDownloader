package es.ollbap.radiodownloader;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.os.PowerManager;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import static es.ollbap.radiodownloader.Util.logI;
import static es.ollbap.radiodownloader.Util.programNextAlarm;
import static es.ollbap.radiodownloader.Util.programTestAlarm;

public class MainActivity extends AppCompatActivity {

    private Timer autoUpdate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
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

        if (id == R.id.program_alarms || item.getItemId() == R.id.program_test_alarm || item.getItemId() == R.id.program_test_1h_alarm) {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH);
            Calendar nextAlarmTime = null;
            if(id == R.id.program_alarms) {
                nextAlarmTime = programNextAlarm(this);
            }

            if(id == R.id.program_test_alarm) {
                nextAlarmTime = programTestAlarm(this, 1000*10);
            }

            if(id == R.id.program_test_1h_alarm) {
                nextAlarmTime = programTestAlarm(this, 1000*30*60);
            }
            if (nextAlarmTime != null) {
                logI("Alarm programmed at: "+format.format(nextAlarmTime.getTime()));
            }
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

        StringBuilder sb = new StringBuilder();

        sb.append("[L-V]   ");
        sb.append(String.format("%02d:%02d", Configuration.ALARM_WEEKDAY_HOUR, Configuration.ALARM_WEEKDAY_MINUTE));
        sb.append("\n");

        sb.append("[S-D]   ");
        sb.append(String.format("%02d:%02d", Configuration.ALARM_WEEKEND_HOUR, Configuration.ALARM_WEEKEND_MINUTE));
        sb.append("\n");

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        assert pm != null;
        boolean ignoringBatteryOptimization = pm.isIgnoringBatteryOptimizations(getPackageName());

        if (!ignoringBatteryOptimization) {
            sb.append("\nNot ignoring battery optimization!!!!!!\n");
        }

        sb.append("\n");
        sb.append(Util.getDownloadTaskProgress());

        toolbar.setText(sb.toString());
    }
}
