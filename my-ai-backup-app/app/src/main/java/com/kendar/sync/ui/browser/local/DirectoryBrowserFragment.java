package com.kendar.sync.ui.browser.local;

import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.kendar.sync.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DirectoryBrowserFragment extends Fragment implements DirectoryAdapter.DirectorySelectListener {

    private EditText pathEditText;
    private Button okButton;
    private Button cancelButton;
    private RecyclerView directoryRecyclerView;
    private DirectoryAdapter directoryAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // We need to modify the layout to include a RecyclerView
        return inflater.inflate(R.layout.fragment_directory_browser, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        pathEditText = view.findViewById(R.id.edit_path);
        okButton = view.findViewById(R.id.button_ok);
        cancelButton = view.findViewById(R.id.button_cancel);
        directoryRecyclerView = view.findViewById(R.id.directory_recycler_view);
        Button browseStorageButton = view.findViewById(R.id.button_browse_storage);

        // Setup RecyclerView
        directoryRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        directoryAdapter = new DirectoryAdapter(getMediaDirectories(), this);
        directoryRecyclerView.setAdapter(directoryAdapter);

        // Set initial path if provided
        if (getArguments() != null && getArguments().containsKey("currentPath")) {
            String currentPath = getArguments().getString("currentPath");
            if (currentPath != null && !currentPath.isEmpty()) {
                pathEditText.setText(currentPath);
            }
        }


        // Add browse storage button click listener
        browseStorageButton.setOnClickListener(v -> {

            StorageManager storageManager = (StorageManager) getContext().getSystemService(Context.STORAGE_SERVICE);
            var sv = storageManager.getStorageVolumes();
            Bundle args = new Bundle();
            args.putString("initialPath",
                    //Environment.getExternalStorageDirectory().getAbsolutePath()
                    "/storage"
                   //storageManager.getPrimaryStorageVolume().getDirectory().getAbsolutePath()
                    //"/storage/sdcard1" // Default path for testing, replace with actual storage path if needed
                    //System.getenv("SECONDARY_STORAGE")
                    );
            Navigation.findNavController(requireView()).navigate(
                    R.id.action_directoryBrowserFragment_to_storageBrowserFragment, args);
        });

        // Register fragment result listener for the storage browser
        getParentFragmentManager().setFragmentResultListener("storageBrowserResult",
                this, (requestKey, result) -> {
                    String selectedPath = result.getString("selectedPath");
                    if (selectedPath != null) {
                        pathEditText.setText(selectedPath);
                    }
                });

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

    @Override
    public void onDirectorySelected(String path) {
        pathEditText.setText(path);
    }

    private List<DirectoryItem> getMediaDirectories() {
        List<DirectoryItem> directories = new ArrayList<>();

        // Get external storage directory

        extractDirs(directories, Environment.getExternalStorageDirectory(), "External Storage");

        return directories;
    }

    private void extractDirs(List<DirectoryItem> directories, File externalStorage, String name) {
        // Add common media directories
        addDirectoryIfExists(directories, new File(externalStorage, Environment.DIRECTORY_DOWNLOADS), "Downloads");
        addDirectoryIfExists(directories, new File(externalStorage, Environment.DIRECTORY_DCIM), "Camera (DCIM)");
        addDirectoryIfExists(directories, new File(externalStorage, Environment.DIRECTORY_PICTURES), "Pictures");
        addDirectoryIfExists(directories, new File(externalStorage, Environment.DIRECTORY_MUSIC), "Music");
        addDirectoryIfExists(directories, new File(externalStorage, Environment.DIRECTORY_MOVIES), "Movies");
        addDirectoryIfExists(directories, new File(externalStorage, Environment.DIRECTORY_DOCUMENTS), "Documents");

        // Add WhatsApp media if exists
        File whatsAppImages = new File(externalStorage, "WhatsApp/Media/WhatsApp Images");
        addDirectoryIfExists(directories, whatsAppImages, "WhatsApp Images");

        File whatsAppVideo = new File(externalStorage, "WhatsApp/Media/WhatsApp Video");
        addDirectoryIfExists(directories, whatsAppVideo, "WhatsApp Video");

        // Add external storage root
        directories.add(new DirectoryItem(name, externalStorage.getAbsolutePath()));
    }

    private void addDirectoryIfExists(List<DirectoryItem> directories, File directory, String displayName) {
        if (directory.exists() && directory.isDirectory()) {
            directories.add(new DirectoryItem(displayName, directory.getAbsolutePath()));
        }
    }
}