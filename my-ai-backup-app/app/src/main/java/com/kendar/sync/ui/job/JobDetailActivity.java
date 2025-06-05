package com.kendar.sync.ui.job;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.kendar.sync.dao.BackupJobDao;
import com.kendar.sync.databinding.ActivityJobDetailBinding;
import com.kendar.sync.model.BackupJob;
import com.kendar.sync.model.Schedule;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Activity for displaying backup job details
 */
public class JobDetailActivity extends AppCompatActivity {
    private ActivityJobDetailBinding binding;
    private BackupJobDao jobDao;
    private long jobId;
    private BackupJob job;
    private SimpleDateFormat dateFormat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityJobDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("Backup Job Details");

        jobDao = new BackupJobDao(this);
        dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

        // Get job ID from intent
        jobId = getIntent().getLongExtra("job_id", -1);
        if (jobId == -1) {
            finish();
            return;
        }

        binding.editButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, JobEditActivity.class);
            intent.putExtra("job_id", jobId);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadJobDetails();
    }

    private void loadJobDetails() {
        job = jobDao.getJob(jobId);
        if (job == null) {
            finish();
            return;
        }

        // Set job details
        binding.jobNameTextView.setText(job.getName());
        binding.localDirectoryTextView.setText(job.getLocalDirectory() != null ? 
                job.getLocalDirectory() : "Not set");

        String remoteInfo = String.format("%s:%d", 
                job.getRemoteAddress() != null ? job.getRemoteAddress() : "Not set", 
                job.getRemotePort());
        binding.remoteServerTextView.setText(remoteInfo);

        binding.remoteTargetTextView.setText(job.getRemoteTarget() != null ? 
                job.getRemoteTarget() : "Not set");

        // Set schedule details
        Schedule schedule = job.getSchedule();
        if (schedule != null) {
            String scheduleType;
            switch (schedule.getType()) {
                case MONTHLY:
                    scheduleType = "Monthly";
                    break;
                case WEEKLY:
                    scheduleType = "Weekly";
                    break;
                case SPECIFIC_TIME:
                    scheduleType = "Specific time";
                    break;
                default:
                    scheduleType = "Unknown";
            }
            binding.scheduleTypeTextView.setText(scheduleType);

            binding.conditionsTextView.setText(
                    "Run only on WiFi: " + (job.isWifiOnly() ? "Yes" : "No") + "\n" +
                    "Run only when charging: " + (job.isChargingOnly() ? "Yes" : "No")
            );
        }

        // Set last run info
        if (job.getLastRunTime() != null) {
            binding.lastRunLayout.setVisibility(View.VISIBLE);
            binding.lastRunTimeTextView.setText(dateFormat.format(job.getLastRunTime()));

            // Format duration
            long durationMs = job.getLastRunDuration();
            long minutes = (durationMs / 1000) / 60;
            long seconds = (durationMs / 1000) % 60;
            binding.lastRunDurationTextView.setText(
                    String.format(Locale.getDefault(), "%d min %d sec", minutes, seconds));
        } else {
            binding.lastRunLayout.setVisibility(View.GONE);
        }

        // Set next run info
        if (job.getNextScheduledRun() != null) {
            binding.nextRunLayout.setVisibility(View.VISIBLE);
            binding.nextRunTimeTextView.setText(dateFormat.format(job.getNextScheduledRun()));
        } else {
            binding.nextRunLayout.setVisibility(View.GONE);
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
