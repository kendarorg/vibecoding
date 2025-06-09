package com.kendar.sync.ui.browser;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.kendar.sync.R;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class StorageBrowserFragment extends Fragment implements StorageBrowserAdapter.StorageItemClickListener {

    private RecyclerView recyclerView;
    private TextView currentPathTextView;
    private File currentDirectory;
    private StorageBrowserAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_storage_browser, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.recycler_view_storage);
        currentPathTextView = view.findViewById(R.id.text_current_path);
        Button buttonBack = view.findViewById(R.id.button_back);
        Button buttonCancel = view.findViewById(R.id.button_cancel);

        // Set up RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new StorageBrowserAdapter(new ArrayList<>(), this);
        recyclerView.setAdapter(adapter);

        // Get initial path from arguments
        String initialPath = getArguments() != null ?
                getArguments().getString("initialPath") :
                "/storage/emulated/0";

        currentDirectory = new File(initialPath);
        loadDirectory(currentDirectory);

        // Set up button click listeners
        buttonBack.setOnClickListener(v -> {
            File parent = currentDirectory.getParentFile();
            if (parent != null && parent.canRead()) {
                currentDirectory = parent;
                loadDirectory(currentDirectory);
            }
        });

        buttonCancel.setOnClickListener(v -> {
            Navigation.findNavController(requireView()).navigateUp();
        });
    }

    private void loadDirectory(File directory) {
        if (directory == null || !directory.exists() || !directory.isDirectory() || !directory.canRead()) {
            return;
        }

        currentPathTextView.setText(directory.getAbsolutePath());

        List<StorageItem> items = new ArrayList<>();

        File[] files = directory.listFiles();
        if (files != null) {
            Arrays.sort(files, (f1, f2) -> {
                // Directories first, then files
                if (f1.isDirectory() && !f2.isDirectory()) {
                    return -1;
                } else if (!f1.isDirectory() && f2.isDirectory()) {
                    return 1;
                } else {
                    return f1.getName().compareToIgnoreCase(f2.getName());
                }
            });

            for (File file : files) {
                if (!file.isHidden()) {
                    items.add(new StorageItem(file.getName(), file.getAbsolutePath(), file.isDirectory()));
                }
            }
        }

        adapter.updateItems(items);
    }

    @Override
    public void onItemClick(StorageItem item) {
        if (item.isDirectory()) {
            currentDirectory = new File(item.getPath());
            loadDirectory(currentDirectory);
        }
    }

    @Override
    public void onSelectDirectoryClick(StorageItem item) {
        if (item.isDirectory()) {
            Bundle result = new Bundle();
            result.putString("selectedPath", item.getPath());
            getParentFragmentManager().setFragmentResult("storageBrowserResult", result);
            Navigation.findNavController(requireView()).navigateUp();
        }
    }
}