package com.example.app;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Set;

import static com.example.app.App.CONNECTED_DEVICES_CHANNEL_ID;

public class BluetoothSyncWorker extends Worker {
    private final Utils utils;
    private final BluetoothAdapter BLUETOOTH_ADAPTER = BluetoothAdapter.getDefaultAdapter();
    private final String CONNECTED_PC_GROUP = "connectedPCS";
    private ArrayList<String> notifiedPCAddresses = new ArrayList<>();

    public BluetoothSyncWorker(@NonNull Context context,
                               @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.utils = Utils.getInstance(getApplicationContext());
    }

    @NonNull
    @Override
    public Result doWork() {
        do {
            handleConnectedDevices();
            handleNotifications();
            SystemClock.sleep(1000);
        } while (!isStopped());

        return Result.success();
    }

    private void handleConnectedDevices() {
        Set<BluetoothDevice> pairedDevices = BLUETOOTH_ADAPTER.getBondedDevices();
        ArrayList<PairedPC> currentlyConnectedPCS = new ArrayList<>();

        // Handle connected PCS
        for (BluetoothDevice device : pairedDevices) {
            if (isConnected(device.getAddress())) {
                int deviceClass = device.getBluetoothClass().getDeviceClass();

                // (deviceClass == BluetoothClass.Device.COMPUTER_LAPTOP |
                //                deviceClass == BluetoothClass.Device.COMPUTER_DESKTOP)
                if (deviceClass != 0) {
                    if (!utils.inPairedPCS(device.getAddress())) {
                        utils.addToPairedPCS(new PairedPC(device.getName(),
                                device.getAddress(), true));
                    } else {
                        if (utils.getPairedPCByAddress(device.getAddress()) != null) {
                            utils.getPairedPCByAddress(device.getAddress()).setConnected(true);
                            utils.savePairedPCSToDevice();
                        }
                    }
                }
            } else {
                if (utils.inPairedPCS(device.getAddress())) {
                    if (utils.getPairedPCByAddress(device.getAddress()) != null) {
                        utils.getPairedPCByAddress(device.getAddress()).setConnected(false);
                        utils.savePairedPCSToDevice();
                    }
                }
            }
        }
    }

    private void handleNotifications() {
        NotificationManagerCompat notificationManager = NotificationManagerCompat
                .from(getApplicationContext());

        for (PairedPC pairedPC : utils.getPairedPCS()) {
            if (pairedPC.isConnected()) {
                if (pairedPC.isActive() && !notifiedPCAddresses.contains(pairedPC.getAddress())) {
                    NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(
                            getApplicationContext(), CONNECTED_DEVICES_CHANNEL_ID)
                            .setSmallIcon(R.drawable.ic_pc)
                            .setContentTitle("Syncing PC")
                            .setContentText(pairedPC.getName())
                            .setGroup(CONNECTED_PC_GROUP)
                            .setPriority(NotificationCompat.PRIORITY_MIN)
                            .setOngoing(true);

                    notificationManager.notify(pairedPC.getAddress(),
                            100, notificationBuilder.build());
                    notifiedPCAddresses.add(pairedPC.getAddress());

                    if (notifiedPCAddresses.size() == 1) {
                        notificationManager = NotificationManagerCompat
                                .from(getApplicationContext());
                        NotificationCompat.Builder summaryNotificationBuilder =
                                new NotificationCompat.Builder(getApplicationContext(),
                                        CONNECTED_DEVICES_CHANNEL_ID)
                                .setSmallIcon(R.drawable.ic_pc)
                                .setContentTitle("Syncing PC's")
                                .setGroup(CONNECTED_PC_GROUP)
                                .setGroupSummary(true)
                                .setPriority(NotificationCompat.PRIORITY_HIGH)
                                .setOngoing(true);
                        notificationManager.notify(101, summaryNotificationBuilder.build());
                    }
                } else if (!pairedPC.isActive()) {
                    notificationManager.cancel(pairedPC.getAddress(), 100);
                    notifiedPCAddresses.remove(pairedPC.getAddress());

                    if (notifiedPCAddresses.size() == 0) {
                        notificationManager.cancel(101);
                    }
                }
            } else {
                if (notifiedPCAddresses.contains(pairedPC.getAddress())) {
                    notificationManager.cancel(pairedPC.getAddress(), 100);
                    notifiedPCAddresses.remove(pairedPC.getAddress());

                    if (notifiedPCAddresses.size() == 0) {
                        notificationManager.cancel(101);
                    }
                }
            }
        }
    }

    private boolean isConnected(String address) {
        Set<BluetoothDevice> pairedDevices = BLUETOOTH_ADAPTER.getBondedDevices();
        for (BluetoothDevice device : pairedDevices) {
            if (device.getAddress().equals(address)) {
                Method method = null;
                try {
                    method = device.getClass().getMethod("isConnected", (Class[]) null);
                } catch (NoSuchMethodException e) {
                    e.printStackTrace();
                }

                boolean connected = false;
                try {
                    assert method != null;
                    Object methodInvocation = method.invoke(device, (Object[]) null);
                    if (methodInvocation != null) {
                        connected = (boolean) methodInvocation;
                    } else {
                        connected = false;
                    }
                } catch (IllegalAccessException | InvocationTargetException e) {
                    e.printStackTrace();
                }

                return connected;
            }
        }
        return false;
    }

    @Override
    public void onStopped() {
        super.onStopped();
    }
}
