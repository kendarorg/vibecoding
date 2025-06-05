package com.kendar.sync.ui.job;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.kendar.sync.R;
import com.kendar.sync.model.BackupJob;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/**
 * Adapter for displaying backup jobs in a RecyclerView
 */
public class JobAdapter extends RecyclerView.Adapter<JobAdapter.JobViewHolder> {
    private final Context context;
    private final List<BackupJob> jobs;
    private final JobItemListener listener;
    private final SimpleDateFormat dateFormat;

    public interface JobItemListener {
        void onViewJob(BackupJob job);
        void onEditJob(BackupJob job);
        void onDeleteJob(BackupJob job);
        void onRunJob(BackupJob job);
    }

    public JobAdapter(Context context, List<BackupJob> jobs, JobItemListener listener) {
        this.context = context;
        this.jobs = jobs;
        this.listener = listener;
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
    }

    @NonNull
    @Override
    public JobViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_backup_job, parent, false);
        return new JobViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull JobViewHolder holder, int position) {
        BackupJob job = jobs.get(position);
        holder.bind(job);
    }

    @Override
    public int getItemCount() {
        return jobs.size();
    }

    class JobViewHolder extends RecyclerView.ViewHolder {
        private final TextView nameTextView;
        private final TextView nextRunTextView;
        private final Button viewButton;
        private final Button editButton;
        private final Button deleteButton;
        private final Button runButton;

        public JobViewHolder(@NonNull View itemView) {
            super(itemView);
            nameTextView = itemView.findViewById(R.id.jobNameTextView);
            nextRunTextView = itemView.findViewById(R.id.nextRunTextView);
            viewButton = itemView.findViewById(R.id.viewButton);
            editButton = itemView.findViewById(R.id.editButton);
            deleteButton = itemView.findViewById(R.id.deleteButton);
            runButton = itemView.findViewById(R.id.runButton);
        }

        public void bind(BackupJob job) {
            nameTextView.setText(job.getName());

            if (job.getNextScheduledRun() != null) {
                nextRunTextView.setText("Next run: " + dateFormat.format(job.getNextScheduledRun()));
                nextRunTextView.setVisibility(View.VISIBLE);
            } else {
                nextRunTextView.setVisibility(View.GONE);
            }

            viewButton.setOnClickListener(v -> listener.onViewJob(job));
            editButton.setOnClickListener(v -> listener.onEditJob(job));
            deleteButton.setOnClickListener(v -> listener.onDeleteJob(job));
            runButton.setOnClickListener(v -> listener.onRunJob(job));
        }
    }
}
