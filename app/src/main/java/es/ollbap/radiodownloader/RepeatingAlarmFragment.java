/*
* Copyright 2013 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package es.ollbap.radiodownloader;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.fragment.app.Fragment;

import java.text.SimpleDateFormat;
import java.util.Calendar;

import static es.ollbap.radiodownloader.Util.logI;
import static es.ollbap.radiodownloader.Util.programNextAlarm;
import static es.ollbap.radiodownloader.Util.programTestAlarm;

public class RepeatingAlarmFragment extends Fragment {

    // This value is defined and consumed by app code, so any value will work.
    // There's no significance to this sample using 0.
    public static final int REQUEST_CODE = 0;
    private boolean weekend;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == R.id.start_download) {
            Util.startForegroundService(getActivity());
            logI("Download started.");
        }

        if(item.getItemId() == R.id.cancel_download) {
            Util.stopForegroundService(getActivity());
            logI("Cancel download.");
        }

        if (item.getItemId() == R.id.program_alarms || item.getItemId() == R.id.program_test_alarm || item.getItemId() == R.id.program_test_1h_alarm) {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            Calendar nextAlarmTime = null;
            if(item.getItemId() == R.id.program_alarms) {
                nextAlarmTime = programNextAlarm(getActivity());
            }

            if(item.getItemId() == R.id.program_test_alarm) {
                nextAlarmTime = programTestAlarm(getActivity(), 1000*10);
            }

            if(item.getItemId() == R.id.program_test_1h_alarm) {
                nextAlarmTime = programTestAlarm(getActivity(), 1000*30*60);
            }
            if (nextAlarmTime != null) {
                logI("Alarm programmed at: "+format.format(nextAlarmTime.getTime()));
            }
        }

        return true;
    }


}
