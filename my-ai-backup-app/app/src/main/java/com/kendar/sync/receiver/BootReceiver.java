package com.kendar.sync.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.kendar.sync.service.BackupService;
import com.kendar.sync.util.AlarmScheduler;

/**
 * Broadcast receiver that starts the backup service when the device boots
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            // Start the backup service
            Intent serviceIntent = new Intent(context, BackupService.class);
            serviceIntent.setAction("CHECK_DUE_JOBS");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }

            // Schedule the next backup check
            AlarmScheduler scheduler = new AlarmScheduler(context);
            scheduler.scheduleNextCheck();
        }
    }
}
