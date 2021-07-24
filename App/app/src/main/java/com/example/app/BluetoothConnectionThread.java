package com.example.app;

import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static com.example.app.App.CONNECTED_DEVICES_CHANNEL_ID;

class BluetoothConnectionThread extends Thread {
    private static final String TAG = "BluetoothConnectionThread";
    final int NOTIFICATION_ID = PairedPC.NotificationID.getID();
    private final Context CONTEXT;
    private final String DEVICE_TAG;
    private final BluetoothDevice DEVICE;
    private BluetoothServerSocket bluetoothServerSocket;
    private BluetoothSocket bluetoothSocket;
    private boolean socketConnected = false;
    private long lastHeartbeat = -1;

    public BluetoothConnectionThread(Context context, BluetoothDevice device) {
        this.CONTEXT = context;
        this.DEVICE = device;
        this.DEVICE_TAG = String.format("%s (%s)", DEVICE.getName(), DEVICE.getAddress());

    }

    @Override
    public void run() {
        Utils utils = Utils.getInstance(CONTEXT);
        createNotification();
        while (!interrupted()) {
            bluetoothServerSocket = null;
            while(bluetoothServerSocket == null && !interrupted()) {
                BluetoothServerSocket tmp = null;
                try {
                    BluetoothAdapter BLUETOOTH_ADAPTER = BluetoothAdapter.getDefaultAdapter();

                    Log.d(TAG, String.format("run: " +
                            "Creating bluetooth server socket for device: %s...", DEVICE_TAG));
                    tmp = BLUETOOTH_ADAPTER.listenUsingRfcommWithServiceRecord(
                            CONTEXT.getString(R.string.app_name), utils.getUuid());
                    Log.d(TAG, String.format("run: bluetooth server socket" +
                            " created for device: %s...", DEVICE_TAG));
                } catch (IOException e) {
                    Log.e(TAG, String.format("run: " +
                            "Creation of bluetooth server socket for device: %s FAILED! ",
                            DEVICE_TAG), e);
                    SystemClock.sleep(5000);
                }
                bluetoothServerSocket = tmp;
            }

            while (!socketConnected && !interrupted()) {
                Log.d(TAG, String.format("run: Creating socket for device: %s...", DEVICE_TAG));
                bluetoothSocket = getConnectedSocket();

                if (bluetoothSocket != null) {
                    Log.d(TAG, String.format("run: Socket created for device: %s!",
                            DEVICE_TAG));
                    socketConnected = true;
                }
            }

            while (socketConnected && !interrupted()) {
                String clientCommand = receiveCommand();
                if (clientCommand != null) {
                    handleCommand(clientCommand);
                }

                sendHeartbeat();
            }
            closeSockets();
        }
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(CONTEXT);
        notificationManager.cancel(DEVICE.getAddress(), NOTIFICATION_ID);
        Log.d(TAG, String.format("run: Stopped connection thread for device %s!", DEVICE_TAG));
    }

    private void closeSockets() {
        if (bluetoothSocket != null) {
            try {
                Log.d(TAG, String.format("stopThread: " +
                        "Closing socket for device: %s...", DEVICE_TAG));
                bluetoothSocket.close();
                Log.d(TAG, String.format("stopThread: " +
                        "Socket closed for device: %s!", DEVICE_TAG));
            } catch (IOException e) {
                Log.e(TAG, String.format("stopThread: " +
                        "Error closing socket thread for device: %s!", DEVICE_TAG), e);
            }
        }

        if (bluetoothServerSocket != null) {
            try {
                Log.d(TAG, String.format("stopThread: " +
                        "Closing bluetooth server socket for device %s!", DEVICE_TAG));
                bluetoothServerSocket.close();
                Log.d(TAG, String.format("stopThread: " +
                        "Bluetooth server socket closed for device %s!", DEVICE_TAG));
            } catch (IOException e) {
                Log.e(TAG, String.format("stopThread: " +
                        "Error closing bluetooth server socket for device: %s!", DEVICE_TAG), e);
            }
        }
    }

