package es.ollbap.radiodownloader;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import java.io.File;

import static es.ollbap.radiodownloader.Util.*;

/**
 * Created by ollbap on 1/19/18.
 */

public class ForegroundService extends Service {
    private static final String LOG_TAG = "ForegroundService";
    public static final int NOTIFICATION_ID = 101;
    private NotificationCompat.Builder mBuilder = null;
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;
    private DownloadTask downloadTask = null;

    public static final String START_PERIODIC_AlARM_INTENT = "es.pablo.my_profiles.START_PERIODIC_AlARM_INTENT";

    public interface ACTION {
        String START_FOREGROUND = ACTION.class.getCanonicalName()+"START_FOREGROUND";
        String STOP_FOREGROUND = ACTION.class.getCanonicalName()+"STOP_FOREGROUND";
        String UPDATE_NOTIFICATION = ACTION.class.getCanonicalName()+"UPDATE_NOTIFICATION";
        String PERIODIC_ALARM = ACTION.class.getCanonicalName()+"PERIODIC_ALARM";
        String VLC_START = ACTION.class.getCanonicalName()+"VLC_START";
    }

    public ForegroundService() {

    }

    public NotificationManager getNotificationManager() {
        return (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private void createNotificationBuilder() {
        mBuilder = new NotificationCompat.Builder(this, Configuration.getForegroundNotificationChanel(this));

        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        Bitmap icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher_background);

        mBuilder
                .setContentTitle("Radio download")
                .setTicker("Radio download")
                .setContentText("Download")
                .setSmallIcon(R.drawable.ic_launcher)
//                .setLargeIcon(Bitmap.createScaledBitmap(icon, 128, 128, false))
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_media_next, "Stop",
                        createServicePendintIntent(ACTION.STOP_FOREGROUND))
                .addAction(android.R.drawable.ic_media_ff, "Alarm",
                        createServicePendintIntent(ACTION.PERIODIC_ALARM))
                .addAction(android.R.drawable.ic_media_play, "VLC",
                        createServicePendintIntent(ACTION.VLC_START));

    }

    private PendingIntent createServicePendintIntent(String action) {
        Intent intent = new Intent(this, ForegroundService.class);
        intent.setAction(action);
        return PendingIntent.getService(this, 0,
                intent, 0);
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            //This is documented to happen when service is recreated internally
            if (intent == null) {
                logE("Received intent==null in service. Service was recreated and download will restart.");

                onStartServiceAction(true);

                return START_STICKY;
            }

            String action = intent.getAction();
            if (action == null) {
                logE("Received action==null in service. I don't know that did this. Intent: " + intent);
            } else if (action.equals(ACTION.START_FOREGROUND)) {
                onStartServiceAction(false);
            } else if (action.equals(ACTION.STOP_FOREGROUND)) {
                onStopServiceAction();
            } else if (action.equals(ACTION.UPDATE_NOTIFICATION)) {
                onUpdateNotificationAction();
            } else if (action.equals(ACTION.PERIODIC_ALARM)) {
                startPeriodicAlarm();
            } else if (action.equals(ACTION.VLC_START)) {
                startVlc();
            }
            return START_STICKY; // Send a NULL intent if the service is killed.
        } catch (Exception e) {
            logE("Unexpected exception", e);
            throw e;
        }
    }

    private void startVlc() {
        File outputFile = Configuration.getRadioOutputFile();

        Uri uri = Uri.fromFile(outputFile);
        Intent vlcIntent = new Intent(Intent.ACTION_VIEW);
        vlcIntent.setPackage("org.videolan.vlc");
        vlcIntent.setComponent(new ComponentName("org.videolan.vlc", "org.videolan.vlc.gui.video.VideoPlayerActivity"));
        vlcIntent.setDataAndTypeAndNormalize(uri, "audio/*");
        vlcIntent.putExtra("title", "Radio Download");
        vlcIntent.putExtra("from_start", false);
        vlcIntent.putExtra("position", 0L);
        startActivity(vlcIntent);
    }

    private void startPeriodicAlarm() {
        Intent periodicAlarmIntent = new Intent(START_PERIODIC_AlARM_INTENT, null);
        sendBroadcast(periodicAlarmIntent);
    }

    private void onStartServiceAction(boolean appendDownload) {
        logI("Received Start Foreground Intent ");
        requestLocks();
        createNotificationBuilder();
        showNotificationAndStartForeground();
        Util.cancelDownloadTask(downloadTask); //Just in case something was there before
        downloadTask = Util.startDownloadTask(this, appendDownload);
    }

    private void onStopServiceAction() {
        logI("Received Stop Foreground Intent");
        Util.cancelDownloadTask(downloadTask);
        mBuilder = null;
        releaseLocks();
        stopForeground(true);
        stopSelf();
    }

    private void requestLocks() {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        assert powerManager != null;
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "radiodownloader:MyWakelockTag");
        wakeLock.acquire(5*60*60*1000L /*5 hours*/);

        WifiManager wMgr = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        assert wMgr != null;
        wifiLock = wMgr.createWifiLock(WifiManager.WIFI_MODE_FULL, "MyWifiLock");
        wifiLock.acquire();
    }

    private void releaseLocks() {
        PowerManager.WakeLock lWakeLock = wakeLock;
        WifiManager.WifiLock lWifiLock = wifiLock;

        if (lWakeLock != null) {
            lWakeLock.release();
        }

        if (lWifiLock != null) {
            lWifiLock.release();
        }
    }

    private void showNotificationAndStartForeground() {
        if (mBuilder == null) {
            return;
        }

        updateNotificationInternal();
        startForeground(NOTIFICATION_ID, mBuilder.build());
    }

    private void onUpdateNotificationAction() {
        if (mBuilder == null) {
            return;
        }

        updateNotificationInternal();
        getNotificationManager().notify(NOTIFICATION_ID, mBuilder.build());
    }

    private void updateNotificationInternal() {
        if (mBuilder == null) {
            return;
        }

        mBuilder.setContentText(Util.getDownloadTaskProgress());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(LOG_TAG, "In onDestroy");
        Toast.makeText(this, "Service Detroyed!", Toast.LENGTH_SHORT).show();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Used only in case if services are bound (Bound Services).
        return null;
    }

}