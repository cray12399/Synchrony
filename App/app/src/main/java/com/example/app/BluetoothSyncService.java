package com.example.app;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.UUID;

import static com.example.app.App.CONNECTED_DEVICES_CHANNEL_ID;
import static com.example.app.App.DISCONNECTED_DEVICES_CHANNEL_ID;

public class BluetoothSyncService extends Service {

    private final UUID UUID = java.util.UUID.fromString("38b9093c-ff2b-413b-839d-c179b37d8528");

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Summary notification which holds notifications for connected PCS
        String CONNECTED_PC_GROUP = "connectedPCS";
        NotificationCompat.Builder summaryNotificationBuilder =
                new NotificationCompat.Builder(this,
                        CONNECTED_DEVICES_CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_placeholder_logo)
                        .setContentTitle("Sync Service Background")
                        .setGroup(CONNECTED_PC_GROUP)
                        .setGroupSummary(true)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setOngoing(true);
        int SUMMARY_NOTIFICATION_ID = 69;

        startForeground(SUMMARY_NOTIFICATION_ID, summaryNotificationBuilder.build());

        // The main thread of the service. Will handle all connected PC's
        PCHandlerThread pcHandlerThread =
                new PCHandlerThread(this);
        pcHandlerThread.start();

        return START_STICKY_COMPATIBILITY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // This thread manages the connected PC's
    private static class PCHandlerThread extends Thread {
        private final BluetoothAdapter BLUETOOTH_ADAPTER = BluetoothAdapter.getDefaultAdapter();
        private final Utils utils;
        private final Context CONTEXT;

        public PCHandlerThread(Context context) {
            this.utils = Utils.getInstance(context);
            this.CONTEXT = context;
        }

        @Override
        public void run() {
            while (!isInterrupted()) {
                handleConnectedDevices();
            }
        }

        private void handleConnectedDevices() {
            // If the bluetooth adapter is enabled, look for PC's
            if (BLUETOOTH_ADAPTER.isEnabled()) {
                for (BluetoothDevice device : BLUETOOTH_ADAPTER.getBondedDevices()) {
                    // If the device is a computer, then see if they have been paired before.
                    int deviceClass = device.getBluetoothClass().getDeviceClass();
                    if (deviceClass == BluetoothClass.Device.COMPUTER_LAPTOP ||
                            deviceClass == BluetoothClass.Device.COMPUTER_DESKTOP) {
                        if (!utils.inPairedPCS(device.getAddress())) {
                            // If the device hasn't been paired before, add it to pairedPCS.
                            utils.addToPairedPCS(new PairedPC(device.getName(),
                                    device.getAddress(), device));
                        }
                        // Handle the PC
                        handlePC(device, utils.isConnected(device.getAddress()));
                    }
                }
                // If the bluetooth adapter is disabled, cancel their notifications
                // and stop their background threads.
            } else {
                for (PairedPC pairedPC : utils.getPairedPCS()) {
                    if (pairedPC.isNotified()) {
                        handleNotification(pairedPC, true);
                        sendDisconnectNotification(pairedPC);
                    }

                    if (pairedPC.getBluetoothCommThread() != null) {
                        pairedPC.getBluetoothCommThread().interrupt();
                    }
                }
            }
        }

        private void handlePC(BluetoothDevice device, boolean deviceConnected) {
            PairedPC pairedPC = utils.getPairedPC(device.getAddress());

            if (deviceConnected) {
                if (pairedPC.isActive()) {
                    if (!pairedPC.isNotified()){handleNotification(pairedPC, false);}
                    if (pairedPC.getBluetoothCommThread() == null) {
                        utils.getPairedPC(device.getAddress()).setBluetoothCommThread(
                                new BluetoothCommThread(CONTEXT, device));
                    }
                } else {
                    if (pairedPC.isNotified()) {handleNotification(pairedPC, true);}
                    if (pairedPC.getBluetoothCommThread() != null) {
                        utils.getPairedPC(device.getAddress()).getBluetoothCommThread().interrupt();
                    }
                }
            } else {
                if (pairedPC.isNotified()) {
                    handleNotification(pairedPC, true);
                    sendDisconnectNotification(pairedPC);
                }
            }
        }

        private void sendDisconnectNotification(PairedPC pairedPC) {
            NotificationManagerCompat notificationManager = NotificationManagerCompat
                    .from(CONTEXT);
            final int CONNECTION_NOTIFICATION_ID = 420;
            NotificationCompat.Builder disconnectedNotification =
                    new NotificationCompat.Builder(CONTEXT,
                            DISCONNECTED_DEVICES_CHANNEL_ID)
                            .setSmallIcon(R.drawable.ic_pc_disconnected)
                            .setContentTitle("PC Disconnected!")
                            .setContentText(String.format("%s disconnected!",
                                    pairedPC.getName()))
                            .setPriority(NotificationCompat.PRIORITY_HIGH);
            notificationManager.notify(pairedPC.getAddress(),
                    CONNECTION_NOTIFICATION_ID, disconnectedNotification.build());
        }

        private void handleNotification(PairedPC pairedPC, boolean removeNotification) {
            NotificationManagerCompat notificationManager = NotificationManagerCompat
                    .from(CONTEXT);
            final int CONNECTION_NOTIFICATION_ID = 420;

            if (!removeNotification) {
                final String CONNECTED_PC_GROUP = "connectedPCS";
                NotificationCompat.Builder connectedNotification =
                        new NotificationCompat.Builder(
                                CONTEXT, CONNECTED_DEVICES_CHANNEL_ID)
                                .setSmallIcon(R.drawable.ic_placeholder_logo)
                                .setContentTitle("Syncing PC")
                                .setContentText(pairedPC.getName())
                                .setGroup(CONNECTED_PC_GROUP)
                                .setPriority(NotificationCompat.PRIORITY_HIGH)
                                .setOngoing(true);
                notificationManager.notify(pairedPC.getAddress(),
                        CONNECTION_NOTIFICATION_ID, connectedNotification.build());
                utils.getPairedPC(pairedPC.getAddress()).setNotified(true);
            } else {
                if (pairedPC.isNotified()) {
                    notificationManager.cancel(pairedPC.getAddress(),
                            CONNECTION_NOTIFICATION_ID);
                    pairedPC.setNotified(false);
                }
            }
        }
    }
}
