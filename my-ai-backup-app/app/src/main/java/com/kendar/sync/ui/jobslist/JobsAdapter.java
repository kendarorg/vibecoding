package com.kendar.sync.ui.jobslist;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.kendar.sync.R;
import com.kendar.sync.model.Job;

import java.util.List;

public class JobsAdapter extends RecyclerView.Adapter<JobsAdapter.JobViewHolder> {
    private List<Job> jobs;
    private final OnJobActionListener listener;

    public interface OnJobActionListener {
        void onEditJob(Job job);
        void onDeleteJob(Job job);
        void onShowJob(Job job);
    }

    public JobsAdapter(List<Job> jobs, OnJobActionListener listener) {
        this.jobs = jobs;
        this.listener = listener;
    }

    @NonNull
    @Override
    public JobViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_job, parent, false);
        return new JobViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull JobViewHolder holder, int position) {
        Job job = jobs.get(position);
        holder.bind(job);
    }

    @Override
    public int getItemCount() {
        return jobs != null ? jobs.size() : 0;
    }

    public void updateJobs(List<Job> jobs) {
        this.jobs = jobs;
        notifyDataSetChanged();
    }

    class JobViewHolder extends RecyclerView.ViewHolder {
        private final TextView jobNameTextView;
        private final TextView lastExecutionTextView;
        private final TextView lastTransferredTextView;
        private final ImageButton editButton;
        private final ImageButton deleteButton;
        private final ImageButton showButton;

        public JobViewHolder(@NonNull View itemView) {
            super(itemView);
            jobNameTextView = itemView.findViewById(R.id.text_job_name);
            lastExecutionTextView = itemView.findViewById(R.id.text_last_execution);
            lastTransferredTextView = itemView.findViewById(R.id.text_last_transferred);
            editButton = itemView.findViewById(R.id.button_edit);
            deleteButton = itemView.findViewById(R.id.button_delete);
            showButton = itemView.findViewById(R.id.button_show);
        }

        public void bind(Job job) {
            jobNameTextView.setText(job.getName());
            lastExecutionTextView.setText("Last execution: " + job.getLastExecution());
            lastTransferredTextView.setText("Last transferred: " + job.getLastTransferred());

            editButton.setOnClickListener(v -> listener.onEditJob(job));
            deleteButton.setOnClickListener(v -> listener.onDeleteJob(job));
            showButton.setOnClickListener(v -> listener.onShowJob(job));
        }
    }
}