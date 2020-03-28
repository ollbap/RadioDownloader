package es.ollbap.radiodownloader.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.preference.PreferenceManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import es.ollbap.radiodownloader.service.DownloadTask;
import es.ollbap.radiodownloader.service.ForegroundService;
import es.ollbap.radiodownloader.service.MyAlarmReceiver;

/**
 * Created by ollbap on 1/13/18.
 */
public final class Util {
    private static final int PROGRAM_DOWNLOAD_REQUEST_CODE = 0;
    private static final String CHARSET = "UTF-8";
    public static final String LAST_PROGRAMED_ALARM_PREFERENCE_KEY = "last_programed_alarm";
    private static Level configuredLevel = Level.INFO;
    public enum Level {
        ERROR,
        WARNING,
        INFO,
        DEBUG
        }

    private Util() {}

    public static void refreshConfiguredLogLevel(Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        String levelTag = sharedPreferences.getString("log_level", "INFO");
        Level level;
        try {
            level = Level.valueOf(levelTag);
        } catch (Exception e) {
            logE("Incorrect log level tag" + levelTag);
            level = Level.INFO;
        }
        if (configuredLevel != level) {
            logI("Log level changed to " + level);
        }
        configuredLevel = level;
    }

    public static String getTimeTag() {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yy-MM-dd HH:mm:ss", Locale.ENGLISH);
        return simpleDateFormat.format(new Date());
    }

    public static void logD(String message) {
        logD(message, null);
    }

    public static void logD(String message, Throwable eToLog) {
        log(Level.DEBUG, message, eToLog);
    }

    public static void logI(String message) {
        log(Level.INFO, message, null);
    }

    public static void logW(String message) {
        log(Level.WARNING, message, null);
    }

    public static void logE(String message, Throwable e) {
        log(Level.ERROR, message, e);
    }

    public static void logE(String message) {
        log(Level.ERROR, message, null);
    }

    public static void log(Level level, String message, Throwable eToLog) {
        File logFile = Configuration.getRadioLogOutputFile();

        switch (level) {
            //case "ERROR": Log.e("Util", message, eToLog);
            //default: Log.i("Util", message, eToLog);
            case ERROR:
                Log.e("Util", message, eToLog);
                break;
            case WARNING:
                Log.w("Util", message, eToLog);
                break;
            case INFO:
                Log.i("Util", message, eToLog);
                break;
            case DEBUG:
                Log.d("Util", message, eToLog);
                break;
        }

        if (configuredLevel.ordinal() < level.ordinal()) {
            return;
        }

        File containingDir = logFile.getParentFile();
        if (containingDir != null && !containingDir.exists()) {
            containingDir.mkdirs();
        }

        boolean append = logFile.exists();

        try (PrintStream out = new PrintStream(new FileOutputStream(logFile, append))) {
            out.println(getTimeTag() + " "+level+" " + message);
            if (eToLog != null) {
                eToLog.printStackTrace(out);
            }
        } catch (FileNotFoundException ex) {
            Log.e("Util", "Can not write log", ex);
        }
    }

    public static Calendar programNextAlarm(Context context) {
        Calendar nextAlarmTime = computeNextAlarmTime(context);
        programAlarm(context, nextAlarmTime.getTimeInMillis());
        return nextAlarmTime;
    }

