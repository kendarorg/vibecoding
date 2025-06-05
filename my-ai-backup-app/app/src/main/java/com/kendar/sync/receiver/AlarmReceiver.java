package com.kendar.sync.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.kendar.sync.service.BackupService;

/**
 * Broadcast receiver that triggers backup checks when an alarm fires
 */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Start the backup service to check for due jobs
        Intent serviceIntent = new Intent(context, BackupService.class);
        serviceIntent.setAction("CHECK_DUE_JOBS");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }
}
