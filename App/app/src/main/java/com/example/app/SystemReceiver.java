package com.example.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * This receiver receives system actions for the app.
 */
public class SystemReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        System.out.println(intent.getAction());
        switch (intent.getAction()) {
            case Intent.ACTION_BOOT_COMPLETED:
                Utils.initValues(context);

                Log.d(TAG, "onReceive: Starting BluetoothSyncThread in foreground...");
                // If the device is booted, start the BluetoothSyncService foreground intent.
                Intent syncServiceIntent = new Intent(context, BluetoothConnectService.class);
                context.startForegroundService(syncServiceIntent);
                break;
        }
    }
}
