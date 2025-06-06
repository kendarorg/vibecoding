package com.kendar.sync.ui.schedule;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.kendar.sync.R;

public class ScheduleFragment extends Fragment {

    private EditText scheduleEditText;
    private Button okButton;
    private Button cancelButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_schedule, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        scheduleEditText = view.findViewById(R.id.edit_schedule);
        okButton = view.findViewById(R.id.button_ok);
        cancelButton = view.findViewById(R.id.button_cancel);

        // Set initial schedule if provided
        if (getArguments() != null && getArguments().containsKey("currentSchedule")) {
            String currentSchedule = getArguments().getString("currentSchedule");
            if (currentSchedule != null && !currentSchedule.isEmpty()) {
                scheduleEditText.setText(currentSchedule);
            }
        }

        okButton.setOnClickListener(v -> {
            Bundle result = new Bundle();
            result.putString("scheduleTime", scheduleEditText.getText().toString());
            getParentFragmentManager().setFragmentResult("scheduleResult", result);
            Navigation.findNavController(requireView()).navigateUp();
        });

        cancelButton.setOnClickListener(v -> {
            Bundle result = new Bundle();
            result.putString("scheduleTime", null);
            getParentFragmentManager().setFragmentResult("scheduleResult", result);
            Navigation.findNavController(requireView()).navigateUp();
        });
    }
}