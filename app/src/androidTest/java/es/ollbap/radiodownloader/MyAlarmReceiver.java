package es.ollbap.radiodownloader;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import static es.ollbap.radiodownloader.Util.logI;

/**
 * Created by ollbap on 1/13/18.
 */

public class MyAlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        logI("MyAlarmReceiver onReceive");
        Util.programNextAlarm(context);
        Util.startForegroundService(context);
    }
}
