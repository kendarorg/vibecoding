package com.kendar.sync.ui.target;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.kendar.sync.api.BackupClient;
import com.kendar.sync.databinding.ActivityRemoteTargetExplorerBinding;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Activity for exploring and selecting remote backup targets
 */
public class RemoteTargetExplorerActivity extends AppCompatActivity implements TargetAdapter.TargetClickListener {
    private ActivityRemoteTargetExplorerBinding binding;
    private TargetAdapter adapter;
    private List<String> targets;
    private String selectedTarget;
    private BackupClient backupClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityRemoteTargetExplorerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("Select Remote Target");

        // Create a fake backup client for demo purposes
        backupClient = createFakeBackupClient();

        // Set up RecyclerView
        binding.targetRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        targets = new ArrayList<>();
        adapter = new TargetAdapter(this, targets, this);
        binding.targetRecyclerView.setAdapter(adapter);

        // Set up button click listeners
        binding.selectButton.setOnClickListener(v -> selectCurrentTarget());

        // Get connection details from intent
        String serverAddress = getIntent().getStringExtra("server_address");
        int port = getIntent().getIntExtra("port", 22);
        String login = getIntent().getStringExtra("login");
        String password = getIntent().getStringExtra("password");

        // Load targets
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.emptyView.setVisibility(View.GONE);

        backupClient.fetchRemoteTargets(serverAddress, port, login, password, new BackupClient.RemoteTargetsListener() {
            @Override
            public void onTargetsReceived(String[] targetArray) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);

                    targets.clear();
                    targets.addAll(Arrays.asList(targetArray));
                    adapter.notifyDataSetChanged();

                    if (targets.isEmpty()) {
                        binding.emptyView.setVisibility(View.VISIBLE);
                    } else {
                        binding.emptyView.setVisibility(View.GONE);
                    }
                });
            }

            @Override
            public void onTargetsFetchFailed(String errorMessage) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    binding.emptyView.setVisibility(View.VISIBLE);
                    binding.emptyView.setText("Error: " + errorMessage);
                    Toast.makeText(RemoteTargetExplorerActivity.this, 
                            "Failed to fetch targets: " + errorMessage, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void selectCurrentTarget() {
        if (selectedTarget != null) {
            Intent resultIntent = new Intent();
            resultIntent.putExtra("selected_target", selectedTarget);
            setResult(RESULT_OK, resultIntent);
            finish();
        }
    }

    @Override
    public void onTargetSelected(String target, int position) {
        selectedTarget = target;
        adapter.setSelectedPosition(position);
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

    /**
     * Create a fake backup client implementation
     * This is just a placeholder as the real implementation will be provided by someone else
     */
    private BackupClient createFakeBackupClient() {
        return new BackupClient() {
            @Override
            public boolean startBackup(BackupJob job, BackupProgressListener listener) {
                return false; // Not used in this activity
            }

            @Override
            public boolean stopBackup() {
                return false; // Not used in this activity
            }

            @Override
            public void fetchRemoteTargets(String serverAddress, int port, String login, 
                                          String password, RemoteTargetsListener listener) {
                // Simulate network request
                new Thread(() -> {
                    try {
                        Thread.sleep(1500); // Simulate network delay

                        // Generate fake targets
                        String[] targets = {
                            "Backup_Daily",
                            "Backup_Weekly",
                            "Backup_Monthly",
                            "Photos",
                            "Documents",
                            "Archives",
                            "Music"
                        };

                        listener.onTargetsReceived(targets);
                    } catch (InterruptedException e) {
                        listener.onTargetsFetchFailed("Operation interrupted");
                    }
                }).start();
            }
        };
    }
}
