package es.ollbap.radiodownloader;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Created by ollbap on 1/13/18.
 */
public final class Util {
    private static final int PROGRAM_DOWNLOAD_REQUEST_CODE = 0;
    private static final String CHARSET = "UTF-8";

    private Util() {}

    public static String getTimeTag() {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yy-MM-dd HH:mm:ss", Locale.ENGLISH);
        return simpleDateFormat.format(new Date());
    }

    public static void logI(String message) {
        log("INFO", message, null);
    }

    public static void logE(String message, Throwable e) {
        log("ERROR", message, e);
    }

    public static void logE(String message) {
        log("ERROR", message, null);
    }

    public static void log(String tag, String message, Throwable eToLog) {
        File logFile = Configuration.getRadioLogOutputFile();

        switch (tag) {
            case "ERROR": Log.e("Util", message, eToLog);
            default: Log.i("Util", message, eToLog);
        }

        File containingDir = logFile.getParentFile();
        if (containingDir != null && !containingDir.exists()) {
            containingDir.mkdirs();
        }

        boolean append = logFile.exists();

        try (PrintStream out = new PrintStream(new FileOutputStream(logFile, append))) {
            Log.i("DownloadTask", message);
            out.println(getTimeTag() + " "+tag+" " + message);
            out.flush();
            if (eToLog != null) {
                eToLog.printStackTrace(out);
            }
        } catch (FileNotFoundException ex) {
            Log.e("Util", "Can not write log", ex);
        }
    }

    public static Calendar programNextAlarm(Context context) {
        Calendar nextAlarmTime = computeNextAlarmTime();
        programAlarm(context, nextAlarmTime.getTimeInMillis());
        return nextAlarmTime;
    }

    public static Calendar programTestAlarm(Context context, long waitTime) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        //calendar.set(Calendar.HOUR_OF_DAY, 12);
        //calendar.set(Calendar.MINUTE, 30);
        calendar.setTimeInMillis(System.currentTimeMillis()+waitTime);

