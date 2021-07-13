package com.example.app;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Set;

import static com.example.app.App.CONNECTED_DEVICES_CHANNEL_ID;

public class BluetoothSyncService extends Service {
    private Utils utils;

    private final BluetoothAdapter BLUETOOTH_ADAPTER = BluetoothAdapter.getDefaultAdapter();
    private final String CONNECTED_PC_GROUP = "connectedPCS";
    private final ArrayList<String> NOTIFIED_PC_ADDRESSES = new ArrayList<>();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        utils = Utils.getInstance(this);

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

        Thread serviceThread = new Thread(() -> {
            while (true) {
                handleConnectedDevices();
                handleNotifications();
            }
        });
        serviceThread.start();

        return START_STICKY_COMPATIBILITY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void handleConnectedDevices() {
        Set<BluetoothDevice> pairedDevices = BLUETOOTH_ADAPTER.getBondedDevices();

        // Handle connected PCS
        for (BluetoothDevice device : pairedDevices) {
            if (isConnected(device.getAddress())) {
                int deviceClass = device.getBluetoothClass().getDeviceClass();

                if (deviceClass == BluetoothClass.Device.COMPUTER_LAPTOP |
                        deviceClass == BluetoothClass.Device.COMPUTER_DESKTOP) {
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
                .from(this);

        for (PairedPC pairedPC : utils.getPairedPCS()) {
            int CONNECTION_NOTIFICATION_ID = 420;
            if (pairedPC.isConnected()) {
                if (pairedPC.isActive() && !NOTIFIED_PC_ADDRESSES.contains(pairedPC.getAddress())) {
                    NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(
                            this, CONNECTED_DEVICES_CHANNEL_ID)
                            .setSmallIcon(R.drawable.ic_pc)
                            .setContentTitle("Syncing PC")
                            .setContentText(pairedPC.getName())
                            .setGroup(CONNECTED_PC_GROUP)
                            .setPriority(NotificationCompat.PRIORITY_MIN)
                            .setOngoing(true);

                    notificationManager.notify(pairedPC.getAddress(),
                            CONNECTION_NOTIFICATION_ID, notificationBuilder.build());
                    NOTIFIED_PC_ADDRESSES.add(pairedPC.getAddress());
                } else if (!pairedPC.isActive()) {
                    notificationManager.cancel(pairedPC.getAddress(), CONNECTION_NOTIFICATION_ID);
                    NOTIFIED_PC_ADDRESSES.remove(pairedPC.getAddress());
                }
            } else {
                if (NOTIFIED_PC_ADDRESSES.contains(pairedPC.getAddress())) {
                    notificationManager.cancel(pairedPC.getAddress(), CONNECTION_NOTIFICATION_ID);
                    NOTIFIED_PC_ADDRESSES.remove(pairedPC.getAddress());
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
}
