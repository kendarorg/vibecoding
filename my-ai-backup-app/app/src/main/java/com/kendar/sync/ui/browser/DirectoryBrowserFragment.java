package com.kendar.sync.ui.browser;

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

public class DirectoryBrowserFragment extends Fragment {

    private EditText pathEditText;
    private Button okButton;
    private Button cancelButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_directory_browser, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        pathEditText = view.findViewById(R.id.edit_path);
        okButton = view.findViewById(R.id.button_ok);
        cancelButton = view.findViewById(R.id.button_cancel);

        // Set initial path if provided
        if (getArguments() != null && getArguments().containsKey("currentPath")) {
            String currentPath = getArguments().getString("currentPath");
            if (currentPath != null && !currentPath.isEmpty()) {
                pathEditText.setText(currentPath);
            }
        }

        okButton.setOnClickListener(v -> {
            Bundle result = new Bundle();
            result.putString("localSource", pathEditText.getText().toString());
            getParentFragmentManager().setFragmentResult("directoryBrowserResult", result);
            Navigation.findNavController(requireView()).navigateUp();
        });

        cancelButton.setOnClickListener(v -> {
            Bundle result = new Bundle();
            result.putString("localSource", null);
            getParentFragmentManager().setFragmentResult("directoryBrowserResult", result);
            Navigation.findNavController(requireView()).navigateUp();
        });
    }
}