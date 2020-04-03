package es.ollbap.radiodownloader.util;

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
public class Configuration {
    public static final String DOWNLOAD_FILE_NAME = "radio.mp3";
    public static final String DOWNLOAD_URL_OLD = "http://19093.live.streamtheworld.com:80/CADENASER_SC";
    public static final String DOWNLOAD_URL_NEW = "https://playerservices.streamtheworld.com/pls/CADENASER.pls";

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

    public static File getRadioOutputFile(Context context) {
        return new File(getOutputDirectory(context), Configuration.DOWNLOAD_FILE_NAME);
    }

    @NonNull
    public static File getOutputDirectory(Context context) {
        File outputDir = context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS);
        assert outputDir != null;

        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        return outputDir;
    }

    private static String logTimeStampCache = null;
    public static File getRadioLogOutputFile(Context context) {
        if (logTimeStampCache == null) {
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
            logTimeStampCache = dateFormat.format(new Date());
        }

        return new File(getOutputDirectory(context), "radio_stream_"+logTimeStampCache+"_log.txt");
    }
}
