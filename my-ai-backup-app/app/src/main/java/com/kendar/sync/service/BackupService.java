package com.kendar.sync.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.kendar.sync.MainActivity;
import com.kendar.sync.R;
import com.kendar.sync.api.BackupClient;
import com.kendar.sync.dao.BackupJobDao;
import com.kendar.sync.model.BackupJob;
import com.kendar.sync.model.Schedule;
import com.kendar.sync.util.AlarmScheduler;

import java.util.Date;
import java.util.List;
concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service for running backup jobs in the background
 */
public class BackupService extends Service {
    private static final String CHANNEL_ID = "BackupServiceChannel";
    private static final int NOTIFICATION_ID = 1;

    private final IBinder binder = new LocalBinder();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean isRunningBackup = new AtomicBoolean(false);
    private final Map<Long, BackupStatus> runningJobs = new ConcurrentHashMap<>();

    private BackupJobDao jobDao;
    private BackupClient backupClient;
    private Handler handler;
    private PowerManager.WakeLock wakeLock;
    private AlarmScheduler alarmScheduler;

    /**
     * Local binder class
     */
    public class LocalBinder extends Binder {
        public BackupService getService() {
            return BackupService.this;
        }
    }

    /**
     * Class to track backup job status
     */
    private static class BackupStatus {
        final BackupJob job;
        int progress;
        String currentFile;
        long startTime;

        BackupStatus(BackupJob job) {
            this.job = job;
            this.progress = 0;
            this.currentFile = "";
            this.startTime = System.currentTimeMillis();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        jobDao = new BackupJobDao(this);
        handler = new Handler();
        alarmScheduler = new AlarmScheduler(this);

        // Create a fake backup client (in a real app, this would be injected or provided elsewhere)
        // This is just a placeholder as per the requirement that it will be implemented by someone else
        backupClient = createFakeBackupClient();

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification("Backup service running", null, 0));

