package es.ollbap.radiodownloader.gui;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.preference.PreferenceManager;

import es.ollbap.radiodownloader.R;
import es.ollbap.radiodownloader.util.Util;

import static es.ollbap.radiodownloader.util.Util.programTestAlarm;

public class SecondFragment extends Fragment {

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_second, container, false);
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.button_second_back).setOnClickListener(v -> NavHostFragment.findNavController(SecondFragment.this)
                    .navigate(R.id.action_SecondFragment_to_FirstFragment));

        view.findViewById(R.id.button_test_10s).setOnClickListener(v -> programTestAlarm(v.getContext(), 1000*10));

        view.findViewById(R.id.button_test_clear_preferences).setOnClickListener(v -> {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(v.getContext());
            sharedPreferences.edit().clear().apply();
        });

        view.findViewById(R.id.button_test_configure_next_alarm).setOnClickListener(v -> Util.programNextAlarm(v.getContext()));

    }
}
