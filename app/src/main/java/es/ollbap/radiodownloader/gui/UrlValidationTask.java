package es.ollbap.radiodownloader.gui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;

import androidx.preference.PreferenceManager;

import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;

import es.ollbap.radiodownloader.util.Util;

import static es.ollbap.radiodownloader.util.Util.logD;

public class UrlValidationTask extends AsyncTask<Void, Void, String> {
    private String result = null;
    private WeakReference<Context> weakContext;

    public UrlValidationTask(Context context) {
        weakContext = new WeakReference<>(context);
    }

    public String getDownloadUrlIsValidationResult() {
        return result;
    }

    @Override
    protected String doInBackground(Void... objects) {
        result = runValidation(weakContext.get());
        return result;
    }

    private static String runValidation(Context context) {
        try {
            if (context == null) {
                return "";
            }
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
            String downloadUrl = sharedPreferences.getString("download_url", "");
            if (downloadUrl.isEmpty()) {
                return "URL is invalid: not configured yet.";
            }
            String resolved = Util.resolveDownloadURL(downloadUrl, 0);
            if (resolved == null) {
                return "URL is invalid: can not be resolved.";
            }

            HttpURLConnection connection = null;
            try {
                connection = Util.createConnection(downloadUrl);
                if (connection != null) {
                    return "";
                } else {
                    return "URL is invalid: can not connect";
                }

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        } catch (Exception e) {
            logD("URL validation failed", e);
            return "URL is invalid: " + e.getMessage();
        }
    }
}
