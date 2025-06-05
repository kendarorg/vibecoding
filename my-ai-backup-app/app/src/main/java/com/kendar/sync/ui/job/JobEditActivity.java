package com.kendar.sync.ui.job;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.kendar.sync.dao.BackupJobDao;
import com.kendar.sync.databinding.ActivityJobEditBinding;
import com.kendar.sync.model.BackupJob;
import com.kendar.sync.ui.directory.DirectoryExplorerActivity;
import com.kendar.sync.ui.schedule.ScheduleEditActivity;
import com.kendar.sync.ui.target.RemoteTargetExplorerActivity;
import com.kendar.sync.util.AlarmScheduler;

import java.util.Date;

/**
 * Activity for editing backup job details
 */
public class JobEditActivity extends AppCompatActivity {
    private static final int REQUEST_SELECT_DIRECTORY = 1;
    private static final int REQUEST_SELECT_TARGET = 2;
    private static final int REQUEST_EDIT_SCHEDULE = 3;

    private ActivityJobEditBinding binding;
    private BackupJobDao jobDao;
    private AlarmScheduler alarmScheduler;
    private long jobId;
    private BackupJob job;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityJobEditBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("Edit Backup Job");

        jobDao = new BackupJobDao(this);
        alarmScheduler = new AlarmScheduler(this);

        // Get job ID from intent
        jobId = getIntent().getLongExtra("job_id", -1);
        if (jobId == -1) {
            finish();
            return;
        }

        // Set up click listeners
        binding.saveButton.setOnClickListener(v -> saveJob());
        binding.exploreLocalButton.setOnClickListener(v -> openDirectoryExplorer());
        binding.exploreRemoteButton.setOnClickListener(v -> openRemoteTargetExplorer());
        binding.editScheduleButton.setOnClickListener(v -> openScheduleEditor());

        // Set up switches
        binding.wifiOnlySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (job != null) {
                job.setWifiOnly(isChecked);
            }
        });

        binding.chargingOnlySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (job != null) {
                job.setChargingOnly(isChecked);
            }
        });

        loadJobDetails();
    }

    private void loadJobDetails() {
        job = jobDao.getJob(jobId);
        if (job == null) {
            finish();
            return;
        }

        // Set values to UI
        binding.jobNameEditText.setText(job.getName());
        binding.localDirectoryEditText.setText(job.getLocalDirectory() != null ? job.getLocalDirectory() : "");
        binding.remoteAddressEditText.setText(job.getRemoteAddress() != null ? job.getRemoteAddress() : "");
        binding.remotePortEditText.setText(String.valueOf(job.getRemotePort() > 0 ? job.getRemotePort() : 22));
        binding.remoteTargetEditText.setText(job.getRemoteTarget() != null ? job.getRemoteTarget() : "");
        binding.loginEditText.setText(job.getLogin() != null ? job.getLogin() : "");
        binding.passwordEditText.setText(job.getPassword() != null ? job.getPassword() : "");

        binding.wifiOnlySwitch.setChecked(job.isWifiOnly());
        binding.chargingOnlySwitch.setChecked(job.isChargingOnly());
    }

    private void saveJob() {
        // Validate fields
        String name = binding.jobNameEditText.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter a job name", Toast.LENGTH_SHORT).show();
            return;
        }

        String localDirectory = binding.localDirectoryEditText.getText().toString().trim();
        if (localDirectory.isEmpty()) {
            Toast.makeText(this, "Please select a local directory", Toast.LENGTH_SHORT).show();
            return;
        }

        String remoteAddress = binding.remoteAddressEditText.getText().toString().trim();
        if (remoteAddress.isEmpty()) {
            Toast.makeText(this, "Please enter a remote server address", Toast.LENGTH_SHORT).show();
            return;
        }

        String remotePortStr = binding.remotePortEditText.getText().toString().trim();
        int remotePort;
        try {
            remotePort = Integer.parseInt(remotePortStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Please enter a valid port number", Toast.LENGTH_SHORT).show();
            return;
        }

        String remoteTarget = binding.remoteTargetEditText.getText().toString().trim();
        if (remoteTarget.isEmpty()) {
            Toast.makeText(this, "Please select a remote target", Toast.LENGTH_SHORT).show();
            return;
        }

        String login = binding.loginEditText.getText().toString().trim();
        String password = binding.passwordEditText.getText().toString();

        // Update job with new values
        job.setName(name);
        job.setLocalDirectory(localDirectory);
        job.setRemoteAddress(remoteAddress);
        job.setRemotePort(remotePort);
        job.setRemoteTarget(remoteTarget);
        job.setLogin(login);
        job.setPassword(password);

        // Calculate next scheduled run if not set
        if (job.getNextScheduledRun() == null) {
            Date nextRun = job.getSchedule().getNextRunTime(new Date());
            job.setNextScheduledRun(nextRun);
        }

        // Save to database
        jobDao.updateJob(job);

        // Schedule the job
        alarmScheduler.scheduleJob(job);

        Toast.makeText(this, "Job saved", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void openDirectoryExplorer() {
        Intent intent = new Intent(this, DirectoryExplorerActivity.class);
        startActivityForResult(intent, REQUEST_SELECT_DIRECTORY);
    }

    private void openRemoteTargetExplorer() {
        // Validate remote connection details
        String serverAddress = binding.remoteAddressEditText.getText().toString().trim();
        String portStr = binding.remotePortEditText.getText().toString().trim();
        String login = binding.loginEditText.getText().toString().trim();
        String password = binding.passwordEditText.getText().toString();

        if (serverAddress.isEmpty() || portStr.isEmpty() || login.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill in server address, port, login and password first", 
                    Toast.LENGTH_SHORT).show();
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid port number", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, RemoteTargetExplorerActivity.class);
        intent.putExtra("server_address", serverAddress);
        intent.putExtra("port", port);
        intent.putExtra("login", login);
        intent.putExtra("password", password);
        startActivityForResult(intent, REQUEST_SELECT_TARGET);
    }

    private void openScheduleEditor() {
        Intent intent = new Intent(this, ScheduleEditActivity.class);
        intent.putExtra("job_id", jobId);
        startActivityForResult(intent, REQUEST_EDIT_SCHEDULE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && data != null) {
            switch (requestCode) {
                case REQUEST_SELECT_DIRECTORY:
                    String selectedDirectory = data.getStringExtra("selected_directory");
                    binding.localDirectoryEditText.setText(selectedDirectory);
                    break;

                case REQUEST_SELECT_TARGET:
                    String selectedTarget = data.getStringExtra("selected_target");
                    binding.remoteTargetEditText.setText(selectedTarget);
                    break;

                case REQUEST_EDIT_SCHEDULE:
                    // No need to do anything, job will be updated directly in the database
                    break;
            }
        }
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
