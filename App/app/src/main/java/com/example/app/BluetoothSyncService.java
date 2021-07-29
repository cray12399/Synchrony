package com.example.app;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import static com.example.app.App.CONNECTED_DEVICES_CHANNEL_ID;

public class BluetoothSyncService extends Service {

    private static final String TAG = "BluetoothSyncService";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Summary notification which holds notifications for BluetoothConnectedThreads
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
        Log.d(TAG, "onStartCommand: BluetoothSyncService started in the foreground!");

        // The main thread of the service. Will handle all connected PC's
        PCHandlerThread pcHandlerThread = new PCHandlerThread(this);
        pcHandlerThread.start();
        Log.d(TAG, "onStartCommand: pcHandlerThread started!");

        return START_STICKY_COMPATIBILITY;
    }

    // This thread manages the connected PC's
    private static class PCHandlerThread extends Thread {
        private static final String TAG = "PCHandlerThread";
        private final Utils utils;
        private final Context CONTEXT;
        private final BluetoothAdapter BLUETOOTH_ADAPTER = BluetoothAdapter.getDefaultAdapter();

        public PCHandlerThread(Context context) {
            this.utils = Utils.getInstance(context);
            this.CONTEXT = context;
        }

        @Override
        public void run() {
            while (!isInterrupted()) {
                // If the bluetooth adapter is enabled, handle connected devices.
                if (BLUETOOTH_ADAPTER.isEnabled()) {
                    handleConnectedDevices();
                    // If the bluetooth adapter is disabled, stop their background threads.
                } else {
                    for (PairedPC pairedPC : utils.getPairedPCS()) {
                        pairedPC.interruptBluetoothConnectionThread();
                        String DEVICE_TAG = String.format("%s (%s)", pairedPC.getName(),
                                pairedPC.getAddress());
                        Log.d(TAG, String.format("run: Bluetooth Disconnected! " +
                                "Stopping background thread for device: %s!"));
                    }
                }
            }
        }

        private void handleConnectedDevices() {
            for (BluetoothDevice device : BLUETOOTH_ADAPTER.getBondedDevices()) {
                String DEVICE_TAG = String.format("%s (%s)", device.getName(),
                        device.getAddress());
                // If the device is a computer, then see if they have been paired before.
                int deviceClass = device.getBluetoothClass().getDeviceClass();
                // (deviceClass == BluetoothClass.Device.COMPUTER_LAPTOP ||
                //                        deviceClass == BluetoothClass.Device.COMPUTER_DESKTOP)
                if (deviceClass != 0) {
                    // If the device hasn't been paired before, add it to pairedPCS.
                    if (!utils.inPairedPCS(device.getAddress())) {
                        utils.addToPairedPCS(new PairedPC(CONTEXT, device.getName(),
                                device.getAddress(), device));
                        Log.d(TAG, String.format("handleConnectedDevices: " +
                                "Created new PairedPC entry for device: %s!", DEVICE_TAG));
                    }
                    // If the Paired PC is active and is connected:
                    if (utils.getPairedPC(device.getAddress()).isActive() &&
                            Utils.isConnected(device.getAddress())) {
                        int notificationID = utils.getPairedPC(device.getAddress())
                                .getNotificationID();
                        // If the thread has not been created yet, create one.
                        if (utils.getPairedPC(device.getAddress()).getBluetoothConnectedThread() ==
                                null) {
                            utils.getPairedPC(device.getAddress()).setBluetoothConnectedThread(
                                    new BluetoothConnectionThread(CONTEXT, device, notificationID));
                            Log.d(TAG, String.format("handleConnectedDevices: " +
                                    "Created BluetoothConnectedThread for device: %s!",
                                    DEVICE_TAG));
                        // If it has been created before, check if it is alive. If it isn't,
                            // create a new thread.
                        } else if (!utils.getPairedPC(device.getAddress())
                                .getBluetoothConnectedThread().isAlive()) {
                            utils.getPairedPC(device.getAddress()).setBluetoothConnectedThread(
                                    new BluetoothConnectionThread(CONTEXT, device, notificationID));
                            Log.d(TAG, String.format("handleConnectedDevices: " +
                                            "Created BluetoothConnectedThread for device: %s!",
                                    DEVICE_TAG));
                        }
                    // If not active or connected, if it has a thread, check if its connected
                    // thread exists.
                    } else if (utils.getPairedPC(device.getAddress())
                            .getBluetoothConnectedThread() != null) {
                        // If the thread is alive, stop it.
                        if (utils.getPairedPC(device.getAddress())
                                .getBluetoothConnectedThread().isAlive()) {
                            utils.getPairedPC(device.getAddress())
                                    .interruptBluetoothConnectionThread();
                            Log.d(TAG, String.format("handleConnectedDevices: " +
                                            "Device: %s no longer connected! " +
                                            "Stopping device's BluetoothConnectedThread",
                                    DEVICE_TAG));
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onDestroy() {
        // If this service is destroyed, cancel all the current BluetoothConnectionThreads
        // and stop the foreground service.
        Log.d(TAG, "onDestroy: " +
                "BluetoothSyncService destroyed! Stopping BluetoothConnected threads.");
        Utils utils = Utils.getInstance(this);
        for (PairedPC pairedPC : utils.getPairedPCS()) {
            pairedPC.interruptBluetoothConnectionThread();
            stopForeground(true);
            stopSelf();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
