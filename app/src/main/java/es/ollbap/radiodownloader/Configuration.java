package es.ollbap.radiodownloader;

import android.annotation.TargetApi;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.Environment;

import androidx.annotation.NonNull;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Created by ollbap on 1/20/18.
 */
class Configuration {
    //Download Task configurations
    public static final int DOWNLOAD_DURATION_SECONDS = 4 * (60 * 60); // 3h
    public static final int LOG_UPDATE_SIZE_BYTES = 10 * (1024 * 1024);   // 10MB
    public static final int NOTIFICATION_UPDATE_SIZE_BYTES = 50 * (1024); // 50Kb

    public static final String DOWNLOAD_FILE_NAME = "radio.mp3";
    public static final String DOWNLOAD_URL_OLD = "http://19093.live.streamtheworld.com:80/CADENASER_SC";
    public static final String DOWNLOAD_URL = "https://playerservices.streamtheworld.com/pls/CADENASER.pls";

    //Alarms configuration
    public static final int ALARM_WEEKEND_HOUR = 8;
    public static final int ALARM_WEEKEND_MINUTE = 0;

    public static final int ALARM_WEEKDAY_HOUR = 6;
    public static final int ALARM_WEEKDAY_MINUTE = 0;

    private static final String FOREGROUND_NOTIFICATION_CHANEL_ID = "FOREGROUND_NOTIFICATION_CHANEL";

    public static String getForegroundNotificationChanel(Context context) {
        createChannelsIfNeeded(context);
        return FOREGROUND_NOTIFICATION_CHANEL_ID;
    }

    @TargetApi(Build.VERSION_CODES.O)
    public static void createChannelsIfNeeded(Context context) {
        NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        assert mNotificationManager != null;

        NotificationChannel prevChannel = mNotificationManager.getNotificationChannel(FOREGROUND_NOTIFICATION_CHANEL_ID);
        if (prevChannel == null) {
            NotificationChannel mChannel = new NotificationChannel(FOREGROUND_NOTIFICATION_CHANEL_ID, "Foreground service", NotificationManager.IMPORTANCE_LOW);
            mChannel.setDescription("Notificación del servicio para tareas en segundo plano");
            mNotificationManager.createNotificationChannel(mChannel);
        }

    }

    public static void initializeApp(Context context) {
        //Re-program the alarm, just in case this is the first application execution ever (since install)
        //Next reprogram will be after alarm execution.
        Util.programNextAlarm(context);
        Configuration.createChannelsIfNeeded(context);
    }

    public static File getRadioOutputFile() {
        return new File(getOutputDirectory(), Configuration.DOWNLOAD_FILE_NAME);
    }

    @NonNull
    public static File getOutputDirectory() {
        File outputDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MUSIC), "radioDownloader");

        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        return outputDir;
    }

    private static String logTimeStampCache = null;
    public static File getRadioLogOutputFile() {
        if (logTimeStampCache == null) {
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
            logTimeStampCache = dateFormat.format(new Date());
        }

        return new File(getOutputDirectory(), "radio_stream_"+logTimeStampCache+"_log.txt");
    }
}
