package com.example.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Utils utils = Utils.getInstance(context);

        for (PairedPC pairedPC : utils.getPairedPCS()) {
            pairedPC.setNotified(false);
            pairedPC.setConnected(false);
        }

        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Intent syncServiceIntent = new Intent(context, BluetoothSyncService.class);
            context.startForegroundService(syncServiceIntent);
        }
    }
}
