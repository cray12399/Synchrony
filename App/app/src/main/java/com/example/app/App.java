package com.example.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.util.Log;

/**
 * The starting point of the app. Notification channels are all created here.
 */

public class App extends android.app.Application {
    // Notification channel ID's.
    public static final String connectedDevicesChannelID = "connected_devices";
    public static final String DISCONNECTED_DEVICES_CHANNEL_ID = "disconnected_devices";

    private static final String TAG = "App";

    @Override
    public void onCreate() {
        super.onCreate();

        Utils.initValues(this);

        createConnectedPCNotificationChannel();
        createDisconnectedPCNotificationChannel();
    }

    /**
     * Creates the channel that contains the notifications for connected PC's
     */

    private void createConnectedPCNotificationChannel() {
        NotificationChannel connectedDevicesChannel = new NotificationChannel(
                connectedDevicesChannelID,
                "Connected Devices",
                NotificationManager.IMPORTANCE_DEFAULT);
        connectedDevicesChannel.setDescription("Contains notifications for connected devices");

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(connectedDevicesChannel);

        Log.d(TAG, "createConnectedPCNotificationChannel: Channel Created!");
    }

    /**
     * Creates the channel that contains the notifications for when a PC is disconnected
     */

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
