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

import org.kendar.sync.client.CommandLineArgs;
import org.kendar.sync.client.SyncClient;

import java.util.Calendar;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

public class JobSchedulerService extends Service {
    private static final String TAG = "JobSchedulerService";
    private static final long CHECK_INTERVAL_MS = 60000; // Check every minute

    private JobsFileUtil jobsFileUtil;
    private ExecutorService executorService;
    private Handler handler;
    private AtomicReference<UUID> runningJobId = new AtomicReference<>(null);
    private ConcurrentLinkedQueue<UUID> toRunJob = new ConcurrentLinkedQueue<>();
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
            var nextScheduleTime = job.retrieveNextScheduleTime();
            if (nextScheduleTime==null && job.retrieveIsOnWifiOnly() && !isJobRunning(job.getId())) {
                executeJob(job);
            }
        }
    }

    private void checkAndRunChargingJobs() {
        List<Job> jobs = jobsFileUtil.readJobs();
        for (Job job : jobs) {
            var nextScheduleTime = job.retrieveNextScheduleTime();
            if (nextScheduleTime == null && job.retrieveIsOnChargeOnly() && !isJobRunning(job.getId())) {
                executeJob(job);
            }
        }
    }

    private void checkAndRunScheduledJobs() {
        List<Job> jobs = jobsFileUtil.readJobs();
        Calendar now = Calendar.getInstance();

        for (Job job : jobs) {
            if (isJobRunning(job.getId()) || job.retrieveIsOnStartup()) {
                continue; // Skip if already running
            }

            Calendar nextRun = job.retrieveNextScheduleTime();
            if(nextRun == null) {
                continue; // Skip if no next run time is set
            }
            // Skip jobs that are wifi-only or charge-only as they're handled separately
            if (job.retrieveIsOnWifiOnly() && !isWifiConnected()) {
                continue;
            }
            if (job.retrieveIsOnChargeOnly() && !isCharging()) {
                continue;
            }

            if (!nextRun.after(now)) {
                executeJob(job);
            }
        }
        Semaphore jobSemaphore = new Semaphore(1);
        while(!toRunJob.isEmpty()) {
            try {
                jobSemaphore.acquire();
                UUID jobId = toRunJob.poll();
                if (jobId != null) {
                    runningJobId.set(jobId);
                    Job job = jobsFileUtil.getJobById(jobId);
                    if (job != null) {
                        runJobInternal(job,jobSemaphore);
                    }
                }

            } catch (InterruptedException e) {
                jobSemaphore.release();
            }
        }
    }

    private void runJobInternal(Job job, Semaphore jobSemaphore) {
        executorService.execute(() -> {
            try {
                Log.d(TAG, "Starting job: " + job.getName());

                var command = new CommandLineArgs();
                command.setBackup(true);
                command.setPassword(job.getPassword());
                command.setUsername(job.getName());
                command.setServerAddress(job.getServerAddress());
                command.setServerPort(job.getServerPort());
                command.setSourceFolder(job.getLocalSource());
                command.setTargetFolder(job.getTargetDestination());
                var sc = new SyncClient();
                sc.doSync(command);

                // Update job with execution details
                job.setLastExecution(Calendar.getInstance().getTime().toString());
                // job.setLastTransferred(...); // Set based on actual bytes transferred

                jobsFileUtil.updateJob(job);
                Log.d(TAG, "Finished job: " + job.getName());
            } catch (Exception e) {
                Log.e(TAG, "Error executing job: " + job.getName(), e);
            } finally {
               jobSemaphore.release();
            }
        });
    }

    private synchronized boolean isJobRunning(UUID jobId) {
        return toRunJob.contains(jobId) || runningJobId.get() != null && runningJobId.get().equals(jobId);
    }

    private void executeJob(Job job) {
        if (isJobRunning(job.getId())) {
            return;
        }

        UUID jobId = job.getId();
        toRunJob.add(jobId);


    }
}