package com.example.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import android.util.Log;

public class App extends android.app.Application {
    public static final String CONNECTED_DEVICES_CHANNEL_ID = "connected_devices";
    public static final String DISCONNECTED_DEVICES_CHANNEL_ID = "disconnected_devices";
    private static final String TAG = "App";

    @Override
    public void onCreate() {
        super.onCreate();

        // Create notification channels
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createConnectedPCNotificationChannel();
            createDisconnectedPCNotificationChannel();
        }
    }

    // Creates the channel that contains the ongoing notifications for connected PC's.
    private void createConnectedPCNotificationChannel() {
        NotificationChannel connectedDevicesChannel = new NotificationChannel(
                CONNECTED_DEVICES_CHANNEL_ID,
                "Connected Devices",
                NotificationManager.IMPORTANCE_DEFAULT);
        connectedDevicesChannel.setDescription("Contains notifications for connected devices");
        connectedDevicesChannel.enableVibration(false);

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(connectedDevicesChannel);

        Log.d(TAG, "createConnectedPCNotificationChannel: Channel Created!");
    }

    // Creates the channel that contains the disconnected PC notifications.
    private void createDisconnectedPCNotificationChannel() {
        NotificationChannel disconnectedDevicesChannel = new NotificationChannel(
                DISCONNECTED_DEVICES_CHANNEL_ID,
                "Disconnected Devices",
                NotificationManager.IMPORTANCE_DEFAULT);
        disconnectedDevicesChannel
                .setDescription("Contains notifications for disconnected devices");

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(disconnectedDevicesChannel);

        Log.d(TAG, "createDisconnectedPCNotificationChannel: Channel Created!");
    }
}
