package org.kendar.sync.ui.jobslist;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import org.kendar.sync.R;
import org.kendar.sync.model.Job;

import java.util.UUID;

public class ShowJobFragment extends Fragment {

    private JobsListViewModel viewModel;
    private Job job;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Replace with the actual layout for showing a job
        return inflater.inflate(R.layout.fragment_show_job, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        viewModel = new ViewModelProvider(this).get(JobsListViewModel.class);
        
        String jobId = getArguments().getString("jobId");
        job = viewModel.getJobById(UUID.fromString(jobId));
        
        // TODO: Implement the show job functionality
    }
}