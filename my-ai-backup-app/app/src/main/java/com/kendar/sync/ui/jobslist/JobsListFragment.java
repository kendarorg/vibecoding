package com.kendar.sync.ui.jobslist;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.kendar.sync.R;
import com.kendar.sync.model.Job;

import java.util.ArrayList;
import java.util.List;

public class JobsListFragment extends Fragment implements JobsAdapter.OnJobActionListener {

    private JobsListViewModel viewModel;
    private RecyclerView recyclerView;
    private JobsAdapter adapter;
    private TextView emptyListTextView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_jobs_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(JobsListViewModel.class);

        recyclerView = view.findViewById(R.id.recycler_jobs);
        emptyListTextView = view.findViewById(R.id.text_empty_list);
        FloatingActionButton addButton = view.findViewById(R.id.fab_add_job);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new JobsAdapter(new ArrayList<>(), this);
        recyclerView.setAdapter(adapter);

        viewModel.getJobs().observe(getViewLifecycleOwner(), this::updateJobsList);

        addButton.setOnClickListener(v -> navigateToAddJob());
    }

    private void updateJobsList(List<Job> jobs) {
        adapter.updateJobs(jobs);
        
        if (jobs == null || jobs.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyListTextView.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyListTextView.setVisibility(View.GONE);
        }
    }

    private void navigateToAddJob() {
        NavController navController = Navigation.findNavController(requireView());
        navController.navigate(R.id.action_jobsListFragment_to_addJobFragment);
    }

    @Override
    public void onEditJob(Job job) {
        NavController navController = Navigation.findNavController(requireView());
        Bundle args = new Bundle();
        args.putString("jobId", job.getId().toString());
        navController.navigate(R.id.action_jobsListFragment_to_addJobFragment, args);
    }

    @Override
    public void onResume() {
        super.onResume();
        viewModel.loadJobs();
    }

    @Override
    public void onDeleteJob(Job job) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Job")
                .setMessage("Are you sure you want to delete this job?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    viewModel.deleteJob(job.getId());
                })
                .setNegativeButton("No", null)
                .show();
    }

    @Override
    public void onShowJob(Job job) {
        NavController navController = Navigation.findNavController(requireView());
        Bundle args = new Bundle();
        args.putString("jobId", job.getId().toString());
        navController.navigate(R.id.action_jobsListFragment_to_showJobFragment, args);
    }
}