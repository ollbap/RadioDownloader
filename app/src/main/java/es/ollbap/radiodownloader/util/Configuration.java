package es.ollbap.radiodownloader.util;

import android.annotation.TargetApi;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

    public static OutputStream getRadioOutputStream(Context context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {

            deleteOldDownloads(context);

            DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

            ContentValues values = new ContentValues();
            String fileName = "radioDownload_" +formatter.format(LocalDateTime.now()) + ".mp3";

            values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            values.put(MediaStore.Downloads.MIME_TYPE, "audio/mpeg");
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/radio_downloader");

            Uri uri = context.getContentResolver().insert(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values);
            if (uri == null) {
                throw new IllegalStateException("File to write can't be opened");
            }

            try {
                return context.getContentResolver().openOutputStream(uri);
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        } else {
            throw new IllegalStateException("Unsupported android version");
        }
    }

    private static void deleteOldDownloads(Context context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // Define the time threshold for deleting old files
            Instant threshold = Instant.now().minus(Duration.ofDays(7));
            //Instant threshold = Instant.now().minus(Duration.ofSeconds(60));

            // Define the query to retrieve the old files
            String selection = MediaStore.Downloads.DISPLAY_NAME + " LIKE 'radioDownload_%' AND " +
                    MediaStore.Downloads.DATE_ADDED + " < " + threshold.getEpochSecond();
            Uri queryUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI.buildUpon().appendQueryParameter(MediaStore.VOLUME_EXTERNAL_PRIMARY, "true").build();

            // Test get values
            Cursor cursor = context.getContentResolver().query(queryUri, null, selection, null, null);
            if (cursor == null) {
                throw new IllegalStateException("Can't find the file");
            }
            while (cursor.moveToNext()) {
                String path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME));
                long dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATE_ADDED));
                Log.d("File Path", "Path of the most recently added file: " + path);
            }
            cursor.close();

            // Perform the delete operation
            int deleted = context.getContentResolver().delete(queryUri, selection, null);
        } else {
            throw new IllegalStateException();
        }
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

    public static File getRadioOutputFile(Context context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            String[] projection = {MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME, MediaStore.Downloads.DATE_ADDED, MediaStore.Downloads.DATA};
            String selection = MediaStore.Downloads.DISPLAY_NAME + " LIKE 'radioDownload_%' ";
            String sortOrder = MediaStore.Downloads.DATE_ADDED + " DESC LIMIT 1";
            Uri queryUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI.buildUpon().appendQueryParameter(MediaStore.VOLUME_EXTERNAL_PRIMARY, "true").build();

            Cursor cursor = context.getContentResolver().query(queryUri, projection, selection, null, sortOrder);
            if (cursor == null) {
                throw new IllegalStateException("Can't find the file");
            }

            cursor.moveToFirst();
            String path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATA));

            cursor.close();
            return new File(path);
        } else {
            throw new IllegalStateException("Unsupported android version");
        }
    }
}
