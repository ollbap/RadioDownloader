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
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import es.ollbap.radiodownloader.util.Util;
import es.ollbap.radiodownloader.util.Configuration;

import static es.ollbap.radiodownloader.util.Util.createConnection;
import static es.ollbap.radiodownloader.util.Util.isActiveNetworkMetered;
import static es.ollbap.radiodownloader.util.Util.logD;
import static es.ollbap.radiodownloader.util.Util.logE;
import static es.ollbap.radiodownloader.util.Util.logI;
import static es.ollbap.radiodownloader.util.Util.resolveDownloadURL;
import static es.ollbap.radiodownloader.util.Util.waitTime;

public class DownloadTask extends AsyncTask<String, Integer, String> {
    private static final Object LOCK = new Object();
    private static DownloadTask lastInstance = null;

    private WeakReference<Context> contextReference;
    private int total = 0;
    private boolean append;
    private int retryCount = 0;
    private static final int WAIT_FOR_RETRY_SECONDS = 5;

    private int downloadSeconds;
    private boolean downloadEnabled;
    private String downloadUrl;
    private int downloadNotificationRefreshInBytes;
    private int downloadLogRefreshInBytes;
    private int allowedErrorRetries;
    private int allowedNotWifiInaRowErrors;
    private boolean allowMetered;
    private DownloadStatus downloadStatus = DownloadStatus.NOT_STARTED;

    public enum DownloadStatus {
        NOT_STARTED,
        INTERRUPTED,
        DOWNLOADING,
        COMPLETED
    }

    public DownloadTask(Context context, boolean append) {
        this.append = append;
        this.contextReference = new WeakReference<>(context);
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

    public DownloadStatus getDownloadStatus() {
        return downloadStatus;
    }

    private void refreshConfigurationForNotificationRefresh(SharedPreferences sharedPreferences) {
        downloadNotificationRefreshInBytes = sharedPreferences.getInt("refresh_download_notification_kb", 50) * 1024;
        downloadLogRefreshInBytes = sharedPreferences.getInt("refresh_download_log_mb", 10) * 1024 * 1024;
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
        downloadUrl = resolveDownloadURL(downloadUrl, 5);
        if (downloadUrl == null) {
            logE("URL can not be resolved, exiting");
            return null;
        }
        logD("Downloading URL " + downloadUrl);
        int notWifiInARow = 0;

        try (OutputStream output = new FileOutputStream(outputFile, append)) {
            while (retryCount <= allowedErrorRetries) {
                long retryStart = System.nanoTime();
                DownloadIterationResult result = downloadStream(startInstant, downloadUrl, output);
                Util.updateForegroundServiceNotification(getContext());
                switch (result) {
                    case CAN_NOT_CONNECT:
                    case CONNECTION_LOST:
                    case ERROR:
                        //This might use some wifi data but if this is not retry with this code some times connections
                        // seems like not wifi and then becomes wifi.
                    case CONNECTION_IS_NOT_WIFI:
                        retryCount++;
                        logD("Download retry " + retryCount + "/" + allowedErrorRetries);
                        //If just tried wait for a second.
                        if ((System.nanoTime() - retryStart) < WAIT_FOR_RETRY_SECONDS * 1000000000L) {
                            waitTime(WAIT_FOR_RETRY_SECONDS * 1000);
                        }

                        break;
                    case COMPLETE:
                        downloadStatus = DownloadStatus.COMPLETED;
                        return null;
                }

                if (result == DownloadIterationResult.CONNECTION_IS_NOT_WIFI) {
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
            Util.stopForegroundService(getContext());
        }

        return null;
    }

    private Instant getCurrentInstant() {
        return Clock.systemUTC().instant();
    }

    public enum DownloadIterationResult {
        ERROR,
        COMPLETE,
        CONNECTION_LOST,
        CAN_NOT_CONNECT,
        CONNECTION_IS_NOT_WIFI
    }

    private DownloadIterationResult downloadStream(Instant startInstant, String url, OutputStream output) {
        HttpURLConnection connection = null;
        InputStream input = null;

        try {
            connection = createConnectionWithRetry(url);
            if (connection == null) {
                return DownloadIterationResult.CAN_NOT_CONNECT;
            }

            //Check again metered just in case wifi was disconnected right after check but before connection is performed.
            if (!allowMetered && isActiveNetworkMetered(getContext())) {
                logD("Download not performed because network is metered");
                return DownloadIterationResult.CONNECTION_IS_NOT_WIFI;
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
                    logI(String.format("Task Completed after %.2f minutes", elapsedTime.getSeconds() / 60.0));
                    return DownloadIterationResult.COMPLETE;
                }

                // allow canceling with back button
                if (isCancelled()) {
                    logI(String.format("Task canceled after %.2f minutes", elapsedTime.getSeconds() / 60.0));
                    return DownloadIterationResult.COMPLETE;
                }
                total += count;

                if ((total - lastLog) > downloadLogRefreshInBytes) {
                    logI("Progress: " + getDownloadedSizeTag());
                    lastLog = total;
                }

                if ((total - lastNotification) > downloadNotificationRefreshInBytes) {
                    Util.updateForegroundServiceNotification(getContext());
                    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
                    refreshConfigurationForNotificationRefresh(sharedPreferences);
                    lastNotification = total;
                }

                if (downloadStatus == DownloadStatus.INTERRUPTED) {
                    logI("Download resumed: " + getDownloadedSizeTag());
                    lastLog = total;
                }
                if (downloadStatus == DownloadStatus.NOT_STARTED) {
                    logI("Download started");
                    lastLog = total;
                }

                downloadStatus = DownloadStatus.DOWNLOADING;
                output.write(data, 0, count);
            }

            return DownloadIterationResult.CONNECTION_LOST;
        } catch (Exception e) {
            String message = e.getMessage();
            if (message != null && message.contains("Software caused connection abort")) {
                logI("Connection was stopped by software, probably wifi was lost.");
                return DownloadIterationResult.CONNECTION_LOST;
            } else {
                logE("Downloading Error " + message, e);
            }
            return DownloadIterationResult.ERROR;
        } finally {
            downloadStatus = DownloadStatus.INTERRUPTED;
            try {
                if (input != null)
                    input.close();
            } catch (IOException ignored) {
            }

            if (connection != null)
                connection.disconnect();
        }
    }

    private HttpURLConnection createConnectionWithRetry(String url) {
        int max = 5;
        for (int i=0; i<max; i++) {
            HttpURLConnection connection = createConnection(url);
            if (connection != null) {
                return connection;
            }
            waitTime(250);
        }
        return null;
    }

    public String getDownloadedSizeTag() {
        String extra = "";
        if (append) {
            extra = " (appended!)";
        }
        if (retryCount > 0) {
            extra += " (r " + retryCount + ")";
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

    public Context getContext() {
        Context context = contextReference.get();
        if (context == null) {
            throw new IllegalStateException("Context reference was lost");
        }
        return context;
    }
}