        // Acquire wake lock to keep service running
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "BackupService::WakeLock");
        wakeLock.acquire();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case "CHECK_DUE_JOBS":
                    checkAndRunDueJobs();
                    break;
                case "RUN_SPECIFIC_JOB":
                    long jobId = intent.getLongExtra("job_id", -1);
                    if (jobId != -1) {
                        runJob(jobId);
                    }
                    break;
            }
        }

        // Reschedule the next check
        alarmScheduler.scheduleNextCheck();

        return START_STICKY;
    }

    /**
     * Check for due jobs and run them
     */
    private void checkAndRunDueJobs() {
        if (isRunningBackup.get()) {
            return; // Already running a backup
        }

        executor.execute(() -> {
            List<BackupJob> dueJobs = jobDao.getDueJobs();
            for (BackupJob job : dueJobs) {
                // Check if conditions are met
                if (canRunJob(job)) {
                    runJobInternal(job);
                    break; // Run one job at a time
                } else {
                    // Reschedule for later if conditions aren't met
                    rescheduleJob(job);
                }
            }
        });
    }

    /**
     * Run a specific job
     */
    public void runJob(long jobId) {
        if (isRunningBackup.get()) {
            return; // Already running a backup
        }

        executor.execute(() -> {
            BackupJob job = jobDao.getJob(jobId);
            if (job != null) {
                if (canRunJob(job)) {
                    runJobInternal(job);
                } else {
                    // Notify that job cannot run due to constraints
                    updateNotification("Cannot run backup: conditions not met", null, 0);
                }
            }
        });
    }

    /**
     * Check if a job can run based on its constraints
     */
    private boolean canRunJob(BackupJob job) {
        // Check if wifi-only and we're on wifi
        if (job.isWifiOnly() && !isWifiConnected()) {
            return false;
        }

        // Check if charging-only and we're charging
        if (job.isChargingOnly() && !isCharging()) {
            return false;
        }

        return true;
    }

    /**
     * Run a job's backup operation
     */
    private void runJobInternal(BackupJob job) {
        if (!isRunningBackup.compareAndSet(false, true)) {
            return; // Already running
        }

        updateNotification("Starting backup: " + job.getName(), null, 0);
        Date startTime = new Date();
        BackupStatus status = new BackupStatus(job);
        runningJobs.put(job.getId(), status);

        backupClient.startBackup(job, new BackupClient.BackupProgressListener() {
            @Override
            public void onBackupStarted() {
                handler.post(() -> {
                    updateNotification("Backup started: " + job.getName(), null, 0);
                });
            }

            @Override
            public void onBackupProgress(int progress, String currentFile) {
                handler.post(() -> {
                    status.progress = progress;
                    status.currentFile = currentFile;
                    updateNotification("Backing up: " + job.getName(), currentFile, progress);

                    // Check if conditions still met
                    if (!canRunJob(job)) {
                        backupClient.stopBackup();
                        updateNotification("Backup stopped: conditions changed", null, 0);
                    }
                });
            }

            @Override
            public void onBackupCompleted(boolean success, String message) {
                handler.post(() -> {
                    Date endTime = new Date();
                    long duration = endTime.getTime() - startTime.getTime();

                    // Calculate next run time
                    Date nextRun = job.getSchedule().getNextRunTime(endTime);

                    // Update job information
                    jobDao.updateJobRunInfo(job.getId(), startTime, duration, nextRun);

                    // Update notification
                    if (success) {
                        updateNotification("Backup completed: " + job.getName(), null, 100);
                    } else {
                        updateNotification("Backup failed: " + job.getName(), message, 0);
                    }

                    isRunningBackup.set(false);
                    runningJobs.remove(job.getId());

                    // Schedule next check
                    alarmScheduler.scheduleNextCheck();
                });
            }

            @Override
            public void onBackupError(String errorMessage) {
                handler.post(() -> {
                    updateNotification("Backup error: " + job.getName(), errorMessage, 0);

                    // Update job with failure information
                    Date endTime = new Date();
                    long duration = endTime.getTime() - startTime.getTime();
                    Date nextRun = job.getSchedule().getNextRunTime(endTime);
                    jobDao.updateJobRunInfo(job.getId(), startTime, duration, nextRun);

                    isRunningBackup.set(false);
                    runningJobs.remove(job.getId());
                });
            }
        });
    }

    /**
     * Reschedule a job for later
     */
    private void rescheduleJob(BackupJob job) {
        // Set next run time to 15 minutes later
        Date nextRun = new Date(System.currentTimeMillis() + (15 * 60 * 1000));
        jobDao.updateJobRunInfo(job.getId(), null, 0, nextRun);
    }

    /**
     * Stop the currently running backup if any
     */
    public void stopCurrentBackup() {
        if (isRunningBackup.get()) {
            backupClient.stopBackup();
            isRunningBackup.set(false);
            updateNotification("Backup stopped", null, 0);
        }
    }

    @Override
    public void onDestroy() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }

        // Stop any running backup
        stopCurrentBackup();
        executor.shutdownNow();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    /**
     * Check if we're connected to WiFi
     */
    private boolean isWifiConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected() && 
               networkInfo.getType() == ConnectivityManager.TYPE_WIFI;
    }

    /**
     * Check if the device is charging
     */
    private boolean isCharging() {
        Intent intent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (intent == null) return false;

        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        return status == BatteryManager.BATTERY_STATUS_CHARGING || 
               status == BatteryManager.BATTERY_STATUS_FULL;
    }

    /**
     * Create the notification channel
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Backup Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    /**
     * Create a notification for the service
     */
    private Notification createNotification(String title, String text, int progress) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setSmallIcon(R.drawable.ic_backup)
                .setContentIntent(pendingIntent);

        if (text != null) {
            builder.setContentText(text);
        }

        if (progress > 0) {
            builder.setProgress(100, progress, false);
        }

        return builder.build();
    }

    /**
     * Update the service notification
     */
    private void updateNotification(String title, String text, int progress) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, createNotification(title, text, progress));
    }

    /**
     * Create a fake backup client implementation
     * This is just a placeholder as the real implementation will be provided by someone else
     */
    private BackupClient createFakeBackupClient() {
        return new BackupClient() {
            private boolean isRunning = false;

            @Override
            public boolean startBackup(BackupJob job, BackupProgressListener listener) {
                if (isRunning) return false;

                isRunning = true;
                listener.onBackupStarted();

                // Simulate backup progress
                new Thread(() -> {
                    try {
                        for (int i = 0; i <= 100 && isRunning; i += 5) {
                            final int progress = i;
                            String file = "file_" + i + ".txt";
                            listener.onBackupProgress(progress, file);
                            Thread.sleep(500); // Simulate work
                        }

                        if (isRunning) {
                            isRunning = false;
                            listener.onBackupCompleted(true, "Backup completed successfully");
                        }
                    } catch (InterruptedException e) {
                        isRunning = false;
                        listener.onBackupError("Backup interrupted: " + e.getMessage());
                    }
                }).start();

                return true;
            }

            @Override
            public boolean stopBackup() {
                if (!isRunning) return false;

                isRunning = false;
                return true;
            }

            @Override
            public void fetchRemoteTargets(String serverAddress, int port, String login, 
                                          String password, RemoteTargetsListener listener) {
                // Simulate network request
                new Thread(() -> {
                    try {
                        Thread.sleep(1000); // Simulate network delay

                        // Generate fake targets
                        String[] targets = {
                            "Backup_Daily",
                            "Backup_Weekly",
                            "Backup_Monthly",
                            "Photos",
                            "Documents"
                        };

                        listener.onTargetsReceived(targets);
                    } catch (InterruptedException e) {
                        listener.onTargetsFetchFailed("Failed to fetch targets: " + e.getMessage());
                    }
                }).start();
            }
        };
    }
}
