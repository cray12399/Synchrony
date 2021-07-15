package com.example.app;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.os.SystemClock;
import android.renderscript.ScriptGroup;
import android.util.Pair;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.apache.commons.io.IOUtils;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
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

    // This thread manages the connected devices and their notifications.
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
                handleNotifications();
            }
        }

        private void handleConnectedDevices() {
            if (BLUETOOTH_ADAPTER.isEnabled()) {
                Set<BluetoothDevice> pairedDevices = BLUETOOTH_ADAPTER.getBondedDevices();
                for (BluetoothDevice device : pairedDevices) {
                    // If PC is connected:
                    if (isConnected(device.getAddress())) {
                        int deviceClass = device.getBluetoothClass().getDeviceClass();

                        // If the PC is a computer:
                        if (deviceClass == BluetoothClass.Device.COMPUTER_LAPTOP |
                                deviceClass == BluetoothClass.Device.COMPUTER_DESKTOP) {
                            // If the PC has not been paired before:
                            if (!utils.inPairedPCS(device.getAddress())) {
                                // Add to PairedPCS list
                                utils.addToPairedPCS(new PairedPC(device.getName(),
                                        device.getAddress(), device, true));
                                utils.savePairedPCSToDevice();

                                // If the PC has been paired before:
                            } else if (utils.getPairedPCByAddress(device.getAddress()) != null) {
                                // Set the PC status to connected.
                                utils.getPairedPCByAddress(device.getAddress())
                                        .setAssociateDevice(device);
                                utils.getPairedPCByAddress(device.getAddress()).setConnected(true);
                            }

                            // Set the PC's UUID
                            utils.getPairedPCByAddress(device.getAddress())
                                    .setUuid(device.getUuids()[0].toString());
                        }
                        // If PC is not connected, but is in paired PCS list:
                    } else if (utils.inPairedPCS(device.getAddress())) {
                        // Set PC status to disconnected.
                        utils.getPairedPCByAddress(device.getAddress()).setConnected(false);
                    }
                }
            } else {
                for (PairedPC pairedPC : utils.getPairedPCS()) {
                    pairedPC.setConnected(false);
                }
            }
        }

        private void handleNotifications() {
            NotificationManagerCompat notificationManager = NotificationManagerCompat
                    .from(CONTEXT);

            final int CONNECTION_NOTIFICATION_ID = 420;
            final String CONNECTED_PC_GROUP = "connectedPCS";
            final String CONNECTED_DEVICES_CHANNEL_ID = "connected_devices";
            final String DISCONNECTED_DEVICES_CHANNEL_ID = "disconnected_devices";

            for (PairedPC pairedPC : utils.getPairedPCS()) {
                // If the device is connected:
                if (pairedPC.isConnected()) {
                    // If the PC is active and has not gotten a notification yet.
                    if (pairedPC.isActive() && !pairedPC.isNotified()) {
                        // Create a notification.
                        NotificationCompat.Builder connectedNotification =
                                new NotificationCompat.Builder(
                                        CONTEXT, CONNECTED_DEVICES_CHANNEL_ID)
                                        .setSmallIcon(R.drawable.ic_pc_connected)
                                        .setContentTitle("Syncing PC")
                                        .setContentText(pairedPC.getName())
                                        .setGroup(CONNECTED_PC_GROUP)
                                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                                        .setOngoing(true);
                        notificationManager.notify(pairedPC.getAddress(),
                                CONNECTION_NOTIFICATION_ID, connectedNotification.build());
                        // Mark PC as notified.
                        pairedPC.setNotified(true);

                        // If the PC connected, but is not active:
                    } else if (!pairedPC.isActive()) {
                        // Cancel the notification and set the PC as non-notified.
                        notificationManager.cancel(pairedPC.getAddress(),
                                CONNECTION_NOTIFICATION_ID);
                        pairedPC.setNotified(false);
                    }

                    // If the PC is not connected, but has been notified:
                } else if (pairedPC.isNotified()) {
                    // Cancel the notification and set the PC as non-notified.
                    notificationManager.cancel(pairedPC.getAddress(), CONNECTION_NOTIFICATION_ID);
                    pairedPC.setNotified(false);

                    // Notify the user that the PC has been disconnected
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

    private static class BluetoothAcceptThread extends Thread {
        private final BluetoothServerSocket BLUETOOTH_SERVER_SOCKET;

        public BluetoothAcceptThread(Context context, java.util.UUID uuid) {

            BluetoothServerSocket tmp = null;
            try {
                BluetoothAdapter BLUETOOTH_ADAPTER = BluetoothAdapter.getDefaultAdapter();
                tmp = BLUETOOTH_ADAPTER.listenUsingRfcommWithServiceRecord(
                        context.getString(R.string.app_name), uuid);
            } catch (IOException e) {
                e.printStackTrace();
            }
            BLUETOOTH_SERVER_SOCKET = tmp;
        }

        @Override
        public void run() {
            while (!isInterrupted()) {
                BluetoothSocket socket = null;
                try {
                    System.out.println("Accepting incoming connections");
                    socket = BLUETOOTH_SERVER_SOCKET.accept();
                    System.out.println("Found connection!");
                } catch (IOException e) {
                    System.out.println(1);
                    e.printStackTrace();
                }

                if (socket != null) {
                    manageConnectedSocket(socket);
                }

                try {
                    assert socket != null;
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        }

        public void cancel() {
            try {
                BLUETOOTH_SERVER_SOCKET.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void manageConnectedSocket(BluetoothSocket socket) {
            try {
                InputStream inputStream = socket.getInputStream();
                while (inputStream.available() == 0) {
                    inputStream = socket.getInputStream();
                }
                int available = inputStream.available();
                byte[] bytesReceive = new byte[available];
                int numBytesRead = inputStream.read(bytesReceive, 0, available);
                String message = new String(bytesReceive);
                System.out.println(message);

                OutputStream outputStream = socket.getOutputStream();
                String receiveMessage = "1234";
                byte[] bytesSend = receiveMessage.getBytes();
                outputStream.write(bytesSend);

                socket.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static class BluetoothCommThread extends Thread {
        private final BluetoothAdapter BLUETOOTH_ADAPTER = BluetoothAdapter.getDefaultAdapter();
        private final Context CONTEXT;
        private final BluetoothDevice DEVICE;
        private final BluetoothSocket BLUETOOTH_SOCKET;
        private java.util.UUID UUID;


        public BluetoothCommThread(Context context, BluetoothDevice device) {
            this.CONTEXT = context;
            this.DEVICE = device;
            this.UUID = java.util.UUID.fromString(device.getUuids()[0].toString());

            BluetoothSocket tmp = null;

            try {
                // Get a BluetoothSocket to connect with the given BluetoothDevice.
                // MY_UUID is the app's UUID string, also used in the server code.
                tmp = DEVICE.createRfcommSocketToServiceRecord(UUID);
            } catch (IOException e) {
                e.printStackTrace();
            }
            BLUETOOTH_SOCKET = tmp;

        }

        @Override
        public void run() {
            BLUETOOTH_ADAPTER.cancelDiscovery();

            try {
                BLUETOOTH_SOCKET.connect();
            } catch (IOException connectException) {
                try {
                    BLUETOOTH_SOCKET.close();
                } catch (IOException closeException) {
                    closeException.printStackTrace();
                }
                return;
            }

            manageConnectedSocket(BLUETOOTH_SOCKET);
        }

        public void manageConnectedSocket(BluetoothSocket socket) {
        }

        public void cancel() {
            try {
                BLUETOOTH_SOCKET.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
