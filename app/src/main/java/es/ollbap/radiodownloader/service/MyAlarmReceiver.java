package es.ollbap.radiodownloader.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import es.ollbap.radiodownloader.util.Util;

import static es.ollbap.radiodownloader.util.Util.logD;

/**
 * Created by ollbap on 1/13/18.
 */

public class MyAlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        logD("MyAlarmReceiver onReceive");
        Util.programNextAlarm(context);
        Util.startForegroundService(context);
    }
}
