package es.ollbap.radiodownloader.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import es.ollbap.radiodownloader.util.Util;

import static es.ollbap.radiodownloader.util.Util.logI;

/**
 * Created by ollbap on 1/13/18.
 */

public class MyAlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        logI("Alarm event received");
        Util.programNextAlarm(context);
        Util.startForegroundService(context);
    }
}
