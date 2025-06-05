package com.kendar.sync.ui.directory;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.kendar.sync.databinding.ActivityDirectoryExplorerBinding;
import com.kendar.sync.model.DirectoryInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Activity for exploring and selecting directories
 */
public class DirectoryExplorerActivity extends AppCompatActivity implements DirectoryAdapter.DirectoryClickListener {
    private ActivityDirectoryExplorerBinding binding;
    private DirectoryAdapter adapter;
    private File currentDirectory;
    private List<DirectoryInfo> directories;
    private DirectoryInfo selectedDirectory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityDirectoryExplorerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("Select Directory");

        // Set up RecyclerView
        binding.directoryRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        directories = new ArrayList<>();
        adapter = new DirectoryAdapter(this, directories, this);
        binding.directoryRecyclerView.setAdapter(adapter);

        // Set up button click listeners
        binding.selectButton.setOnClickListener(v -> selectCurrentDirectory());
        binding.parentDirectoryButton.setOnClickListener(v -> navigateToParent());

        // Start with external storage directory
        navigateTo(Environment.getExternalStorageDirectory());
    }

    private void navigateTo(File directory) {
        if (!directory.exists() || !directory.isDirectory()) {
            Toast.makeText(this, "Cannot access directory", Toast.LENGTH_SHORT).show();
            return;
        }

        currentDirectory = directory;
        binding.currentPathTextView.setText(directory.getAbsolutePath());

        // Clear selection
        selectedDirectory = null;
        binding.selectButton.setEnabled(false);

        // List subdirectories
        loadDirectories();
    }

    private void loadDirectories() {
        directories.clear();

        File[] files = currentDirectory.listFiles();
        if (files != null) {
            Arrays.sort(files, (f1, f2) -> {
                // Directories first, then sort by name
                if (f1.isDirectory() && !f2.isDirectory()) return -1;
                if (!f1.isDirectory() && f2.isDirectory()) return 1;
                return f1.getName().compareToIgnoreCase(f2.getName());
            });

            for (File file : files) {
                if (file.isDirectory() && !file.isHidden()) {
                    // Count files in directory
                    int fileCount = 0;
                    File[] subFiles = file.listFiles();
                    if (subFiles != null) {
                        fileCount = subFiles.length;
                    }

                    directories.add(new DirectoryInfo(file, fileCount));
                }
            }
        }

        // Show empty view if no directories
        if (directories.isEmpty()) {
            binding.emptyView.setVisibility(View.VISIBLE);
        } else {
            binding.emptyView.setVisibility(View.GONE);
        }

        adapter.notifyDataSetChanged();
    }

    private void navigateToParent() {
        if (currentDirectory != null) {
            File parent = currentDirectory.getParentFile();
            if (parent != null) {
                navigateTo(parent);
            } else {
                Toast.makeText(this, "Already at root directory", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void selectCurrentDirectory() {
        if (selectedDirectory != null) {
            Intent resultIntent = new Intent();
            resultIntent.putExtra("selected_directory", selectedDirectory.getPath());
            setResult(RESULT_OK, resultIntent);
            finish();
        }
    }

    @Override
    public void onDirectoryClick(DirectoryInfo directory, int position) {
        // Navigate into directory on click
        navigateTo(directory.getDirectory());
    }

    @Override
    public void onDirectorySelect(DirectoryInfo directory, int position) {
        // Clear previous selection
        if (selectedDirectory != null) {
            selectedDirectory.setSelected(false);
        }

        // Set new selection
        selectedDirectory = directory;
        selectedDirectory.setSelected(true);
        adapter.notifyDataSetChanged();

        // Enable select button
        binding.selectButton.setEnabled(true);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
