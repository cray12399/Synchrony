package com.example.app;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.UUID;

import static com.example.app.App.CONNECTED_DEVICES_CHANNEL_ID;

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
        PCHandlerThread pcHandlerThread = new PCHandlerThread(this);
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
                    }
                }
            }
        }

        private void handleConnectedDevices() {
            for (BluetoothDevice device : BLUETOOTH_ADAPTER.getBondedDevices()) {
                // If the device is a computer, then see if they have been paired before.
                int deviceClass = device.getBluetoothClass().getDeviceClass();
                if (deviceClass == BluetoothClass.Device.COMPUTER_LAPTOP ||
                        deviceClass == BluetoothClass.Device.COMPUTER_DESKTOP) {
                    if (!utils.inPairedPCS(device.getAddress())) {
                        // If the device hasn't been paired before, add it to pairedPCS.
                        utils.addToPairedPCS(new PairedPC(CONTEXT, device.getName(),
                                device.getAddress(), device));
                    }
                    // Handle the PC thread
                    if (utils.getPairedPC(device.getAddress()).isActive() &&
                            utils.isConnected(device.getAddress())) {
                        if (utils.getPairedPC(device.getAddress()).getBluetoothConnectionThread() ==
                                null) {
                            utils.getPairedPC(device.getAddress()).setBluetoothConnectionThread(
                                    new BluetoothConnectionThread(CONTEXT, device));
                        } else if (!utils.getPairedPC(device.getAddress())
                                .getBluetoothConnectionThread().isAlive()) {
                            utils.getPairedPC(device.getAddress()).setBluetoothConnectionThread(
                                    new BluetoothConnectionThread(CONTEXT, device));
                        }
                    } else if (utils.getPairedPC(device.getAddress())
                            .getBluetoothConnectionThread() != null) {
                        if (utils.getPairedPC(device.getAddress())
                                .getBluetoothConnectionThread().isAlive()) {
                            utils.getPairedPC(device.getAddress())
                                    .interruptBluetoothConnectionThread();
                        }
                    }
                }
            }
        }
    }
}
