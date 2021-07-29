package com.example.app;

import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import static com.example.app.App.CONNECTED_DEVICES_CHANNEL_ID;

// This class receives actions from the BluetoothConnectedThread notifications
public class ConnectedPCNotificationReceiver extends BroadcastReceiver {
    public static final String STOP_SYNC_ACTION = "stopSyncAction";
    public static final String PC_NAME_KEY = "pcNameKey";
    public static final String PC_ADDRESS_KEY = "pcAddressKey";
    public static final String NOTIFICATION_ID_KEY = "notificationIDKey";

    @Override
    public void onReceive(Context context, Intent intent) {
        // This action is responsive to the "Stop Sync" button on
        // BluetoothConnectedThread notifications
        if (intent.getAction().equals(STOP_SYNC_ACTION)) {
            // Set the PC as inactive
            Utils utils = Utils.getInstance(context);
            utils.setPCActive(intent.getStringExtra(PC_ADDRESS_KEY), false);

            // Cancel the notification
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
            notificationManager.cancel(intent.getStringExtra(PC_ADDRESS_KEY),
                    intent.getIntExtra(NOTIFICATION_ID_KEY, -1));
        }
    }
}
