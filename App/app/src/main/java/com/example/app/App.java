package com.example.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Build;

import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.concurrent.ExecutionException;

public class App extends android.app.Application {

    public static final String CONNECTED_DEVICES_CHANNEL_ID = "connected_devices";
    public static final String DISCONNECTED_DEVICES_CHANNEL_ID = "disconnected_devices";

    @Override
    public void onCreate() {
        super.onCreate();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createConnectedDevicesNotificationChannel();
            createDisconnectedDevicesNotificationChannel();
        }

        startSyncWork();
    }

    private void startSyncWork() {
        //TODO: Modify start sync work function
    }

    private boolean btWorkScheduled() {
        WorkManager instance = WorkManager.getInstance(getApplicationContext());
        ListenableFuture<List<WorkInfo>> statuses = instance
                .getWorkInfosByTag("bluetoothSyncWorker");
        try {
            boolean running = false;
            List<WorkInfo> workInfoList = statuses.get();
            for (WorkInfo workInfo : workInfoList) {
                WorkInfo.State state = workInfo.getState();
                running = state == WorkInfo.State.RUNNING | state == WorkInfo.State.ENQUEUED;
            }
            return running;
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void createConnectedDevicesNotificationChannel() {
        NotificationChannel connectedDevicesChannel = new NotificationChannel(
                CONNECTED_DEVICES_CHANNEL_ID,
                "Connected Devices",
                NotificationManager.IMPORTANCE_DEFAULT);
        connectedDevicesChannel.setDescription("Contains notifications for connected devices");
        connectedDevicesChannel.enableVibration(false);

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(connectedDevicesChannel);
    }

    private void createDisconnectedDevicesNotificationChannel() {
        NotificationChannel disconnectedDevicesChannel = new NotificationChannel(
                DISCONNECTED_DEVICES_CHANNEL_ID,
                "Disconnected Devices",
                NotificationManager.IMPORTANCE_DEFAULT);
        disconnectedDevicesChannel
                .setDescription("Contains notifications for disconnected devices");

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(disconnectedDevicesChannel);
    }
}
