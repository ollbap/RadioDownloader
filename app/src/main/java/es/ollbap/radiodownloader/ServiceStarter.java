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
        String action = intent.getAction();
        Util.logI("Received system intent: " + action);
        if (action != null) {
            switch (action) {
                case Intent.ACTION_BOOT_COMPLETED:
                    Util.logI("On boot receive executed");
                    Util.programNextAlarm(context);
                    break;
            }
        }

    }
}
