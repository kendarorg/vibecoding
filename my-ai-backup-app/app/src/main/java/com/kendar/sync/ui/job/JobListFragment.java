package com.kendar.sync.ui.job;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.kendar.sync.R;
import com.kendar.sync.dao.BackupJobDao;
import com.kendar.sync.databinding.FragmentJobListBinding;
import com.kendar.sync.model.BackupJob;
import com.kendar.sync.service.BackupService;

import java.util.List;

/**
 * Fragment that displays the list of backup jobs
 */
public class JobListFragment extends Fragment implements JobAdapter.JobItemListener {
    private FragmentJobListBinding binding;
    private BackupJobDao jobDao;
    private JobAdapter adapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentJobListBinding.inflate(inflater, container, false);
        jobDao = new BackupJobDao(requireContext());

        // Set up RecyclerView
        RecyclerView recyclerView = binding.jobRecyclerView;
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        // Set up Add button
        binding.addJobButton.setOnClickListener(v -> {
            // Create empty job and open edit activity
            BackupJob newJob = new BackupJob();
            newJob.setName("New Backup Job");
            long jobId = jobDao.insertJob(newJob);

            Intent intent = new Intent(requireContext(), JobEditActivity.class);
            intent.putExtra("job_id", jobId);
            startActivity(intent);
        });

        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadJobs();
    }

    /**
     * Load all jobs from the database
     */
    private void loadJobs() {
        List<BackupJob> jobs = jobDao.getAllJobs();

        if (jobs.isEmpty()) {
            binding.emptyView.setVisibility(View.VISIBLE);
            binding.jobRecyclerView.setVisibility(View.GONE);
        } else {
            binding.emptyView.setVisibility(View.GONE);
            binding.jobRecyclerView.setVisibility(View.VISIBLE);

            adapter = new JobAdapter(requireContext(), jobs, this);
            binding.jobRecyclerView.setAdapter(adapter);
        }
    }

    @Override
    public void onViewJob(BackupJob job) {
        Intent intent = new Intent(requireContext(), JobDetailActivity.class);
        intent.putExtra("job_id", job.getId());
        startActivity(intent);
    }

    @Override
    public void onEditJob(BackupJob job) {
        Intent intent = new Intent(requireContext(), JobEditActivity.class);
        intent.putExtra("job_id", job.getId());
        startActivity(intent);
    }

    @Override
    public void onDeleteJob(BackupJob job) {
        jobDao.deleteJob(job.getId());
        Toast.makeText(requireContext(), "Job deleted", Toast.LENGTH_SHORT).show();
        loadJobs();
    }

    @Override
    public void onRunJob(BackupJob job) {
        // Start service and run specific job
        Intent serviceIntent = new Intent(requireContext(), BackupService.class);
        serviceIntent.setAction("RUN_SPECIFIC_JOB");
        serviceIntent.putExtra("job_id", job.getId());

        requireContext().startService(serviceIntent);
        Toast.makeText(requireContext(), "Job started", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