    private void createNotification() {
        Intent stopSyncIntent = new Intent(CONTEXT, NotificationIntentReceiver.class);
        stopSyncIntent.setAction(NotificationIntentReceiver.STOP_SYNC_ACTION);
        stopSyncIntent.putExtra(NotificationIntentReceiver.PC_ADDRESS_KEY, DEVICE.getAddress());
        PendingIntent stopSyncPendingIntent = PendingIntent.getBroadcast(CONTEXT, NOTIFICATION_ID,
                stopSyncIntent, 0);


        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(CONTEXT);
        final String CONNECTED_PC_GROUP = "connectedPCS";
        NotificationCompat.Builder connectedNotification =
                new NotificationCompat.Builder(
                        CONTEXT, CONNECTED_DEVICES_CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_placeholder_logo)
                        .setContentTitle("Syncing PC")
                        .setContentText(DEVICE.getName())
                        .setGroup(CONNECTED_PC_GROUP)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setOngoing(true)
                        .setContentIntent(stopSyncPendingIntent)
                .addAction(R.drawable.ic_pc_disconnected, "Stop Sync", stopSyncPendingIntent);
        notificationManager.notify(DEVICE.getAddress(),
                NOTIFICATION_ID, connectedNotification.build());
    }

    public BluetoothSocket getConnectedSocket() {
        BluetoothSocket socket = null;
        try {
            if (!interrupted()) {
                Log.d(TAG, String.format("getConnectedSocket: " +
                        "Listening for client from device: %s...", DEVICE_TAG));
                socket = bluetoothServerSocket.accept(5000);
                System.out.println(socket.isConnected());
            }
        } catch (IOException e) {
            if (!e.toString().contains("Try again")) {
                Log.e(TAG, String.format("getConnectedSocket: " +
                        "Error connecting to client for device: %s! ", DEVICE_TAG), e);
            }
        }

        if (socket != null) {
            if (socket.getRemoteDevice().getAddress().equals(DEVICE.getAddress())) {
                Log.d(TAG, String.format("getConnectedSocket: " +
                        "Socket created for device: %s!", DEVICE_TAG));
                return socket;
            } else {
                try {
                    socket.close();
                } catch (IOException e) {
                    Log.e(TAG, String.format("getConnectedSocket: " +
                            "Error closing socket for device: %s!", DEVICE_TAG), e);
                }
            }
        }

        return null;
    }

    public String receiveCommand() {
        try {
            InputStream inputStream = bluetoothSocket.getInputStream();
            if (inputStream.available() > 0) {
                Log.d(TAG, String.format("receiveCommand: " +
                        "Incoming bytes from client for device: %s...", DEVICE_TAG));
                int available = inputStream.available();
                byte[] bytes = new byte[available];
                inputStream.read(bytes);
                Log.d(TAG, String.format("receiveCommand: " +
                                "Incoming bytes successfully received from client for device: %s!",
                        DEVICE_TAG));
                return new String(bytes);
            }
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, String.format("receiveCommand: " +
                            "Error receiving incoming bytes from client for device: %s!",
                    DEVICE_TAG), e);
        }

        return null;
    }

    public void sendCommand(byte[] command) {
        try {
            Log.d(TAG, String.format("sendCommand: " +
                            "Sending command to client for device: %s: %s...", DEVICE_TAG,
                    new String(command)));
            OutputStream outputStream = bluetoothSocket.getOutputStream();
            outputStream.write(command);
            Log.d(TAG, String.format("sendCommand: " +
                            "Successfully sent command to client for device: %s: %s!",
                    DEVICE_TAG, new String(command)));
        } catch (IOException e) {
            Log.e(TAG, String.format("sendCommand: " +
                    "Couldn't send command to client for device: %s!", DEVICE_TAG), e);
            try {
                bluetoothSocket.close();
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
            socketConnected = false;
        }
    }

    public void handleCommand(String clientCommand) {
        Log.d(TAG, String.format("handleCommand: " +
                "Command obtained from client for device: %s: %s!", DEVICE_TAG, clientCommand));
        switch (clientCommand) {
            default:
                break;
        }
    }

    public void sendHeartbeat() {
        final int HEARTBEAT_TIMING = 5000;
        if (lastHeartbeat == -1 || System.currentTimeMillis() - lastHeartbeat >
                HEARTBEAT_TIMING) {
            try {
                OutputStream outputStream = bluetoothSocket.getOutputStream();
                outputStream.write("heartbeat".getBytes());
            } catch (IOException e) {
                Log.e(TAG, String.format("sendHeartbeat: " +
                        "Couldn't send heartbeat to client for device: %s!", DEVICE_TAG), e);
                socketConnected = false;
            }
            lastHeartbeat = System.currentTimeMillis();
        }
    }
}
