package es.ollbap.radiodownloader.gui.settings;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import es.ollbap.radiodownloader.R;
import es.ollbap.radiodownloader.util.Util;

import static java.util.Objects.requireNonNull;

public class SettingsFragment extends PreferenceFragmentCompat implements
        SharedPreferences.OnSharedPreferenceChangeListener {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.preference_main);
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireNonNull(this.getContext()));
        Util.applyConfigurationChanges(this.getContext());
        prefs.registerOnSharedPreferenceChangeListener(this);
        updateBatteryOptimizationsStatus();
    }

    @Override
    public void onPause() {
        super.onPause();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireNonNull(this.getContext()));
        prefs.unregisterOnSharedPreferenceChangeListener(this);
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Util.applyConfigurationChanges(this.getContext());
    }

    private void updateBatteryOptimizationsStatus() {
        Preference batteryOptimization = findPreference("battery_optimization_disable");
        assert batteryOptimization != null;
        batteryOptimization.setOnPreferenceClickListener(p -> openBatteryOptimizationSettings());
        batteryOptimization.setTitle("Disable battery optimizations: " +
                Util.checkIsIgnoringBatteryOptimization(requireNonNull(this.getContext())));
    }

    private boolean openBatteryOptimizationSettings() {
        Util.openPowerSettings(requireNonNull(this.getContext()));
        return true;
    }
}

