package com.kendar.sync.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.kendar.sync.dao.BackupJobDao;
import com.kendar.sync.model.BackupJob;
import com.kendar.sync.receiver.AlarmReceiver;
import com.kendar.sync.service.BackupService;

import java.util.Date;
import java.util.List;

/**
 * Utility class for scheduling alarms to trigger backup jobs
 */
public class AlarmScheduler {
    private static final long MIN_INTERVAL = 5 * 60 * 1000; // 5 minutes minimum interval
    private final Context context;
    private final BackupJobDao jobDao;
    private final AlarmManager alarmManager;

    public AlarmScheduler(Context context) {
        this.context = context;
        this.jobDao = new BackupJobDao(context);
        this.alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    }

    /**
     * Schedule the next check for due jobs
     */
    public void scheduleNextCheck() {
        // Find the next due job
        Date nextRunTime = findNextRunTime();
        if (nextRunTime == null) {
            // If no jobs are scheduled, check every hour
            nextRunTime = new Date(System.currentTimeMillis() + 60 * 60 * 1000);
        }

        // Create intent for alarm
        Intent intent = new Intent(context, AlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Schedule alarm
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextRunTime.getTime(), pendingIntent);
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, nextRunTime.getTime(), pendingIntent);
        }
    }

    /**
     * Find the next run time among all jobs
     */
    private Date findNextRunTime() {
        List<BackupJob> jobs = jobDao.getAllJobs();
        Date nextRun = null;
        long now = System.currentTimeMillis();

        for (BackupJob job : jobs) {
            Date jobNextRun = job.getNextScheduledRun();
            if (jobNextRun != null) {
                // Ensure minimum interval
                if (jobNextRun.getTime() < now + MIN_INTERVAL) {
                    jobNextRun = new Date(now + MIN_INTERVAL);
                }

                if (nextRun == null || jobNextRun.before(nextRun)) {
                    nextRun = jobNextRun;
                }
            }
        }

        return nextRun;
    }

    /**
     * Schedule a specific job
     */
    public void scheduleJob(BackupJob job) {
        // Calculate next run time if it's not set
        if (job.getNextScheduledRun() == null) {
            Date nextRun = job.getSchedule().getNextRunTime(new Date());
            job.setNextScheduledRun(nextRun);
            jobDao.updateJob(job);
        }

        // Update the global schedule
        scheduleNextCheck();
    }

    /**
     * Cancel all scheduled alarms
     */
    public void cancelAll() {
        Intent intent = new Intent(context, AlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarmManager.cancel(pendingIntent);
    }
}
