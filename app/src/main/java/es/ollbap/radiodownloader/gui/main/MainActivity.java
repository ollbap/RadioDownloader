package es.ollbap.radiodownloader.gui.main;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.MenuCompat;
import androidx.preference.PreferenceManager;

import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import java.util.Timer;
import java.util.TimerTask;

import es.ollbap.radiodownloader.gui.settings.SettingsActivity;
import es.ollbap.radiodownloader.gui.UrlValidationTask;
import es.ollbap.radiodownloader.service.DownloadTask;
import es.ollbap.radiodownloader.R;
import es.ollbap.radiodownloader.util.Time;
import es.ollbap.radiodownloader.util.Util;

import static es.ollbap.radiodownloader.util.Util.checkIsIgnoringBatteryOptimization;
import static es.ollbap.radiodownloader.util.Util.isActiveNetworkMetered;
import static es.ollbap.radiodownloader.util.Util.openPowerSettings;

public class MainActivity extends AppCompatActivity {

    private static final boolean REQUIRE_EXTERNAL_STORAGE_PERMISSIONS = false;
    private static final boolean REQUIRE_DISABLE_BATTERY_OPTIMIZATIONS = false;
    private Timer autoUpdate;
    private UrlValidationTask urlValidationTask = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Util.configureLogFile(this);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        MenuCompat.setGroupDividerEnabled(menu, true);
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
        }

        if(id == R.id.cancel_download) {
            Util.stopForegroundService(this);
        }

        if(id == R.id.open_radio_file) {
            Util.openRadioFile(this);
        }

        if(id == R.id.open_log_file) {
            Util.openLogFile(this);
        }

        if(id == R.id.open_vlc) {
            Util.playInVlc(this);
        }

        if(id == R.id.toggle_metered) {
            DownloadTask lastInstance = DownloadTask.getLastInstance();
            if (lastInstance != null) {
                lastInstance.toggleMetered();
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
        if (REQUIRE_EXTERNAL_STORAGE_PERMISSIONS) {
            int p = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            if (p != PackageManager.PERMISSION_GRANTED) {
                int requestCode = 123;
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        requestCode);
            }
        }

        if (REQUIRE_DISABLE_BATTERY_OPTIMIZATIONS) {
            if (!checkIsIgnoringBatteryOptimization(this)) {
                openPowerSettings(this);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        requestPermissions();
        autoUpdate = new Timer();
        revalidateDownloadUrl();
        autoUpdate.schedule(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(() -> refreshContent());
            }
        }, 0, 1000);
    }

    private void revalidateDownloadUrl() {
       urlValidationTask = new UrlValidationTask(this);
       urlValidationTask.execute();
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

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        long lastProgramedAlarmTime = sharedPreferences.getLong(Util.LAST_PROGRAMED_ALARM_PREFERENCE_KEY, -1);
        String lastProgramedAlarm = "Not programmed";
        if (lastProgramedAlarmTime != -1) {
            lastProgramedAlarm = Util.formatMilliseconds(lastProgramedAlarmTime);
        }

        String downloadUrl = sharedPreferences.getString("download_url", "");

        UrlValidationTask validationTask = urlValidationTask;
        if (validationTask != null && validationTask.getDownloadUrlIsValidationResult() != null) {
            String downloadUrlIsValidationResult = validationTask.getDownloadUrlIsValidationResult();
            if (!downloadUrlIsValidationResult.isEmpty()) {
                sb.append(downloadUrlIsValidationResult);
                sb.append("\n");
            }
        }

        sb.append("[L-V]   ");
        sb.append(weekdayTime);
        sb.append("\n");

        sb.append("[S-D]   ");
        sb.append(weekendTime);
        sb.append("\n");
        sb.append("Next alarm: ");
        sb.append(lastProgramedAlarm);
        sb.append("\n");

        sb.append("\n");
        sb.append(Util.getDownloadTaskProgress());


        sb.append("\n\n\n\n");

        if (REQUIRE_DISABLE_BATTERY_OPTIMIZATIONS) {
            boolean ignoringBatteryOptimization = checkIsIgnoringBatteryOptimization(this);
            if (!ignoringBatteryOptimization) {
                sb.append("\nNot ignoring battery optimization!!!!!!\n");
            }
        }

        sb.append("Network is metered: ").append(isActiveNetworkMetered(this));
        sb.append("\n");

        toolbar.setText(sb.toString());
    }

}