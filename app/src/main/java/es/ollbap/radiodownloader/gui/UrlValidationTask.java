package es.ollbap.radiodownloader.gui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;

import androidx.preference.PreferenceManager;

import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;

import es.ollbap.radiodownloader.util.Util;

import static es.ollbap.radiodownloader.util.Util.logD;

public class UrlValidationTask extends AsyncTask<Void, Void, Void> {
    private Boolean downloadUrlIsValid = null;
    private WeakReference<Context> weakContext;

    public UrlValidationTask(Context context) {
        weakContext = new WeakReference<>(context);
    }

    public Boolean getDownloadUrlIsValid() {
        return downloadUrlIsValid;
    }

    @Override
    protected Void doInBackground(Void... objects) {
        try {
            Context context = weakContext.get();
            if (context == null) {
                return null;
            }
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
            String downloadUrl = sharedPreferences.getString("download_url", "");
            String resolved = Util.resolveDownloadURL(downloadUrl, 0);
            if (resolved == null) {
                downloadUrlIsValid = false;
            }

            HttpURLConnection connection = null;
            try {
                connection = Util.createConnection(downloadUrl);
                downloadUrlIsValid = connection != null;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        } catch (Exception e) {
            logD("URL validation failed", e);
            downloadUrlIsValid = false;
        }
        return null;
    }
}