        long alarmTime = calendar.getTimeInMillis();
        programAlarm(context, alarmTime);
        return calendar;
    }

    public static Calendar computeNextAlarmTime() {
        Calendar now = Calendar.getInstance();
        Calendar calendar = (Calendar) now.clone();

        setStartForDay(calendar);

        //If alarm already lost then go to tomorrow.
        if (calendar.before(now)) {
            calendar = (Calendar) now.clone();
            calendar.add(Calendar.HOUR, 24);
            setStartForDay(calendar);
        }

        return calendar;
    }

    public static void setStartForDay(Calendar calendar) {
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);

        if (isWeekend(dayOfWeek)) {
            calendar.set(Calendar.HOUR_OF_DAY, Configuration.ALARM_WEEKEND_HOUR);
            calendar.set(Calendar.MINUTE, Configuration.ALARM_WEEKEND_MINUTE);
        } else {
            calendar.set(Calendar.HOUR_OF_DAY, Configuration.ALARM_WEEKDAY_HOUR);
            calendar.set(Calendar.MINUTE, Configuration.ALARM_WEEKDAY_MINUTE);
        }
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    public static void startForegroundService(Context context) {
        sendActionToForegroundService(context, ForegroundService.ACTION.START_FOREGROUND);
    }

    public static void stopForegroundService(Context context) {
        sendActionToForegroundService(context, ForegroundService.ACTION.STOP_FOREGROUND);
    }

    public static void updateForegroundServiceNotification(Context context) {
        sendActionToForegroundService(context, ForegroundService.ACTION.UPDATE_NOTIFICATION);
    }

    public static void sendActionToForegroundService(Context context, String action) {
        Intent service = new Intent(context, ForegroundService.class);
        service.setAction(action);
        context.startService(service);
    }

    public static DownloadTask startDownloadTask(Context context, boolean append) {
        DownloadTask downloadTask = new DownloadTask(context, append);
        downloadTask.execute();
        return downloadTask;
    }

    @Deprecated
    //It is better to keep the task and stop it.
    public static void cancelLastDownloadTask() {
        cancelDownloadTask(DownloadTask.getLastInstance());
    }

    public static void cancelDownloadTask(DownloadTask downloadTask) {
        if (downloadTask != null) {
            logI("DownloadTask cancel executed, background thread should cancel itself if still running.");
            downloadTask.cancel(true);
        }
    }

    public static String getDownloadTaskProgress() {
        String updateText = "Task is not running";
        DownloadTask instance = DownloadTask.getLastInstance();
        if (instance != null) {
            updateText = "Download status "+instance.getStatus()+" "+instance.getDownloadedSizeTag();
            if (instance.isAllowMetered()) {
                updateText += " (metered allowed)";
            }
        }

        return updateText;
    }

    public static void programAlarm(Context context, long alarmTime) {
        AlarmManager alarmMgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        assert alarmMgr != null : "AlarmManager can not be retrieved";
        Intent intent = new Intent(context, MyAlarmReceiver.class);
        intent.setAction("com.example.android.repeatingalarm");
        PendingIntent alarmIntent = PendingIntent.getBroadcast(context, PROGRAM_DOWNLOAD_REQUEST_CODE, intent, 0);
        alarmMgr.setExact(AlarmManager.RTC_WAKEUP, alarmTime, alarmIntent);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH);
        logI("Alarm programmed at: "+format.format(alarmTime));
    }

    public static boolean isWeekend(int dayOfWeek) {
        return dayOfWeek == Calendar.SUNDAY || dayOfWeek == Calendar.SATURDAY;
    }

    @Nullable
    private static String extractM3uInternal(String url) {
        InputStream inputStream = null;
        BufferedReader reader = null;
        try {
            // Read URL content
            URL theUrl = new URL(url);
            URLConnection urlConnection = theUrl.openConnection();
            inputStream = urlConnection.getInputStream();
            reader = new BufferedReader(new InputStreamReader(
                    inputStream, CHARSET));
            String currentLine = reader.readLine();
            if (currentLine == null) {
                logE("Unexpected end of M3U file");
                return null;
            }

            // Skip comments
            while (currentLine.startsWith("#")) {
                currentLine = reader.readLine();
                if (currentLine == null) {
                    logE("Unexpected end of M3U file");
                    return null;
                }
            }
            return currentLine;
        } catch (Exception e) {
            logE("Error reading stream", e);
            return null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    logE("Error closing stream", e);
                }
            }
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    logE("Error closing stream", e);
                }
            }
        }
    }

    @Nullable
    private static String resolvePlsInternal(String url) {
        InputStream inputStream = null;
        BufferedReader reader = null;
        try {
            // Read URL content
            URL theUrl = new URL(url);
            URLConnection urlConnection = theUrl.openConnection();
            inputStream = urlConnection.getInputStream();
            reader = new BufferedReader(new InputStreamReader(
                    inputStream, CHARSET));
            String currentLine = reader.readLine();
            if (currentLine == null) {
                logE("Unexpected end of PLS file");
                return null;
            }

            if (!currentLine.matches(".*\\[.*playlist.*\\].*")) {
                logE("Unexpected header line in pls format: " +currentLine);
            }


            currentLine = reader.readLine();
            if (currentLine == null) {
                logE("Unexpected end of PLS file");
                return null;
            }

            String[] splits = currentLine.split("=");
            if (splits.length != 2) {
                logE("Unexpected track line format at second line, expected 'NAME=URL' found: " +currentLine);
            }

            return splits[1];
        } catch (Exception e) {
            logE("Error reading stream", e);
            return null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    logE("Error closing stream", e);
                }
            }
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    logE("Error closing stream", e);
                }
            }
        }
    }

    public static String resolveM3u(String url) {
        return extractM3uInternal(url);
    }

    public static String resolvePls(String url) {
        return resolvePlsInternal(url);
    }

    public static void openLogFile(Context context) {
        openFile(context, Configuration.getRadioLogOutputFile(), "text/plain");
    }

    public static void openRadioFile(Context context) {
        openFile(context, Configuration.getRadioOutputFile(), "audio/*");
    }

    public static void openFile(Context context, File file, String type) {
        try {
            String authority = context.getApplicationContext().getPackageName() + ".provider";
            Uri uri = FileProvider.getUriForFile(context, authority, file);

            Intent intent = new Intent();
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setAction(android.content.Intent.ACTION_VIEW);
            intent.setDataAndType(uri, type);
            context.startActivity(intent);
        } catch (Exception e) {
            logE("Can not open file", e);
        }
    }

    public static void playInVlc(Context context) {
        try {
            File outputFile = Configuration.getRadioOutputFile();
            String authority = context.getApplicationContext().getPackageName() + ".provider";
            Uri uri = FileProvider.getUriForFile(context,authority, outputFile);

            Intent vlcIntent = new Intent(Intent.ACTION_VIEW);
            vlcIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            vlcIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            vlcIntent.setPackage("org.videolan.vlc");
            vlcIntent.setComponent(new ComponentName("org.videolan.vlc", "org.videolan.vlc.gui.video.VideoPlayerActivity"));
            vlcIntent.setDataAndTypeAndNormalize(uri, "audio/*");
            vlcIntent.putExtra("title", "Radio Download");
            vlcIntent.putExtra("from_start", false);
            vlcIntent.putExtra("position", 0L);
            context.startActivity(vlcIntent);
        } catch (Exception e) {
            logE("Can not play in vlc", e);
        }
    }

}
