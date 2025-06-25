package org.kendar.sync.ui.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import org.kendar.sync.databinding.FragmentHomeBinding;

import java.util.UUID;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;

    private static final String PREFS_NAME = "app_settings";
    private static final String DEVICE_ID_KEY = "device_id";

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        HomeViewModel homeViewModel =
                new ViewModelProvider(this).get(HomeViewModel.class);

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();


        final EditText editDeviceId = binding.editDeviceId;
        final Button buttonSaveDeviceId = binding.buttonSaveDeviceId;

        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String deviceId = prefs.getString(DEVICE_ID_KEY, null);
        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString();
            prefs.edit().putString(DEVICE_ID_KEY, deviceId).apply();
        }
        editDeviceId.setText(deviceId);

        buttonSaveDeviceId.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String newId = editDeviceId.getText().toString();
                prefs.edit().putString(DEVICE_ID_KEY, newId).apply();
            }
        });

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}