    public static Calendar programTestAlarm(Context context, long waitTime) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis()+waitTime);

        long alarmTime = calendar.getTimeInMillis();
        programAlarm(context, alarmTime);
        return calendar;
    }

    public static Calendar computeNextAlarmTime(Context context) {
        Calendar now = Calendar.getInstance();
        Calendar calendar = (Calendar) now.clone();

        setStartForDay(calendar, context);

        //If alarm already lost then go to tomorrow.
        if (calendar.before(now)) {
            calendar = (Calendar) now.clone();
            calendar.add(Calendar.DAY_OF_MONTH, 1);
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            setStartForDay(calendar, context);
        }

        return calendar;
    }

    public static void setStartForDay(Calendar calendar, Context context) {
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);

        Time time;
        try {
            time = getStartTime(context, isWeekend(dayOfWeek));
        } catch (Time.IncorrectTimeFormatException e) {
            Util.logE("Time format is incorrect: " + e.getMessage()+". Defaulting to 06:00.");
            try {
                time = new Time(6,0);
            } catch (Time.IncorrectTimeFormatException ex) {
                throw new IllegalStateException(ex);
            }
        }

        calendar.set(Calendar.HOUR_OF_DAY, time.getHour());
        calendar.set(Calendar.MINUTE, time.getMinute());
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    public static Time getStartTime(Context context, boolean isWeekend) throws Time.IncorrectTimeFormatException {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        String value;
        if (isWeekend) {
            value = sharedPreferences.getString("weekend_start_time", "08:00");
        } else {
            value = sharedPreferences.getString("weekday_start_time", "06:00");
        }
        return new Time(value);
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
            logD("DownloadTask cancel executed, background thread should cancel itself if still running.");
            downloadTask.cancel(true);
        }
    }

    public static String getDownloadTaskProgress() {
        String updateText = "Task is not running";
        DownloadTask instance = DownloadTask.getLastInstance();
        if (instance != null) {
            updateText = "Download status "+instance.getDownloadStatus()+" "+instance.getDownloadedSizeTag();
            if (instance.isAllowMetered()) {
                updateText += "\n(metered allowed)";
            }
        }

        return updateText;
    }

    public static void programAlarm(Context context, long alarmTime) {
        AlarmManager alarmMgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        assert alarmMgr != null : "AlarmManager can not be retrieved";
        Intent intent = new Intent(context, MyAlarmReceiver.class);
        intent.setAction(context.getApplicationContext().getPackageName());
        PendingIntent alarmIntent = PendingIntent.getBroadcast(context, PROGRAM_DOWNLOAD_REQUEST_CODE, intent, 0);
        alarmMgr.setExact(AlarmManager.RTC_WAKEUP, alarmTime, alarmIntent);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        long lastProgramedAlarmTime = sharedPreferences.getLong(LAST_PROGRAMED_ALARM_PREFERENCE_KEY, -1);
        sharedPreferences.edit().putLong(LAST_PROGRAMED_ALARM_PREFERENCE_KEY, alarmTime).apply();

        if (lastProgramedAlarmTime != alarmTime) {
            logI("Alarm reprogrammed at: " + formatMilliseconds(alarmTime));
        }
    }

    public static String formatMilliseconds(long alarmTime) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH);
        return format.format(alarmTime);
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

    public static boolean isActiveNetworkMetered(Context context) {
        ConnectivityManager mConnectivity =
                (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        // Skip if no connection
        assert mConnectivity != null;

        if (mConnectivity.isActiveNetworkMetered()) {
            logD("Current network is metered");
            return true;
        }

        logD("Current network is not metered");
        return false;
    }

    public static void applyConfigurationChanges(Context context) {
        refreshConfiguredLogLevel(context);
        programNextAlarm(context);
    }

    public static String resolveDownloadURL(String url, int retry) {
        int max = 5;
        for (int i = 0; i < retry+1; i++) {
            if (i != 0) {
                waitTime(250);
            }
            String resolved = resolveDownloadURLInternal(url);
            if (resolved != null) {
                return resolved;
            }

            logE("Resolve error, " + i + "/" + max);

        }
        return null;
    }

    private static String resolveDownloadURLInternal(String url) {
        if (url.endsWith("m3u")) {
            url = Util.resolveM3u(url);
            logD("Resolved URL " + url);
        } else if (url.endsWith("pls")) {
            url  = Util.resolvePls(url);
            logD("Resolved URL " + url);
        }
        return url;
    }

    public static void waitTime(int i) {
        try {
            Thread.sleep(i);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logE("Interrupted", e);
        }
    }

    public static HttpURLConnection createConnection(String url) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.connect();

            // expect HTTP 200 OK, so we don't mistakenly save error report
            // instead of the file
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                logE("Error connecting: " + connection.getResponseCode() + " " + connection.getResponseMessage());
                return null;
            }
            return connection;
        } catch (IOException e) {
            logD("Connection failed, will retry", e);
            return null;
        }
    }

}
