package es.ollbap.radiodownloader.gui;

import android.os.Bundle;

import androidx.preference.PreferenceFragmentCompat;

import es.ollbap.radiodownloader.R;

public class SettingsFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.preference_main);
    }
}

