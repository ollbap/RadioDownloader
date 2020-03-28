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

        view.findViewById(R.id.button_second_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
            NavHostFragment.findNavController(SecondFragment.this)
                        .navigate(R.id.action_SecondFragment_to_FirstFragment);
            }
        });

        view.findViewById(R.id.button_test_10s).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                programTestAlarm(view.getContext(), 1000*10);
            }
        });

        view.findViewById(R.id.button_test_clear_preferences).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(view.getContext());
                sharedPreferences.edit().clear().apply();
            }
        });

        view.findViewById(R.id.button_test_configure_next_alarm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
            Util.programNextAlarm(view.getContext());
            }
        });

    }
}
