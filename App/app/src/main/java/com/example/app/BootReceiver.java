package com.example.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

// This receiver receives the boot action for the app.
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "onReceive: Starting BluetoothSyncThread in foreground!");
            // If the device is booted, start the BluetoothSyncService foreground intent.
            Intent syncServiceIntent = new Intent(context, BluetoothSyncService.class);
            context.startForegroundService(syncServiceIntent);
        }
    }
}
