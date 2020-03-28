package es.ollbap.radiodownloader.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;

import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.URL;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import es.ollbap.radiodownloader.util.Util;
import es.ollbap.radiodownloader.util.Configuration;

import static es.ollbap.radiodownloader.util.Util.isActiveNetworkMetered;
import static es.ollbap.radiodownloader.util.Util.logD;
import static es.ollbap.radiodownloader.util.Util.logE;
import static es.ollbap.radiodownloader.util.Util.logI;
import static es.ollbap.radiodownloader.util.Util.logW;

public class DownloadTask extends AsyncTask<String, Integer, String> {
    private static final Object LOCK = new Object();
    private static DownloadTask lastInstance = null;

    private Context context;
    private int total = 0;
    private boolean append;
    private int retryCount = 0;
    private static final int WAIT_FOR_RETRY_SECONDS = 5;

    private int downloadSeconds = 0;
    private boolean downloadEnabled;
    private String downloadUrl;
    private int downloadNotificationRefreshInBytes;
    private int allowedErrorRetries;
    private int allowedNotWifiInaRowErrors;
    private boolean allowMetered;

    public DownloadTask(Context context, boolean append) {
        this.append = append;
        this.context = context;
        synchronized (LOCK) {
            if (lastInstance != null) {
                lastInstance.cancel(true);
                if (lastInstance.getStatus() == Status.RUNNING) {
                    try {
                        //TODO: do something more elegant.
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            lastInstance = this;
        }

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        downloadEnabled = sharedPreferences.getBoolean("download_enabled", false);
        downloadUrl = sharedPreferences.getString("download_url", "");
        downloadSeconds = sharedPreferences.getInt("download_hours", 4) * 60 * 60;
        refreshConfigurationForNotificationRefresh(sharedPreferences);
        allowedErrorRetries = sharedPreferences.getInt("allowed_errors_retry_count", 50);
        allowedNotWifiInaRowErrors = sharedPreferences.getInt("allowed_not_wifi_in_a_row_errors", 50);
        allowMetered = sharedPreferences.getBoolean("download_allow_metered", false);
    }

    private void refreshConfigurationForNotificationRefresh(SharedPreferences sharedPreferences) {
        downloadNotificationRefreshInBytes = sharedPreferences.getInt("refresh_notification_kb", 50) *1025;
    }

    public static DownloadTask getLastInstance() {
        return lastInstance;
    }

    @Override
    protected String doInBackground(String... notUsed) {
        Instant startInstant = getCurrentInstant();
        if (!downloadEnabled) {
            logI("Download skipped because configuration disabled it");
            return null;
        }

        File outputFile = Configuration.getRadioOutputFile();
        downloadUrl = resolveURL(downloadUrl);
        if (downloadUrl == null) {
            logE("URL can not be resolved, exiting");
            return null;
        }
        logI("Downloading URL " + downloadUrl);
        int notWifiInARow = 0;

        try (OutputStream output = new FileOutputStream(outputFile, append)) {
            while (retryCount <= allowedErrorRetries) {
                long retryStart = System.nanoTime();
                DownloadStatus result = downloadStream(startInstant, downloadUrl, output);
                Util.updateForegroundServiceNotification(context);
                switch (result) {
                    case CAN_NOT_CONNECT:
                    case CONNECTION_LOST:
                    case ERROR:
                        //This might use some wifi data but if this is not retry with this code some times connections
                        // seems like not wifi and then becomes wifi.
                    case CONNECTION_IS_NOT_WIFI:
                        retryCount++;
                        logW("Download retry " + retryCount + "/" + allowedErrorRetries);
                        //If just tried wait for a second.
                        if ((System.nanoTime() - retryStart) < WAIT_FOR_RETRY_SECONDS * 1000000000L) {
                            waitTime(WAIT_FOR_RETRY_SECONDS * 1000);
                        }

                        break;
                    case COMPLETE:
                        return null;
                }

                if (result == DownloadStatus.CONNECTION_IS_NOT_WIFI) {
                    notWifiInARow++;
                    if (notWifiInARow >= allowedNotWifiInaRowErrors) {
                        logI("Not wifi " + allowedNotWifiInaRowErrors + " times in a row, exiting");
                        return null;
                    }
                } else {
                    notWifiInARow = 0;
                }
            }
        } catch (Exception e) {
            logE("Error downloading", e);
        } finally {
            Util.stopForegroundService(context);
        }

        return null;
    }

    private Instant getCurrentInstant() {
        return Clock.systemUTC().instant();
    }

    enum DownloadStatus {
        ERROR,
        COMPLETE,
        CONNECTION_LOST,
        CAN_NOT_CONNECT,
        CONNECTION_IS_NOT_WIFI
    }

    private DownloadStatus downloadStream(Instant startInstant, String url, OutputStream output) {
        HttpURLConnection connection = null;
        InputStream input = null;

        try {
            connection = createConnection(url);
            if (connection == null) {
                return DownloadStatus.CAN_NOT_CONNECT;
            }

            //Check again metered just in case wifi was disconnected right after check but before connection is performed.
            if (!allowMetered && isActiveNetworkMetered(context)) {
                logD("Download not performed because network is metered");
                return DownloadStatus.CONNECTION_IS_NOT_WIFI;
            }

            // download the file
            input = connection.getInputStream();

            byte data[] = new byte[4096];
            long lastLog = 0;
            long lastNotification = 0;
            int count;
            while ((count = input.read(data)) != -1) {
                Instant now = getCurrentInstant();
                Duration elapsedTime = Duration.between(startInstant, now);

                if (elapsedTime.getSeconds() > downloadSeconds) {
                    logI(String.format("Task Completed after %.2f hours", downloadSeconds / 60.0));
                    return DownloadStatus.COMPLETE;
                }

                // allow canceling with back button
                if (isCancelled()) {
                    logI(String.format("Task canceled after %.2f hours", downloadSeconds / 60.0));
                    return DownloadStatus.COMPLETE;
                }
                total += count;

                if ((total - lastLog) > Configuration.LOG_UPDATE_SIZE_BYTES) {
                    logI("Progress: " + getDownloadedSizeTag());
                    lastLog = total;
                }

                if ((total - lastNotification) > downloadNotificationRefreshInBytes) {
                    Util.updateForegroundServiceNotification(context);
                    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
                    refreshConfigurationForNotificationRefresh(sharedPreferences);
                    lastNotification = total;
                }

                output.write(data, 0, count);
            }

            return DownloadStatus.CONNECTION_LOST;
        } catch (SocketException e) {
            String message = e.getMessage();
            if (message != null && message.contains("Software caused connection abort")) {
                logI("Connection was stopped by software, probably wifi was lost.");
                return DownloadStatus.CONNECTION_LOST;
            } else {
                logE("Downloading Error " + message, e);
            }
            return DownloadStatus.ERROR;
        } catch (Exception e) {
            logE("Downloading Error " + e.getMessage(), e);
            return DownloadStatus.ERROR;
        } finally {
            try {
                if (input != null)
                    input.close();
            } catch (IOException ignored) {
            }

            if (connection != null)
                connection.disconnect();
        }
    }

    private HttpURLConnection createConnection(String url) {
        int max = 5;
        for (int i=0; i<max; i++) {
            HttpURLConnection connection = createConnectionInternal(url);
            if (connection != null) {
                return connection;
            }
            waitTime(250);
        }
        return null;
    }

    private HttpURLConnection createConnectionInternal(String url) {
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
            logE("Connection failed, will retry", e);
            return null;
        }
    }

    private String resolveURL(String url) {
        int max = 5;
        for (int i=0; i<max; i++) {
            String resolved = resolveURLInternal(url);
            if (resolved != null) {
                return resolved;
            }

            logE("Resolve error, " + i + "/" + max);

            waitTime(250);
        }
        return null;
    }

    private void waitTime(int i) {
        try {
            Thread.sleep(i);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logE("Interrupted", e);
        }
    }

    private String resolveURLInternal(String url) {
        if (url.endsWith("m3u")) {
            url = Util.resolveM3u(url);
            logD("Resolved URL " + url);
        } else if (url.endsWith("pls")) {
            url  = Util.resolvePls(url);
            logD("Resolved URL " + url);
        }
        return url;
    }

    public String getDownloadedSizeTag() {
        String extra = "";
        if (append) {
            extra = " (appended!)";
        }
        if (retryCount > 0) {
            extra += " retry: "+retryCount;
        }
        return String.format(Locale.ENGLISH, "%.2fMb"+extra, total / (1024.0*1024.0));
    }

    public void toggleMetered() {
        allowMetered = !allowMetered;
        logI("Toggled metered restrictions: " + allowMetered);
    }

    public boolean isAllowMetered() {
        return allowMetered;
    }
}
