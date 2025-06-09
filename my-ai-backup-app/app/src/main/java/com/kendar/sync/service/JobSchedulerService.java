package com.kendar.sync.service;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.kendar.sync.model.Job;
import com.kendar.sync.util.JobsFileUtil;

import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class JobSchedulerService extends Service {
    private static final String TAG = "JobSchedulerService";
    private static final long CHECK_INTERVAL_MS = 60000; // Check every minute

    private JobsFileUtil jobsFileUtil;
    private ExecutorService executorService;
    private Handler handler;
    private Set<UUID> runningJobs = new HashSet<>();
    private ConnectivityManager connectivityManager;
    private boolean isWifiConnected = false;
    private boolean isCharging = false;

    // ConnectivityManager callback for monitoring WiFi status
    private ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            isWifiConnected = isWifiConnected();
            if (isWifiConnected) {
                checkAndRunWifiJobs();
            }
        }

        @Override
        public void onLost(@NonNull Network network) {
            isWifiConnected = isWifiConnected();
        }
    };

    // BroadcastReceiver for monitoring battery charging status
    private BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean newChargingState = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL;

            if (!isCharging && newChargingState) {
                isCharging = true;
                checkAndRunChargingJobs();
            } else {
                isCharging = newChargingState;
            }
        }
    };

    // Runnable that periodically checks jobs that need to be run
    private Runnable jobCheckRunnable = new Runnable() {
        @Override
        public void run() {
            checkAndRunScheduledJobs();
            handler.postDelayed(this, CHECK_INTERVAL_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "JobSchedulerService created");

        jobsFileUtil = new JobsFileUtil(this);
        executorService = Executors.newFixedThreadPool(3); // Limit concurrent job executions
        handler = new Handler(Looper.getMainLooper());

        // Setup network monitoring
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build();
        connectivityManager.registerNetworkCallback(request, networkCallback);

        // Setup battery monitoring
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(batteryReceiver, filter);

        // Start periodic job checking
        handler.post(jobCheckRunnable);

        // Check initial states
        isWifiConnected = isWifiConnected();
        isCharging = isCharging();

        // Start jobs that should run on startup
        checkAndRunStartupJobs();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "JobSchedulerService started");
        return START_STICKY; // Service will be explicitly restarted if killed
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "JobSchedulerService destroyed");
        handler.removeCallbacks(jobCheckRunnable);
        connectivityManager.unregisterNetworkCallback(networkCallback);
        unregisterReceiver(batteryReceiver);
        executorService.shutdown();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private boolean isWifiConnected() {
        if (connectivityManager == null) return false;

        Network network = connectivityManager.getActiveNetwork();
        if (network == null) return false;

        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
        return capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }

    private boolean isCharging() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, filter);
        if (batteryStatus == null) return false;

        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL;
    }

    private void checkAndRunStartupJobs() {
        List<Job> jobs = jobsFileUtil.readJobs();
        for (Job job : jobs) {
            if (job.retrieveIsOnStartup() && !isJobRunning(job.getId())) {
                executeJob(job);
            }
        }
    }

    private void checkAndRunWifiJobs() {
        List<Job> jobs = jobsFileUtil.readJobs();
        for (Job job : jobs) {
            if (job.retrieveIsOnWifiOnly() && !isJobRunning(job.getId())) {
                executeJob(job);
            }
        }
    }

    private void checkAndRunChargingJobs() {
        List<Job> jobs = jobsFileUtil.readJobs();
        for (Job job : jobs) {
            if (job.retrieveIsOnChargeOnly() && !isJobRunning(job.getId())) {
                executeJob(job);
            }
        }
    }

    private void checkAndRunScheduledJobs() {
        List<Job> jobs = jobsFileUtil.readJobs();
        Calendar now = Calendar.getInstance();

        for (Job job : jobs) {
            if (isJobRunning(job.getId())) {
                continue; // Skip if already running
            }

            // Skip jobs that are wifi-only or charge-only as they're handled separately
            if (job.retrieveIsOnWifiOnly() || job.retrieveIsOnChargeOnly() || job.retrieveIsOnStartup()) {
                continue;
            }

            Calendar nextRun = job.retrieveNextScheduleTime();
            if (nextRun != null && !nextRun.after(now)) {
                executeJob(job);
            }
        }
    }

    private synchronized boolean isJobRunning(UUID jobId) {
        return runningJobs.contains(jobId);
    }

    private void executeJob(Job job) {
        if (isJobRunning(job.getId())) {
            return;
        }

        UUID jobId = job.getId();
        runningJobs.add(jobId);

        executorService.execute(() -> {
            try {
                Log.d(TAG, "Starting job: " + job.getName());

                // Here is where you'd implement the actual job execution logic
                // For example, connecting to the server, transferring files, etc.

                // Simulate job execution
                // TODO: Replace with actual implementation
                Thread.sleep(5000); // Simulate work for 5 seconds

                // Update job with execution details
                job.setLastExecution(Calendar.getInstance().getTime().toString());
                // job.setLastTransferred(...); // Set based on actual bytes transferred

                jobsFileUtil.updateJob(job);
                Log.d(TAG, "Finished job: " + job.getName());
            } catch (Exception e) {
                Log.e(TAG, "Error executing job: " + job.getName(), e);
            } finally {
                synchronized (JobSchedulerService.this) {
                    runningJobs.remove(jobId);
                }
            }
        });
    }
}