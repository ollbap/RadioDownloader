package es.ollbap.radiodownloader;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Created by ollbap on 1/13/18.
 */

public class ServiceStarter extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Util.logI("On boot receive executed");
        Util.programNextAlarm(context);
    }
}
