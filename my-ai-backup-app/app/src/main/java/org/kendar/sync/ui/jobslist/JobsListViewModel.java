package org.kendar.sync.ui.jobslist;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.kendar.sync.model.Job;
import org.kendar.sync.util.JobsFileUtil;

import java.util.List;
import java.util.UUID;

public class JobsListViewModel extends AndroidViewModel {
    private final JobsFileUtil jobsFileUtil;
    private final MutableLiveData<List<Job>> jobsLiveData = new MutableLiveData<>();

    public JobsListViewModel(@NonNull Application application) {
        super(application);
        jobsFileUtil = new JobsFileUtil(application);
        loadJobs();
    }

    public LiveData<List<Job>> getJobs() {
        return jobsLiveData;
    }

    public void loadJobs() {
        List<Job> jobs = jobsFileUtil.readJobs();
        jobsLiveData.setValue(jobs);
    }

    public void addJob(Job job) {
        jobsFileUtil.addJob(job);
        loadJobs();
    }

    public void updateJob(Job job) {
        jobsFileUtil.updateJob(job);
        loadJobs();
    }

    public void deleteJob(UUID jobId) {
        jobsFileUtil.deleteJob(jobId);
        loadJobs();
    }

    public Job getJobById(UUID jobId) {
        return jobsFileUtil.getJobById(jobId);
    }
}