package com.kendar.sync.util;

import android.content.Context;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.kendar.sync.model.Job;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class JobsFileUtil {
    private static final String FILE_NAME = "jobs.json";
    private final Gson gson;
    private final Context context;

    public JobsFileUtil(Context context) {
        this.context = context;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public List<Job> readJobs() {
        File file = new File(context.getFilesDir(), FILE_NAME);
        if (!file.exists()) {
            return new ArrayList<>();
        }

        try (FileReader reader = new FileReader(file)) {
            Type listType = new TypeToken<ArrayList<Job>>() {}.getType();
            List<Job> jobs = gson.fromJson(reader, listType);
            return jobs != null ? jobs : new ArrayList<>();
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public void saveJobs(List<Job> jobs) {
        File file = new File(context.getFilesDir(), FILE_NAME);
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(jobs, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addJob(Job job) {
        List<Job> jobs = readJobs();
        if (job.getId() == null) {
            job.setId(UUID.randomUUID());
        }
        jobs.add(job);
        saveJobs(jobs);
    }

    public void updateJob(Job job) {
        List<Job> jobs = readJobs();
        for (int i = 0; i < jobs.size(); i++) {
            if (jobs.get(i).getId().equals(job.getId())) {
                jobs.set(i, job);
                break;
            }
        }
        saveJobs(jobs);
    }

    public void deleteJob(UUID jobId) {
        List<Job> jobs = readJobs();
        jobs.removeIf(job -> job.getId().equals(jobId));
        saveJobs(jobs);
    }

    public Job getJobById(UUID jobId) {
        List<Job> jobs = readJobs();
        for (Job job : jobs) {
            if (job.getId().equals(jobId)) {
                return job;
            }
        }
        return null;
    }
}