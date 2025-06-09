package com.kendar.sync.ui.browser;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

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
import java.util.concurrent.Semaphore;

public class StorageBrowserFragment extends Fragment implements StorageBrowserAdapter.StorageItemClickListener {

    private RecyclerView recyclerView;
    private TextView currentPathTextView;
    private File currentDirectory;
    private StorageBrowserAdapter adapter;

    private void checkBasicStoragePermissions() {
        if (requireActivity().checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE,
                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    1001);
        }
    }

    private void requestManageStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                Uri uri = Uri.fromParts("package", requireActivity().getPackageName(), null);
                intent.setData(uri);
                startActivity(intent);
            } catch (Exception e) {
                semaphore.release();
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivity(intent);
            }
        }
    }

    private Semaphore semaphore = new Semaphore(1);

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        try {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            if (requestCode == 1001) {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Reload current directory after permission granted
                    //loadDirectory(currentDirectory);
                } else {
                    Toast.makeText(requireContext(), "Storage permission denied", Toast.LENGTH_SHORT).show();
                }
            }
        }finally {
            semaphore.release();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // For Android 11+, check if we have manage external storage permission
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    semaphore.acquire();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                requestManageStoragePermission();
            }
        } else {
            // For Android 10 and below
            checkBasicStoragePermissions();
        }
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

        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        currentDirectory = new File(initialPath);
        loadDirectory(currentDirectory);

        // Set up button click listeners
        buttonBack.setOnClickListener(v -> {
            File parent = currentDirectory.getParentFile();
            if ((parent!=null && parent.getAbsolutePath().equalsIgnoreCase("/storage")) ||
                    (parent != null && parent.canRead())) {
                currentDirectory = parent;
                loadDirectory(currentDirectory);
            }
        });

        buttonCancel.setOnClickListener(v -> {
            Navigation.findNavController(requireView()).navigateUp();
        });
    }

    private void loadDirectory(File directory) {
        currentPathTextView.setText(directory.getAbsolutePath());

        List<StorageItem> items = new ArrayList<>();
        File[] files = new File[]{};
        if(directory.getAbsolutePath().equals("/storage")) {
            StorageManager storageManager = (StorageManager) getContext().getSystemService(Context.STORAGE_SERVICE);
            var sv = storageManager.getStorageVolumes();
            List<File> result =new ArrayList<>();
            for(var item:sv){
                result.add(new File(item.getDirectory().getAbsolutePath()));
            }
            files= result.stream()
                    //.filter(File::exists)
                    //.filter(File::isDirectory)
                    .toArray(File[]::new);
            //File dcimPath = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) + "/Camera");

        }else{
            if (directory == null || !directory.exists() || !directory.isDirectory() || !directory.canRead()) {
                return;
            }

            files = directory.listFiles();
        }


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